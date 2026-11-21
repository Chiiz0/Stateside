package app.stateside.env.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
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

data class AdbEndpoint(
    val host: String,
    val port: Int
)

object AdbMdnsDiscovery {

    // 使用进程级共享守护单线程 Executor（切勿在 awaitClose 中调用 shutdownNow！
    // Android 14~17 的 NsdManager.unregisterServiceInfoCallback 是异步 IPC，注销后仍会在 ConnectivityThread
    // 回调 onServiceInfoCallbackUnregistered()，若提前 shutdown Executor 会抛出 RejectedExecutionException 崩溃！）
    private val nsdCallbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "stateside-nsd-cb").apply { isDaemon = true }
    }
    private val safeExecutor = java.util.concurrent.Executor { command ->
        runCatching { nsdCallbackExecutor.execute(command) }
    }

    private val ipv4Loopback: InetAddress by lazy {
        InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    }
    private val ipv6Loopback: InetAddress by lazy {
        InetAddress.getByAddress(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )
    }

    fun observeLocalPort(context: Context, type: AdbMdnsType): Flow<Int> =
        observeLocalEndpoint(context, type).map { it.port }

    fun observeLocalEndpoint(context: Context, type: AdbMdnsType): Flow<AdbEndpoint> = callbackFlow {
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
                            val resolvedHost = updatedInfo.hostAddresses
                                .filterIsInstance<Inet4Address>()
                                .firstOrNull { !it.isLoopbackAddress }
                                ?.hostAddress
                                ?: getActiveWifiIpv4(appCtx)?.hostAddress
                                ?: "127.0.0.1"
                            trySend(AdbEndpoint(host = resolvedHost, port = port))
                        }
                    }
                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                synchronized(activeCallbacks) {
                    activeCallbacks.add(infoCallback)
                }
                runCatching {
                    nsdManager.registerServiceInfoCallback(serviceInfo, safeExecutor, infoCallback)
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
