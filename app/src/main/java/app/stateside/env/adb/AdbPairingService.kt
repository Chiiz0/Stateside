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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private var submissionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WATCH -> startWatchingPairingPort()
            ACTION_SUBMIT_CODE -> {
                val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                val rawFromRemote = remoteInputResults
                    ?.getCharSequence(KEY_REMOTE_PAIRING_CODE)
                    ?.toString()
                    .orEmpty()
                val rawInput = rawFromRemote.ifBlank {
                    intent.getStringExtra(EXTRA_CODE).orEmpty()
                }
                val extraPort = intent.getIntExtra(EXTRA_PORT, 0)
                val effectivePort = if (extraPort in 1..65535) extraPort else latestDetectedPort
                val effectiveHost = intent.getStringExtra(EXTRA_HOST)
                    ?.takeIf { it.isNotBlank() }
                    ?: latestDetectedHost
                handleCodeSubmission(effectiveHost, effectivePort, rawInput)
            }
            ACTION_STOP -> {
                radarJob?.cancel()
                submissionJob?.cancel()
                _phase.value = PairingUiPhase.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                promoteForegroundSafely(buildSearchingNotification(allowManualPortCodeInput = false))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun promoteForegroundSafely(notification: Notification) {
        val started = runCatching {
            startForeground(
                NOTIF_ID_PAIRING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            true
        }.getOrDefault(false)

        if (!started) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID_PAIRING, notification)
        }
    }

    private fun onPairingEndpointDetected(endpoint: AdbEndpoint) {
        if (endpoint.port !in 1..65535) return
        if (_phase.value is PairingUiPhase.PairingInProgress ||
            _phase.value is PairingUiPhase.PairedAndApplied
        ) {
            return
        }
        if (latestDetectedPort == endpoint.port &&
            latestDetectedHost == endpoint.host &&
            _phase.value is PairingUiPhase.ReadyForCode
        ) {
            return
        }
        latestDetectedPort = endpoint.port
        latestDetectedHost = endpoint.host
        _phase.value = PairingUiPhase.ReadyForCode(endpoint.port)
        promoteForegroundSafely(buildCodeInputNotification(endpoint.host, endpoint.port))
    }

    private fun startWatchingPairingPort() {
        submissionJob?.cancel()
        latestDetectedPort = 0
        latestDetectedHost = "127.0.0.1"
        _phase.value = PairingUiPhase.SearchingPairingDialog
        // 搜索阶段仅展示等待提示与「取消配对」，与 Shizuku-Next 一致避免端口尚未捕获时误填 0 端口
        promoteForegroundSafely(buildSearchingNotification(allowManualPortCodeInput = false))

        radarJob?.cancel()
        radarJob = scope.launch {
            launch {
                AdbMdnsDiscovery.observeLocalEndpoint(applicationContext, AdbMdnsType.TLS_PAIRING)
                    .distinctUntilChanged()
                    .collect { endpoint ->
                        onPairingEndpointDetected(endpoint)
                    }
            }

            // 若 6 秒后仍未收到 mDNS（如校园网 AP 隔离、VPN 拦截组播或未授本地网络权限），自动补充「端口 配对码」手填入口
            launch {
                delay(6_000L)
                if (latestDetectedPort <= 0 && _phase.value is PairingUiPhase.SearchingPairingDialog) {
                    promoteForegroundSafely(buildSearchingNotification(allowManualPortCodeInput = true))
                }
            }
        }
    }

    private fun handleCodeSubmission(host: String, fallbackPort: Int, rawInput: String) {
        val parsed = PairingInputParser.parse(rawInput, fallbackPort)
        if (parsed == null) {
            val hint = if (fallbackPort in 1..65535) {
                "请输入弹窗上的 6 位数字配对码（如 184109）"
            } else {
                "尚未自动捕获配对端口：请先点开「使用配对码配对设备」，或直接输入「端口 6位配对码」（如 44775 123456）"
            }
            _phase.value = PairingUiPhase.Failed(hint, fallbackPort)
            val errNotif = if (fallbackPort in 1..65535) {
                buildCodeInputNotification(host, fallbackPort, subtitleOverride = hint)
            } else {
                buildSearchingNotification(allowManualPortCodeInput = true, subtitleOverride = hint)
            }
            promoteForegroundSafely(errNotif)
            return
        }

        val targetPort = parsed.port
        val cleanCode = parsed.sixDigitCode
        latestDetectedPort = targetPort

        _phase.value = PairingUiPhase.PairingInProgress(targetPort)
        promoteForegroundSafely(
            buildProgressNotification("正在与本机无线调试握手配对（端口 $targetPort）…")
        )

        submissionJob?.cancel()
        submissionJob = scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            val adb = LocalAdbManager.getInstance(applicationContext)

            // 8.5 秒硬超时保护：彻底杜绝任何情况下卡死在「正在完成中」
            val pairOutcome = withTimeoutOrNull(8_500L) {
                adb.pairWithCode(
                    port = targetPort,
                    sixDigitCode = cleanCode,
                    preferredHost = host
                )
            }

            if (pairOutcome == null || pairOutcome.isFailure) {
                val errMsg = pairOutcome?.exceptionOrNull()?.message
                    ?: "连接配对端口 $targetPort 超时，请核对当前无线调试弹窗上的端口号与最新 6 位配对码"
                _phase.value = PairingUiPhase.Failed(errMsg, targetPort)
                val retryNotif = if (targetPort in 1..65535) {
                    buildCodeInputNotification(host, targetPort, subtitleOverride = errMsg)
                } else {
                    buildSearchingNotification(allowManualPortCodeInput = true, subtitleOverride = errMsg)
                }
                promoteForegroundSafely(retryNotif)
                return@launch
            }

            // 配对已成功落盘！停止配对搜索雷达，更新进度文案
            radarJob?.cancel()
            promoteForegroundSafely(
                buildProgressNotification("无线调试配对成功！正在连接并为您开启美境…")
            )

            // 等待系统 adbd 完成新配对公钥重载并广播 _adb-tls-connect._tcp
            delay(550L)

            val applyResult = withTimeoutOrNull(14_000L) {
                adb.runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)
            }

            if (applyResult != null && applyResult.isSuccess) {
                val summary = applyResult.getOrThrow().summaryText
                _phase.value = PairingUiPhase.PairedAndApplied(summary)
                nm?.notify(NOTIF_ID_RESULT, buildSuccessNotification(summary))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val err = applyResult?.exceptionOrNull()?.message
                    ?: "无线调试已完成配对！请返回美境 App 点击「一键开启美境」（并确认已开启开发者选项底部的安全开关）"
                _phase.value = PairingUiPhase.Failed("无线调试已成功绑定！最后一步提示：$err", targetPort)
                nm?.notify(NOTIF_ID_RESULT, buildWarningNotification(err))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildSearchingNotification(
        allowManualPortCodeInput: Boolean,
        subtitleOverride: String? = null
    ): Notification {
        val stopPending = PendingIntent.getForegroundService(
            this,
            REQ_STOP,
            Intent(this, AdbPairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle(
                if (allowManualPortCodeInput) {
                    "等待配对弹窗（支持直接输入「端口 配对码」）"
                } else {
                    "正在等待点开「使用配对码配对设备」…"
                }
            )
            .setContentText(
                subtitleOverride ?: if (allowManualPortCodeInput) {
                    "点开弹窗后若仍未自动捕获端口（如校园网/VPN），可点下方输入「端口 6位配对码」"
                } else {
                    "请在系统「无线调试」中点开「使用配对码配对设备」，捕获端口后此通知将立即切换为输入框"
                }
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)

        if (allowManualPortCodeInput) {
            val remoteInput = RemoteInput.Builder(KEY_REMOTE_PAIRING_CODE)
                .setLabel("输入：端口 6位配对码（如 44775 123456）")
                .build()

            val manualSubmitIntent = Intent(this, AdbPairingService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                putExtra(EXTRA_HOST, "127.0.0.1")
                putExtra(EXTRA_PORT, 0)
            }
            val manualSubmitPending = PendingIntent.getForegroundService(
                this,
                REQ_MANUAL_SUBMIT,
                manualSubmitIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val replyAction = Notification.Action.Builder(
                null,
                "输入「端口 配对码」",
                manualSubmitPending
            ).addRemoteInput(remoteInput).build()
            builder.addAction(replyAction)
        }

        builder.addAction(
            Notification.Action.Builder(null, getString(R.string.notif_action_stop), stopPending).build()
        )
        return builder.build()
    }

    private fun buildCodeInputNotification(
        host: String,
        port: Int,
        subtitleOverride: String? = null
    ): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REMOTE_PAIRING_CODE)
            .setLabel(getString(R.string.notif_input_hint))
            .build()

        // 直接使用 PendingIntent.getForegroundService 投递至前台服务，绕开 ColorOS OplusHansManager 后台广播冻结队列
        val submitIntent = Intent(this, AdbPairingService::class.java).apply {
            action = ACTION_SUBMIT_CODE
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
        }
        val submitPending = PendingIntent.getForegroundService(
            this,
            REQ_AUTO_SUBMIT,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val stopPending = PendingIntent.getForegroundService(
            this,
            REQ_STOP,
            Intent(this, AdbPairingService::class.java).setAction(ACTION_STOP),
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
            .setContentText(
                subtitleOverride ?: "保持配对码弹窗开启，点击下方「提交配对码」填入 6 位数字即可完成激活"
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .addAction(replyAction)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.notif_action_stop), stopPending).build()
            )
            .build()
    }

    private fun buildProgressNotification(message: String): Notification {
        val stopPending = PendingIntent.getForegroundService(
            this,
            REQ_STOP,
            Intent(this, AdbPairingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("正在开启美境（Stateside）…")
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.notif_action_stop), stopPending).build()
            )
            .build()
    }

    private fun buildSuccessNotification(summary: String): Notification {
        val openMain = PendingIntent.getActivity(
            this,
            REQ_OPEN_MAIN_OK,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
            REQ_OPEN_MAIN_WARN,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, StatesideApp.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("无线调试已绑定，请点击查看最后一步提示")
            .setContentText(reason)
            .setContentIntent(openMain)
            .setAutoCancel(true)
            .build()
    }

    override fun onDestroy() {
        radarJob?.cancel()
        submissionJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_WATCH = "app.stateside.env.action.START_WATCH"
        const val ACTION_SUBMIT_CODE = "app.stateside.env.action.SUBMIT_CODE"
        const val ACTION_STOP = "app.stateside.env.action.STOP_WATCH"
        const val EXTRA_CODE = "extra_pairing_code"
        const val EXTRA_PORT = "extra_pairing_port"
        const val EXTRA_HOST = "extra_pairing_host"
        const val KEY_REMOTE_PAIRING_CODE = "remote_input_6digit_code"

        private const val NOTIF_ID_PAIRING = 1001
        private const val NOTIF_ID_RESULT = 1002

        private const val REQ_STOP = 1
        private const val REQ_AUTO_SUBMIT = 2
        private const val REQ_OPEN_MAIN_OK = 3
        private const val REQ_OPEN_MAIN_WARN = 4
        private const val REQ_MANUAL_SUBMIT = 5

        @Volatile
        var latestDetectedPort: Int = 0
            private set

        @Volatile
        var latestDetectedHost: String = "127.0.0.1"
            private set

        private val _phase = MutableStateFlow<PairingUiPhase>(PairingUiPhase.Idle)
        val phase: StateFlow<PairingUiPhase> = _phase.asStateFlow()

        fun startWatching(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_START_WATCH)
            context.startForegroundService(intent)
        }

        fun submitCodeFromUi(context: Context, port: Int, code: String, host: String = latestDetectedHost) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                putExtra(EXTRA_HOST, host)
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
