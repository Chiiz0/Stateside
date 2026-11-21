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
import io.github.muntashirakon.adb.PairingConnectionCtx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * 自包含本机 127.0.0.1 无线 ADB 引擎（同时兼容已 Root 设备 su 免配对直通与 TCP 5555 模式）。
 * 负责首次配对、mDNS 端口实时追踪重连、失效连接自愈、以及触发 StatesideEnvInstrumentation 特权批处理。
 */
class LocalAdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val appContext = context.applicationContext
    private val keyStore = AdbKeyStore(appContext)
    private val execMutex = Mutex()
    private val ioWorkerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var rootChecked = false

    @Volatile
    private var hasRootCached = false

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress(LOOPBACK_HOST)
        setTimeout(3, TimeUnit.SECONDS)
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

    /**
     * 执行无线调试 SPAKE2 + TLS 1.3 配对：
     * 直接创建独立的 PairingConnectionCtx 并施加 5.5s 硬超时，绝不持有 AbsAdbConnectionManager.mLock，
     * 彻底杜绝非 ADB 端口或半开连接把整个 ADB 管理器锁死。
     */
    suspend fun pairWithCode(
        port: Int,
        sixDigitCode: String,
        preferredHost: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCode = sixDigitCode.filter { it.isDigit() }
            require(cleanCode.length == 6) { "请输入完整的 6 位数字配对码" }
            require(port in 1..65535) {
                "未检测到有效的无线调试配对端口，请确认已点开「使用配对码配对设备」弹窗（校园网/VPN 环境可输入「端口 配对码」）"
            }

            runCatching { disconnect() }

            val candidateHosts = LinkedHashSet<String>().apply {
                add(LOOPBACK_HOST)
                preferredHost?.trim()?.takeIf { it.isNotEmpty() && it != LOOPBACK_HOST }?.let(::add)
                AdbMdnsDiscovery.getActiveWifiIpv4(appContext)?.hostAddress
                    ?.takeIf { it.isNotEmpty() && it != LOOPBACK_HOST }
                    ?.let(::add)
            }

            var paired = false
            var lastFailure: Throwable? = null
            var codeRejectedByAdbDaemon = false

            for (host in candidateHosts) {
                val attempt = tryPairEndpointWithTimeout(host, port, cleanCode, timeoutMs = 5_500L)
                if (attempt.isSuccess) {
                    paired = true
                    break
                } else {
                    val err = attempt.exceptionOrNull()
                    lastFailure = err
                    val msg = err?.message.orEmpty()
                    // 若已连上 adbd 但 SPAKE2 校验未过（配对码填错或弹窗已刷新），无需再用错误配对码重试其他网卡地址
                    if (msg.contains("Exchanging message", ignoreCase = true) ||
                        msg.contains("peer info", ignoreCase = true) ||
                        msg.contains("decrypt", ignoreCase = true)
                    ) {
                        codeRejectedByAdbDaemon = true
                        break
                    }
                }
            }

            if (!paired) {
                val friendlyReason = when {
                    codeRejectedByAdbDaemon -> {
                        "6 位配对码不正确或已过期，请核对当前配对弹窗上的最新 6 位数字重新提交"
                    }
                    lastFailure is ConnectException -> {
                        "无法连接配对端口 $port（连接被拒绝），请确认「使用配对码配对设备」弹窗仍处于打开状态"
                    }
                    lastFailure?.message?.contains("timeout", ignoreCase = true) == true -> {
                        "连接配对端口 $port 握手超时，请核对无线调试配对弹窗上的端口号是否正确"
                    }
                    else -> {
                        "配对未通过（${lastFailure?.message ?: "未知原因"}），请确认配对弹窗未关闭并重新输入 6 位数字"
                    }
                }
                error(friendlyReason)
            }

            val repo = UserPrefsRepository(appContext)
            repo.markAdbPaired(true)
            syncGlobalPrefs()
        }
    }

    private suspend fun tryPairEndpointWithTimeout(
        host: String,
        port: Int,
        cleanCode: String,
        timeoutMs: Long
    ): Result<Unit> {
        val (privKey, cert) = keyStore.getOrCreateIdentity()
        val pswdBytes = cleanCode.toByteArray(StandardCharsets.UTF_8)
        var ctxRef: PairingConnectionCtx? = null

        val job = ioWorkerScope.async {
            runCatching {
                val ctx = PairingConnectionCtx(
                    host,
                    port,
                    pswdBytes,
                    privKey,
                    cert,
                    getDeviceName()
                )
                ctxRef = ctx
                ctx.use { it.start() }
            }
        }

        val completed = withTimeoutOrNull(timeoutMs) {
            job.await()
        }
        if (completed == null) {
            job.cancel()
            runCatching { ctxRef?.close() }
            return Result.failure(IllegalStateException("TLS pairing timeout on $host:$port"))
        }
        return completed
    }

    suspend fun ensureConnected(timeoutMs: Long = 7_500L): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (isConnected) return@runCatching

                val prefs = UserPrefsRepository(appContext)
                val settings = prefs.settings.value
                var sawAuthRejected = false

                suspend fun tryConnectEndpoint(
                    port: Int,
                    preferredHost: String? = null,
                    retries: Int = 1
                ): Boolean {
                    if (port !in 1..65535) return false
                    val hosts = LinkedHashSet<String>().apply {
                        add(LOOPBACK_HOST)
                        preferredHost?.takeIf { it.isNotBlank() && it != LOOPBACK_HOST }?.let(::add)
                    }

                    repeat(retries) { attempt ->
                        for (host in hosts) {
                            val attemptRes = withTimeoutOrNull(3_800L) {
                                ioWorkerScope.async {
                                    runCatching { connect(host, port) && isConnected }
                                }.await()
                            }
                            if (attemptRes == null) {
                                runCatching { disconnect() }
                                continue
                            }
                            if (attemptRes.isSuccess && attemptRes.getOrDefault(false)) {
                                prefs.markAdbPaired(true, port)
                                syncGlobalPrefs()
                                return true
                            }
                            val ex = attemptRes.exceptionOrNull()
                            if (ex is AdbPairingRequiredException || ex is AdbAuthenticationFailedException) {
                                sawAuthRejected = true
                                runCatching { disconnect() }
                                return false
                            }
                            runCatching { disconnect() }
                            if (ex !is ConnectException) {
                                break
                            }
                        }
                        if (attempt + 1 < retries) {
                            delay(350L)
                        }
                    }
                    return false
                }

                // 1. 系统属性快车道（部分 ROM 直接暴露当前无线调试/TCP 端口，0ms 直连）
                for (propName in listOf("service.adb.tls.port", "service.adb.tcp.port")) {
                    val propPort = readSystemPropertyPort(propName)
                    if (propPort in 1024..65535 && tryConnectEndpoint(propPort, retries = 2)) {
                        return@runCatching
                    }
                }

                // 2. 用户手动指定的无线调试连接端口（校园网/VPN 严格拦截组播时使用）
                if (settings.manualConnectPort in 1024..65535) {
                    val wifiIp = AdbMdnsDiscovery.getActiveWifiIpv4(appContext)?.hostAddress
                    if (tryConnectEndpoint(settings.manualConnectPort, preferredHost = wifiIp, retries = 2)) {
                        return@runCatching
                    }
                }

                // 3. 优先通过 mDNS 实时发现当前真实的 _adb-tls-connect._tcp 端口（避免误连其他系统服务端口）
                val discovered = withTimeoutOrNull(timeoutMs.coerceAtMost(2_800L)) {
                    AdbMdnsDiscovery.observeLocalEndpoint(appContext, AdbMdnsType.TLS_CONNECT).first()
                }
                if (discovered != null && discovered.port in 1..65535) {
                    if (tryConnectEndpoint(discovered.port, preferredHost = discovered.host, retries = 2)) {
                        return@runCatching
                    }
                }

                // 4. 上次成功连接的缓存端口快速探活（应对开启 VPN 后 mDNS 未返回但端口未变的场景）
                if (!sawAuthRejected &&
                    settings.lastConnectPort in 1024..65535 &&
                    settings.lastConnectPort != discovered?.port &&
                    AdbMdnsDiscovery.isLocalLoopbackPortListening(settings.lastConnectPort)
                ) {
                    if (tryConnectEndpoint(settings.lastConnectPort, retries = 1)) {
                        return@runCatching
                    }
                }

                // 5. 经典 adb tcpip 5555 兜底（无需 Wi-Fi、蜂窝流量下亦可用）
                if (!sawAuthRejected &&
                    AdbMdnsDiscovery.isLocalLoopbackPortListening(DEFAULT_TCP_ADB_PORT)
                ) {
                    if (tryConnectEndpoint(DEFAULT_TCP_ADB_PORT, retries = 1)) {
                        return@runCatching
                    }
                }

                if (sawAuthRejected) {
                    prefs.markAdbPaired(false)
                    syncGlobalPrefs()
                    error("检测到无线调试已开启，但本机配对授权尚未建立或已失效。请点击右上角齿轮（或主按钮）使用 6 位配对码完成一次绑定。")
                }

                error("未检测到开启中的「无线调试」连接端口。请确认手机已连接 Wi-Fi、已允许本地网络权限，且「开发者选项 → 无线调试」处于开启状态（校园网/VPN 环境可在激活向导中填入无线调试页面显示的端口）。")
            }
        }

    /**
     * 一键执行「美境开启（双卡美国区域 + 核心应用组及扩展应用设为 en-US）」或「恢复国内默认状态」。
     */
    suspend fun runEnvironmentTask(
        action: PrivilegedAction,
        carrier: UsCarrierPreset? = null,
        targetPackagesOverride: List<String>? = null,
        explicitTimezoneId: String? = null
    ): Result<EnvExecutionReport> = execMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = UserPrefsRepository(appContext)
                val currentSettings = repo.settings.value
                val effectiveCarrier = carrier ?: currentSettings.selectedCarrier

                val packagesToProcess = when {
                    action == PrivilegedAction.SWITCH_US_TIMEZONE ||
                        action == PrivilegedAction.RESTORE_HOME_TIMEZONE -> ""
                    targetPackagesOverride != null -> targetPackagesOverride.joinToString(",")
                    action == PrivilegedAction.RESTORE_CN_DEFAULT -> {
                        // 恢复默认时将内置四大应用及用户曾勾选的全部扩展应用一并还原为跟随系统中文
                        (CoreSuiteApp.ALL_BUILTIN_PACKAGES +
                            currentSettings.extraEnglishPackages +
                            currentSettings.confirmedEnglishPackages)
                            .map { it.trim() }
                            .filter { StatesideDefaults.isValidPackageName(it) }
                            .distinct()
                            .joinToString(",")
                    }
                    else -> currentSettings.effectiveEnglishPackages()
                        .map { it.trim() }
                        .filter { StatesideDefaults.isValidPackageName(it) }
                        .distinct()
                        .joinToString(",")
                }

                val pkg = appContext.packageName
                val cmd = buildString {
                    append("pm grant ").append(pkg).append(" android.permission.PACKAGE_USAGE_STATS >/dev/null 2>&1; ")
                    append("cmd appops set ").append(pkg).append(" GET_USAGE_STATS allow >/dev/null 2>&1; ")
                    append("am instrument -w --no-restart ")
                    append("-e action ").append(action.name).append(' ')
                    if (carrier != null) {
                        append("-e carrier_id ").append(effectiveCarrier.id).append(' ')
                    }
                    if (!explicitTimezoneId.isNullOrBlank()) {
                        append("-e tz_id '").append(explicitTimezoneId.trim()).append("' ")
                    }
                    if (packagesToProcess.isNotEmpty()) {
                        append("-e target_pkgs '").append(packagesToProcess).append("' ")
                    }
                    append("$pkg/.privileged.StatesideEnvInstrumentation")
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
                    PrivilegedAction.SWITCH_US_TIMEZONE,
                    PrivilegedAction.RESTORE_HOME_TIMEZONE -> Unit
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
            val input = stream.openInputStream()
            val outBytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!stream.isClosed) {
                val readCount = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    // libadb 在远端 shell 正常执行完毕并发送 CLSE 关闭流时，read()/available() 会抛出 "Stream closed."，属正常 EOF
                    if (stream.isClosed || e.message?.contains("Stream closed", ignoreCase = true) == true) {
                        break
                    }
                    throw e
                }
                if (readCount < 0) break
                if (readCount > 0) {
                    outBytes.write(buffer, 0, readCount)
                }
            }
            return outBytes.toString(StandardCharsets.UTF_8.name())
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
