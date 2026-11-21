package app.stateside.env.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.Executors

enum class AdbMdnsType(val serviceType: String) {
    TLS_PAIRING("_adb-tls-pairing._tcp"),
    TLS_CONNECT("_adb-tls-connect._tcp")
}

object AdbMdnsDiscovery {

    private val ipv4Loopback: InetAddress by lazy { InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) }
    private val ipv6Loopback: InetAddress by lazy {
        InetAddress.getByAddress(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )
    }

    fun observeLocalPort(context: Context, type: AdbMdnsType): Flow<Int> = callbackFlow {
        val appCtx = context.applicationContext
        val nsdManager = appCtx.getSystemService(NsdManager::class.java)
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val wifiManager = appCtx.getSystemService(WifiManager::class.java)
        val multicastLock = wifiManager?.createMulticastLock("stateside_mdns_${type.name}")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val executor = Executors.newSingleThreadExecutor()
        val activeCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val infoCallback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                    override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                        val port = updatedInfo.port
                        if (port in 1..65535 && isOwnDeviceService(appCtx, updatedInfo, port)) {
                            trySend(port)
                        }
                    }
                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                synchronized(activeCallbacks) {
                    activeCallbacks.add(infoCallback)
                }
                runCatching {
                    nsdManager.registerServiceInfoCallback(serviceInfo, executor, infoCallback)
                }
            }
        }

        runCatching {
            nsdManager.discoverServices(type.serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }

        awaitClose {
            synchronized(activeCallbacks) {
                activeCallbacks.forEach { cb ->
                    runCatching { nsdManager.unregisterServiceInfoCallback(cb) }
                }
                activeCallbacks.clear()
            }
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            runCatching {
                if (multicastLock?.isHeld == true) multicastLock.release()
            }
            executor.shutdownNow()
        }
    }

    private fun isOwnDeviceService(
        context: Context,
        info: NsdServiceInfo,
        port: Int
    ): Boolean {
        if (isLocalLoopbackPortListening(port)) {
            return true
        }
        val hostAddresses = info.hostAddresses
        if (hostAddresses.isEmpty()) return false

        val localRawAddresses = getLocalInetRawAddresses(context)
        if (localRawAddresses.isEmpty()) return false

        return hostAddresses.any { candidate ->
            candidate.isLoopbackAddress ||
                localRawAddresses.any { raw -> raw.contentEquals(candidate.address) }
        }
    }

    fun isLocalLoopbackPortListening(port: Int): Boolean {
        if (port !in 1..65535) return false
        if (isAddressPortBound(ipv4Loopback, port)) return true
        return isAddressPortBound(ipv6Loopback, port)
    }

    /**
     * 零权限内核端口占用扫描雷达（专治 Android 17 ACCESS_LOCAL_NETWORK 未授权、VPN tun 拦截组播、校园网 AP 隔离）：
     * 优先排列同时占用 127.0.0.1 与 ::1 的双栈通配端口（*:port / [::]:port，即系统 adbd 与配对弹窗标准监听方式），
     * 其次包含仅占用 0.0.0.0 / Wi-Fi 局域网 IP 的监听端口，全量扫描仅需约 80~150ms。
     */
    suspend fun scanLocalDualStackCandidatePorts(
        context: Context? = null,
        startPort: Int = 30000,
        endPort: Int = 65535,
        parallelism: Int = 8
    ): Set<Int> = withContext(Dispatchers.IO) {
        val wifiInet4 = context?.let { getActiveWifiIpv4(it) }
        coroutineScope {
            val total = endPort - startPort + 1
            val chunkSize = (total + parallelism - 1) / parallelism
            val chunks = (0 until parallelism).map { workerIdx ->
                val chunkStart = startPort + workerIdx * chunkSize
                val chunkEnd = (chunkStart + chunkSize - 1).coerceAtMost(endPort)
                async {
                    val dualStack = ArrayList<Int>(4)
                    val singleStack = ArrayList<Int>(4)
                    if (chunkStart <= chunkEnd) {
                        for (port in chunkStart..chunkEnd) {
                            val v4Bound = isAddressPortBound(ipv4Loopback, port)
                            if (v4Bound) {
                                if (isAddressPortBound(ipv6Loopback, port)) {
                                    dualStack.add(port)
                                } else {
                                    singleStack.add(port)
                                }
                            } else if (wifiInet4 != null && isAddressPortBound(wifiInet4, port)) {
                                singleStack.add(port)
                            }
                        }
                    }
                    dualStack to singleStack
                }
            }.awaitAll()

            LinkedHashSet<Int>().apply {
                chunks.forEach { addAll(it.first) }
                chunks.forEach { addAll(it.second) }
            }
        }
    }

    fun getActiveWifiIpv4(context: Context): Inet4Address? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        cm.getLinkProperties(net)?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
    }.getOrNull()

    private fun isAddressPortBound(address: InetAddress, port: Int): Boolean {
        return try {
            ServerSocket().use { ss ->
                ss.reuseAddress = false
                ss.bind(InetSocketAddress(address, port), 1)
            }
            false
        } catch (_: IOException) {
            true
        }
    }

    private fun getLocalInetRawAddresses(context: Context): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val activeNet = cm?.activeNetwork
            if (activeNet != null) {
                cm.getLinkProperties(activeNet)?.linkAddresses?.forEach { linkAddr ->
                    val addr: InetAddress = linkAddr.address
                    result.add(addr.address)
                }
            }
        }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    result.add(addrs.nextElement().address)
                }
            }
        }
        return result
    }
}
