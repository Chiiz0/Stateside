package app.stateside.env.adb

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.stateside.env.R
import app.stateside.env.StatesideApp
import app.stateside.env.model.PairingInputParser
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PairingUiPhase {
    data object Idle : PairingUiPhase
    data object SearchingPairingDialog : PairingUiPhase
    data class ReadyForCode(val pairingPort: Int) : PairingUiPhase
    data class PairingInProgress(val pairingPort: Int) : PairingUiPhase
    data class PairedAndApplied(val summary: String) : PairingUiPhase
    data class Failed(val reason: String, val lastPort: Int) : PairingUiPhase
}

class AdbPairingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var radarJob: Job? = null

    @Volatile
    private var baselineDualStackPorts: Set<Int> = emptySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WATCH -> startWatchingPairingPort()
            ACTION_SUBMIT_CODE -> {
                val rawInput = intent.getStringExtra(EXTRA_CODE).orEmpty()
                val fallbackPort = intent.getIntExtra(EXTRA_PORT, latestDetectedPort)
                handleCodeSubmission(fallbackPort, rawInput)
            }
            ACTION_STOP -> {
                _phase.value = PairingUiPhase.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                promoteForegroundSafely(buildSearchingNotification())
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteForegroundSafely(notification: Notification) {
        runCatching {
            startForeground(
                NOTIF_ID_PAIRING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
    }

    private fun onPairingPortDetected(port: Int) {
        if (port !in 1..65535) return
        if (latestDetectedPort == port && _phase.value is PairingUiPhase.ReadyForCode) return
        latestDetectedPort = port
        _phase.value = PairingUiPhase.ReadyForCode(port)
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID_PAIRING, buildCodeInputNotification(port))
    }

    private fun startWatchingPairingPort() {
        latestDetectedPort = 0
        _phase.value = PairingUiPhase.SearchingPairingDialog
        promoteForegroundSafely(buildSearchingNotification())

        radarJob?.cancel()
        radarJob = scope.launch {
            // 通道 1：标准 mDNS 监听（在已授予 Android 17 本地网络权限的常规 Wi-Fi 下即时响应）
            launch {
                AdbMdnsDiscovery.observeLocalPort(applicationContext, AdbMdnsType.TLS_PAIRING)
                    .distinctUntilChanged()
                    .collect { port ->
                        onPairingPortDetected(port)
                    }
            }

            // 通道 2：零权限内核回环双栈端口差分雷达（即使未授 Android 17 本地网络权限、开着 v2ray/Clash VPN 或校园网 AP 隔离也能自动识别弹窗端口！）
            launch {
                val initialPorts = AdbMdnsDiscovery.scanLocalDualStackCandidatePorts(applicationContext)
                baselineDualStackPorts = initialPorts
                while (isActive) {
                    delay(1_200L)
                    if (_phase.value is PairingUiPhase.PairingInProgress ||
                        _phase.value is PairingUiPhase.PairedAndApplied
                    ) {
                        continue
                    }
                    val currentPorts = AdbMdnsDiscovery.scanLocalDualStackCandidatePorts(applicationContext)
                    val newlyOpened = currentPorts - baselineDualStackPorts
                    if (newlyOpened.size == 1) {
                        onPairingPortDetected(newlyOpened.first())
                    } else if (newlyOpened.size > 1 && latestDetectedPort !in newlyOpened) {
                        onPairingPortDetected(newlyOpened.first())
                    }
                }
            }
        }
    }

    private fun handleCodeSubmission(fallbackPort: Int, rawInput: String) {
        val parsedExplicit = PairingInputParser.parse(rawInput, fallbackPort)
        val digitsOnly = rawInput.filter { it.isDigit() }

        // 既没有解析出显式端口+配对码，也不是纯 6 位数字配对码时提示格式有误
        if (parsedExplicit == null && digitsOnly.length != 6) {
            val hint = "请输入屏幕上的 6 位数字配对码（如 184109）"
            _phase.value = PairingUiPhase.Failed(hint, fallbackPort)
            val errNotif = if (fallbackPort > 0) {
                buildCodeInputNotification(fallbackPort, subtitleOverride = hint)
            } else {
                buildSearchingNotification(subtitleOverride = hint)
            }
            promoteForegroundSafely(errNotif)
            return
        }

        val cleanCode = parsedExplicit?.sixDigitCode ?: digitsOnly
        val primaryPort = parsedExplicit?.port ?: 0

        _phase.value = PairingUiPhase.PairingInProgress(primaryPort)
        promoteForegroundSafely(buildProgressNotification("正在完成无线调试配对并自动开启美境…"))

        scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            val adb = LocalAdbManager.getInstance(applicationContext)

            // 构建候选配对端口队列：首选已捕获/手填端口 -> 新增监听端口 -> 全量本机双栈监听端口自动盲配！
            val candidatePorts = LinkedHashSet<Int>().apply {
                if (primaryPort in 1..65535) {
                    add(primaryPort)
                }
                val allDualStack = AdbMdnsDiscovery.scanLocalDualStackCandidatePorts(applicationContext)
                // 优先尝试启动雷达后新出现的端口（即刚点开的配对弹窗端口）
                addAll(allDualStack - baselineDualStackPorts)
                // 再兜底尝试其余双栈端口（应对用户先点开弹窗、后启动雷达的场景）
                addAll(allDualStack)
            }

            if (candidatePorts.isEmpty()) {
                val msg = "未检测到打开中的配对码弹窗！请保持「无线调试 → 使用配对码配对设备」弹窗处于打开状态，再下拉通知栏输入 6 位数字。"
                _phase.value = PairingUiPhase.Failed(msg, 0)
                nm?.notify(NOTIF_ID_PAIRING, buildSearchingNotification(subtitleOverride = msg))
                return@launch
            }

            var pairedSuccessPort = -1
            var lastErrorMsg = "配对未通过，请确认 6 位配对码未过期且配对弹窗未关闭"

            for (candidatePort in candidatePorts) {
                val res = adb.pairWithCode(candidatePort, cleanCode)
                if (res.isSuccess) {
                    pairedSuccessPort = candidatePort
                    latestDetectedPort = candidatePort
                    break
                } else {
                    res.exceptionOrNull()?.message?.let { lastErrorMsg = it }
                }
            }

            if (pairedSuccessPort <= 0) {
                _phase.value = PairingUiPhase.Failed(lastErrorMsg, primaryPort)
                val retryNotif = if (latestDetectedPort > 0 && AdbMdnsDiscovery.isLocalLoopbackPortListening(latestDetectedPort)) {
                    buildCodeInputNotification(latestDetectedPort, subtitleOverride = lastErrorMsg)
                } else {
                    buildSearchingNotification(subtitleOverride = lastErrorMsg)
                }
                nm?.notify(NOTIF_ID_PAIRING, retryNotif)
                return@launch
            }

            // 等待国产 ROM adbd 完成新配对公钥落盘重载后再发起 TLS 连接
            delay(450L)

            val applyResult = adb.runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)
            if (applyResult.isSuccess) {
                val summary = applyResult.getOrThrow().summaryText
                _phase.value = PairingUiPhase.PairedAndApplied(summary)
                nm?.notify(NOTIF_ID_RESULT, buildSuccessNotification(summary))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val err = applyResult.exceptionOrNull()?.message ?: "无线调试已配对成功，请点击查看最后一步开关设置"
                _phase.value = PairingUiPhase.Failed(err, pairedSuccessPort)
                nm?.notify(NOTIF_ID_RESULT, buildWarningNotification(err))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildSearchingNotification(subtitleOverride: String? = null): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REMOTE_PAIRING_CODE)
            .setLabel(getString(R.string.notif_input_hint))
            .build()

        val submitPending = PendingIntent.getBroadcast(
            this,
            5,
            Intent(this, PairingActionReceiver::class.java)
                .setPackage(packageName)
                .setAction(ACTION_SUBMIT_CODE)
                .putExtra(EXTRA_PORT, latestDetectedPort),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val stopPending = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, PairingActionReceiver::class.java)
                .setPackage(packageName)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyAction = Notification.Action.Builder(
            null,
            getString(R.string.notif_action_submit),
            submitPending
        ).addRemoteInput(remoteInput).build()

        return Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("美境激活：点开配对弹窗后直接在此输入 6 位配对码")
            .setContentText(
                subtitleOverride
                    ?: "请在无线调试中点开「使用配对码配对设备」，保持弹窗不关，直接点下方「提交配对码」填入 6 位数字"
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(replyAction)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_action_stop), stopPending).build())
            .build()
    }

    private fun buildCodeInputNotification(port: Int, subtitleOverride: String? = null): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REMOTE_PAIRING_CODE)
            .setLabel(getString(R.string.notif_input_hint))
            .build()

        val submitIntent = Intent(this, PairingActionReceiver::class.java).apply {
            setPackage(packageName)
            action = ACTION_SUBMIT_CODE
            putExtra(EXTRA_PORT, port)
        }
        val submitPending = PendingIntent.getBroadcast(
            this,
            2,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val stopPending = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, PairingActionReceiver::class.java)
                .setPackage(packageName)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyAction = Notification.Action.Builder(
            null,
            getString(R.string.notif_action_submit),
            submitPending
        ).addRemoteInput(remoteInput).build()

        return Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("已锁定配对弹窗（端口 $port）")
            .setContentText(subtitleOverride ?: "保持配对码弹窗开启，点击下方「提交配对码」填入 6 位数字即可完成激活")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .addAction(replyAction)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_action_stop), stopPending).build())
            .build()
    }

    private fun buildProgressNotification(message: String): Notification =
        Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("正在开启美境（Stateside）…")
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

    private fun buildSuccessNotification(summary: String): Notification {
        val openMain = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, StatesideApp.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("美境激活完成！环境已生效")
            .setContentText(summary)
            .setContentIntent(openMain)
            .setAutoCancel(true)
            .build()
    }

    private fun buildWarningNotification(reason: String): Notification {
        val openMain = PendingIntent.getActivity(
            this,
            4,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, StatesideApp.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("无线调试已配对，请点击查看最后一步设置")
            .setContentText(reason)
            .setContentIntent(openMain)
            .setAutoCancel(true)
            .build()
    }

    override fun onDestroy() {
        radarJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_WATCH = "app.stateside.env.action.START_WATCH"
        const val ACTION_SUBMIT_CODE = "app.stateside.env.action.SUBMIT_CODE"
        const val ACTION_STOP = "app.stateside.env.action.STOP_WATCH"
        const val EXTRA_CODE = "extra_pairing_code"
        const val EXTRA_PORT = "extra_pairing_port"
        const val KEY_REMOTE_PAIRING_CODE = "remote_input_6digit_code"

        private const val NOTIF_ID_PAIRING = 1001
        private const val NOTIF_ID_RESULT = 1002

        @Volatile
        var latestDetectedPort: Int = 0
            private set

        private val _phase = MutableStateFlow<PairingUiPhase>(PairingUiPhase.Idle)
        val phase: StateFlow<PairingUiPhase> = _phase.asStateFlow()

        fun startWatching(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_START_WATCH)
            context.startForegroundService(intent)
        }

        fun submitCodeFromUi(context: Context, port: Int, code: String) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_CODE, code)
            }
            context.startForegroundService(intent)
        }

        fun stopWatching(context: Context) {
            context.stopService(Intent(context, AdbPairingService::class.java))
            _phase.value = PairingUiPhase.Idle
        }
    }
}
