package app.stateside.env.adb

import android.content.Context
import android.os.Build
import app.stateside.env.StatesideApp
import app.stateside.env.boot.BootRestoreJobService
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.StatesideDefaults
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.privileged.EnvExecutionReport
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.privileged.PrivilegedResultBus
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * 自包含本机 127.0.0.1 无线 ADB 引擎（同时兼容已 Root 设备 su 免配对直通与 TCP 5555 模式）。
 * 负责首次配对、端口自动追踪重连、失效连接自愈、以及触发 StatesideEnvInstrumentation 特权批处理。
 */
class LocalAdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val appContext = context.applicationContext
    private val keyStore = AdbKeyStore(appContext)
    private val execMutex = Mutex()

    @Volatile
    private var rootChecked = false

    @Volatile
    private var hasRootCached = false

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress(LOOPBACK_HOST)
        setTimeout(6, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = keyStore.getOrCreateIdentity().first

    override fun getCertificate(): Certificate = keyStore.getOrCreateIdentity().second

    override fun getDeviceName(): String = "Stateside"

    fun cachedHasRootAccess(): Boolean = hasRootCached

    suspend fun detectRootAccess(forceRefresh: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (rootChecked && !forceRefresh) return@withContext hasRootCached
            val rooted = tryHasRootAccess()
            hasRootCached = rooted
            rootChecked = true
            rooted
        }

    suspend fun pairWithCode(port: Int, sixDigitCode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanCode = sixDigitCode.filter { it.isDigit() }
                require(cleanCode.length == 6) { "请输入完整的 6 位数字配对码" }
                require(port in 1..65535) { "未检测到有效的无线调试配对端口，请确认已点开「使用配对码配对设备」弹窗（校园网/VPN 环境可同时输入「端口 配对码」）" }

                runCatching { disconnect() }
                val pairedLoopback = runCatching {
                    pair(LOOPBACK_HOST, port, cleanCode)
                }.getOrDefault(false)

                val paired = if (pairedLoopback) {
                    true
                } else {
                    val wifiHost = AdbMdnsDiscovery.getActiveWifiIpv4(appContext)?.hostAddress
                    if (!wifiHost.isNullOrBlank()) {
                        runCatching { pair(wifiHost, port, cleanCode) }.getOrDefault(false)
                    } else {
                        false
                    }
                }

                if (!paired) {
                    error("配对未通过，请确认 6 位配对码未过期并保持配对弹窗开启")
                }
                val repo = UserPrefsRepository(appContext)
                repo.markAdbPaired(true)
                syncGlobalPrefs()
            }
        }

    suspend fun ensureConnected(timeoutMs: Long = 7_500L): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isConnected) return@runCatching

                val prefs = UserPrefsRepository(appContext)
                val settings = prefs.settings.value
                var sawAuthRejected = false

                suspend fun tryConnectLoopbackPort(port: Int, retries: Int = 1): Boolean {
                    if (port !in 1..65535) return false
                    repeat(retries) { attempt ->
                        try {
                            if (connect(LOOPBACK_HOST, port) && isConnected) {
                                prefs.markAdbPaired(true, port)
                                syncGlobalPrefs()
                                return true
                            }
                        } catch (_: AdbPairingRequiredException) {
                            sawAuthRejected = true
                            return false
                        } catch (_: AdbAuthenticationFailedException) {
                            sawAuthRejected = true
                            return false
                        } catch (_: Throwable) {
                            runCatching { disconnect() }
                        }
                        if (attempt + 1 < retries) {
                            delay(400L)
                        }
                    }
                    return false
                }

                // 1. 系统属性快车道（部分 ROM 直接暴露当前无线调试/TCP 端口，0ms 直连）
                for (propName in listOf("service.adb.tls.port", "service.adb.tcp.port")) {
                    val propPort = readSystemPropertyPort(propName)
                    if (propPort in 1024..65535 && tryConnectLoopbackPort(propPort, retries = 2)) {
                        return@runCatching
                    }
                }

                // 2. 用户手动指定的连接端口（校园网/VPN 严格拦截 mDNS 时使用）
                if (settings.manualConnectPort in 1024..65535) {
                    if (tryConnectLoopbackPort(settings.manualConnectPort, retries = 2)) {
                        return@runCatching
                    }
                }

                // 3. 上次成功连接的缓存端口快速探活
                if (settings.lastConnectPort in 1024..65535 &&
                    AdbMdnsDiscovery.isLocalLoopbackPortListening(settings.lastConnectPort)
                ) {
                    if (tryConnectLoopbackPort(settings.lastConnectPort, retries = 2)) {
                        return@runCatching
                    }
                }

                // 4. 本机 mDNS 自动发现 _adb-tls-connect._tcp 端口（设定 2.5s 快速窗口，若因 Android 17 本地网络未授权或 VPN 拦截未返回则立即切入内核双栈扫描）
                val discoveredPort = withTimeoutOrNull(timeoutMs.coerceAtMost(2_500L)) {
                    AdbMdnsDiscovery.observeLocalPort(appContext, AdbMdnsType.TLS_CONNECT).first()
                }
                if (discoveredPort != null && discoveredPort in 1..65535) {
                    if (tryConnectLoopbackPort(discoveredPort, retries = 3)) {
                        return@runCatching
                    }
                }

                // 5. 零权限内核回环双栈端口扫描兜底（无需 mDNS、不受 Android 17 ACCESS_LOCAL_NETWORK 或 VPN 限制，毫秒级定位 *:port 无线调试端口）
                val dualStackCandidates = AdbMdnsDiscovery.scanLocalDualStackCandidatePorts(appContext)
                setTimeout(2, TimeUnit.SECONDS)
                try {
                    for (candidatePort in dualStackCandidates) {
                        if (candidatePort == discoveredPort) continue
                        if (tryConnectLoopbackPort(candidatePort, retries = 1)) {
                            return@runCatching
                        }
                    }
                } finally {
                    setTimeout(6, TimeUnit.SECONDS)
                }

                // 6. 经典 adb tcpip 5555 兜底（无需 Wi-Fi、蜂窝流量下亦可用）
                if (AdbMdnsDiscovery.isLocalLoopbackPortListening(DEFAULT_TCP_ADB_PORT)) {
                    if (tryConnectLoopbackPort(DEFAULT_TCP_ADB_PORT, retries = 1)) {
                        return@runCatching
                    }
                }

                if (sawAuthRejected) {
                    prefs.markAdbPaired(false)
                    syncGlobalPrefs()
                    error("检测到无线调试已开启，但本机配对证书已被撤销或失效。请点击右上角「激活向导」重新完成一次 6 位配对码绑定。")
                }

                error("未检测到开启中的「无线调试」。请确认手机已连接 Wi-Fi，且「开发者选项 → 无线调试」处于开启状态（若处于校园网/VPN 拦截环境，可在激活向导中填入连接端口）。")
            }
        }

    /**
     * 一键执行「美境开启（双卡美国运营商 + 核心应用组及扩展应用设为 en-US）」或「两步恢复国内默认状态」。
     */
    suspend fun runEnvironmentTask(
        action: PrivilegedAction,
        carrier: UsCarrierPreset? = null,
        targetPackagesOverride: List<String>? = null
    ): Result<EnvExecutionReport> = execMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = UserPrefsRepository(appContext)
                val currentSettings = repo.settings.value
                val effectiveCarrier = carrier ?: currentSettings.selectedCarrier

                val packagesToProcess = when {
                    targetPackagesOverride != null -> targetPackagesOverride
                    action == PrivilegedAction.RESTORE_CN_DEFAULT -> {
                        // 恢复默认时将内置四大应用及用户曾勾选的全部扩展应用一并还原为跟随系统中文
                        (CoreSuiteApp.ALL_BUILTIN_PACKAGES +
                            currentSettings.extraEnglishPackages +
                            currentSettings.confirmedEnglishPackages).toList()
                    }
                    else -> currentSettings.effectiveEnglishPackages()
                }
                    .map { it.trim() }
                    .filter { StatesideDefaults.isValidPackageName(it) }
                    .distinct()
                    .joinToString(",")

                val cmd = buildString {
                    append("am instrument -w --no-restart ")
                    append("-e action ").append(action.name).append(' ')
                    append("-e carrier_id ").append(effectiveCarrier.id).append(' ')
                    append("-e target_pkgs '").append(packagesToProcess).append("' ")
                    append("${appContext.packageName}/.privileged.StatesideEnvInstrumentation")
                }

                val deferred = PrivilegedResultBus.prepareNewRun(appContext)

                val useRoot = detectRootAccess()
                val runShell: suspend (String) -> String = if (useRoot) {
                    { c -> execViaRoot(c) }
                } else {
                    ensureConnected().getOrThrow()
                    val adbRunner: suspend (String) -> String = { c -> execViaAdbStreamWithRetry(c) }
                    adbRunner
                }

                var shellOutput = runShell(cmd)
                if (
                    cmd.contains("--no-restart ") &&
                    (shellOutput.contains("Unknown option") ||
                        shellOutput.contains("instrumentWithoutRestart") ||
                        shellOutput.contains("NullPointerException"))
                ) {
                    shellOutput = runShell(cmd.replace("--no-restart ", ""))
                }

                val report = withTimeoutOrNull(12_000L) {
                    deferred.await()
                } ?: PrivilegedResultBus.readDiskFallback(appContext)
                    ?: error("特权组件未返回结果，系统输出：${shellOutput.trim().take(280).ifEmpty { "无输出（请检查开发者选项安全开关）" }}")

                if (!report.overallSuccess) {
                    error(report.errorMessage.ifBlank { "执行未完全成功，请检查系统「USB 调试（安全设置）」或「禁止权限监控」开关" })
                }

                when (action) {
                    PrivilegedAction.APPLY_US_ENV -> {
                        repo.recordAppliedSuccess(report.updatedEnglishPackages)
                    }
                    PrivilegedAction.RESTORE_CN_DEFAULT -> {
                        repo.recordRestoredDefault()
                        BootRestoreJobService.cancelPending(appContext)
                    }
                }
                syncGlobalPrefs()
                report
            }
        }
    }

    private suspend fun execViaAdbStreamWithRetry(command: String): String {
        return try {
            execViaAdbStreamOnce(command)
        } catch (_: Throwable) {
            // 锁屏休眠或切换 Wi-Fi 后旧 Socket 半开失效时，自动断开重连并透明重试一次
            runCatching { disconnect() }
            ensureConnected(5_500L).getOrThrow()
            execViaAdbStreamOnce(command)
        }
    }

    private fun execViaAdbStreamOnce(command: String): String {
        openStream("shell:$command").use { stream ->
            val reader = BufferedReader(
                InputStreamReader(stream.openInputStream(), StandardCharsets.UTF_8)
            )
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            return sb.toString()
        }
    }

    private fun tryHasRootAccess(): Boolean = runCatching {
        val proc = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val finished = proc.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@runCatching false
        }
        val out = proc.inputStream.bufferedReader().readText()
        proc.exitValue() == 0 && out.contains("uid=0")
    }.getOrDefault(false)

    private fun execViaRoot(command: String): String {
        val proc = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = proc.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            error("Root 命令执行超时，请检查 Magisk / KernelSU / APatch 授权状态")
        }
        return proc.inputStream.bufferedReader().readText()
    }

    private fun readSystemPropertyPort(key: String): Int = runCatching {
        val spClass = Class.forName("android.os.SystemProperties")
        val raw = spClass.getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String
        raw?.trim()?.toIntOrNull() ?: -1
    }.getOrDefault(-1)

    private fun syncGlobalPrefs() {
        runCatching { StatesideApp.instance.prefs.reload() }
    }

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val DEFAULT_TCP_ADB_PORT = 5555

        @Volatile
        private var instance: LocalAdbManager? = null

        fun getInstance(context: Context): LocalAdbManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAdbManager(context).also { instance = it }
            }
        }
    }
}
