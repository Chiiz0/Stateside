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
    private var mdnsJob: Job? = null

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
                // 防止空 Intent 重建时触发 ForegroundServiceDidNotStartInTimeException
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

    private fun startWatchingPairingPort() {
        _phase.value = PairingUiPhase.SearchingPairingDialog
        promoteForegroundSafely(buildSearchingNotification())

        mdnsJob?.cancel()
        mdnsJob = scope.launch {
            AdbMdnsDiscovery.observeLocalPort(applicationContext, AdbMdnsType.TLS_PAIRING)
                .distinctUntilChanged()
                .collect { port ->
                    latestDetectedPort = port
                    _phase.value = PairingUiPhase.ReadyForCode(port)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIF_ID_PAIRING, buildCodeInputNotification(port))
                }
        }
    }

    private fun handleCodeSubmission(fallbackPort: Int, rawInput: String) {
        val parsed = PairingInputParser.parse(rawInput, fallbackPort)
        if (parsed == null) {
            val hint = if (fallbackPort > 0) {
                "配对码应为 6 位数字，请重新输入"
            } else {
                "尚未自动识别到弹窗端口，请先点开「使用配对码配对设备」，或直接输入「端口 6位配对码」（如 38941 123456）"
            }
            _phase.value = PairingUiPhase.Failed(hint, fallbackPort)
            val errNotif = if (fallbackPort > 0) {
                buildCodeInputNotification(fallbackPort, subtitleOverride = hint)
            } else {
                buildSearchingNotification(subtitleOverride = hint)
            }
            // 必须调用 startForeground 以满足 Android 14~17 startForegroundService 5 秒硬性要求
            promoteForegroundSafely(errNotif)
            return
        }

        val port = parsed.port
        latestDetectedPort = port
        _phase.value = PairingUiPhase.PairingInProgress(port)
        val progressNotif = buildProgressNotification("正在完成无线调试配对并开启美境环境…")
        promoteForegroundSafely(progressNotif)

        scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            val adb = LocalAdbManager.getInstance(applicationContext)
            val pairResult = adb.pairWithCode(port, parsed.sixDigitCode)
            if (pairResult.isFailure) {
                val msg = pairResult.exceptionOrNull()?.message ?: "配对失败，请确认配对码未过期"
                _phase.value = PairingUiPhase.Failed(msg, port)
                nm?.notify(NOTIF_ID_PAIRING, buildCodeInputNotification(port, subtitleOverride = msg))
                return@launch
            }

            // 等待国产 ROM adbd 完成新配对公钥落盘重载后再发起 TLS 连接
            delay(400L)

            val applyResult = adb.runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)
            if (applyResult.isSuccess) {
                val summary = applyResult.getOrThrow().summaryText
                _phase.value = PairingUiPhase.PairedAndApplied(summary)
                nm?.notify(NOTIF_ID_RESULT, buildSuccessNotification(summary))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                val err = applyResult.exceptionOrNull()?.message ?: "无线调试已配对成功，请点击查看最后一步开关设置"
                _phase.value = PairingUiPhase.Failed(err, port)
                nm?.notify(NOTIF_ID_RESULT, buildWarningNotification(err))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildSearchingNotification(subtitleOverride: String? = null): Notification {
        val remoteInput = RemoteInput.Builder(KEY_REMOTE_PAIRING_CODE)
            .setLabel("输入「端口 6位配对码」（如 38941 123456）")
            .build()

        val manualSubmitPending = PendingIntent.getBroadcast(
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

        val manualReplyAction = Notification.Action.Builder(
            null,
            "手填端口与配对码",
            manualSubmitPending
        ).addRemoteInput(remoteInput).build()

        return Notification.Builder(this, StatesideApp.CHANNEL_PAIRING)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("美境激活：请点开「使用配对码配对设备」")
            .setContentText(
                subtitleOverride
                    ?: "自动扫描弹窗中；若校园网/VPN 拦截扫描，可直接点下方按钮输入「端口 配对码」"
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(manualReplyAction)
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
            .setContentTitle("已识别配对弹窗（端口 $port）")
            .setContentText(subtitleOverride ?: "请点击下方「提交配对码」，直接填入屏幕上的 6 位数字")
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
        mdnsJob?.cancel()
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
