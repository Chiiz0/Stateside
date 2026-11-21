package app.stateside.env.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
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

    /**
     * 双重真机本机判定：
     * 1. 首选回环端口占用探测（ServerSocket bind 127.0.0.1:port 触发 EADDRINUSE）：
     *    不受 Android 11~17 targetSdk>=30 对 NetworkInterface(NETLINK_ROUTE) 的 SELinux 封锁影响，
     *    也不受 VPN tun0 或 IPv6 %wlan0 作用域后缀差异影响，且不会向 adbd 发起客户端握手干扰配对状态机；
     * 2. 辅以 ConnectivityManager LinkProperties 原始字节比对兜底。
     */
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
        val ipv4InUse = try {
            ServerSocket().use { ss ->
                ss.reuseAddress = false
                ss.bind(InetSocketAddress("127.0.0.1", port), 1)
            }
            false
        } catch (_: IOException) {
            true
        }
        if (ipv4InUse) return true

        return try {
            ServerSocket().use { ss ->
                ss.reuseAddress = false
                ss.bind(InetSocketAddress("::1", port), 1)
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
