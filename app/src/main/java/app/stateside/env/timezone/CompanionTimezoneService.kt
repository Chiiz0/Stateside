package app.stateside.env.timezone

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.view.inputmethod.InputMethodManager
import app.stateside.env.R
import app.stateside.env.StatesideApp
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.model.TimezoneMode
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
import kotlinx.coroutines.launch

sealed interface CompanionTzSessionState {
    data object Idle : CompanionTzSessionState
    data class Active(
        val appTitle: String,
        val targetPackage: String,
        val zoneLabel: String
    ) : CompanionTzSessionState
    data class WaitingRestoreRetry(
        val zoneLabel: String,
        val reason: String
    ) : CompanionTzSessionState
}

/**
 * 方案二「随开随切 · 退出即还」动态伴随时区前台守护服务：
 * 仅在用户启动了勾选伴随时区的目标应用（如 TikTok）期间运行，一旦检测到用户退回桌面或切换至非伴随应用，
 * 自动静默归位原时区并立即停止服务（平时零后台驻留、零耗电）。
 */
class CompanionTimezoneService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var isRestoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COMPANION -> {
                val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "目标应用" }
                val zoneLabel = intent.getStringExtra(EXTRA_ZONE_LABEL).orEmpty().ifBlank { "美西洛杉矶" }
                startMonitoringSession(pkg, title, zoneLabel)
            }
            ACTION_RESTORE_NOW -> {
                triggerRestoreAndStop(manualTrigger = true)
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMonitoringSession(
        targetPackage: String,
        appTitle: String,
        zoneLabel: String
    ) {
        monitorJob?.cancel()
        isRestoring = false
        _sessionState.value = CompanionTzSessionState.Active(
            appTitle = appTitle,
            targetPackage = targetPackage,
            zoneLabel = zoneLabel
        )
        promoteForegroundSafely(buildActiveNotification(appTitle, zoneLabel))

        monitorJob = scope.launch {
            val prefs = UserPrefsRepository(applicationContext)
            val usageManager = getSystemService(UsageStatsManager::class.java)
            val powerManager = getSystemService(PowerManager::class.java)
            val imePackages = queryEnabledImePackages()

            val sessionStartElapsed = System.currentTimeMillis()
            var lastKnownForegroundPkg = targetPackage
            var hasConfirmedTargetEntered = false
            var consecutiveNonCompanionChecks = 0
            var consecutiveScreenOffChecks = 0

            while (true) {
                delay(1_800L)

                val currentSettings = prefs.settings.value
                if (currentSettings.timezoneMode != TimezoneMode.COMPANION_APP) {
                    // 用户在中途切换了全局模式，直接结束伴随监听
                    _sessionState.value = CompanionTzSessionState.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val interactive = powerManager?.isInteractive ?: true
                if (!interactive) {
                    consecutiveScreenOffChecks++
                    if (consecutiveScreenOffChecks >= 8) { // 息屏超过约 14 秒自动归位原时区
                        triggerRestoreAndStop(manualTrigger = false)
                        return@launch
                    }
                    continue
                } else {
                    consecutiveScreenOffChecks = 0
                }

                if (usageManager != null && hasUsageStatsPermission(applicationContext)) {
                    val latestResumed = queryLatestResumedNonTransientPackage(
                        usageManager = usageManager,
                        imePackages = imePackages
                    )
                    if (!latestResumed.isNullOrBlank()) {
                        lastKnownForegroundPkg = latestResumed
                    }

                    val companionGroup = currentSettings.companionTimezonePackages + setOf(targetPackage)
                    val inCompanionApp = lastKnownForegroundPkg in companionGroup

                    if (inCompanionApp) {
                        hasConfirmedTargetEntered = true
                        consecutiveNonCompanionChecks = 0
                    } else {
                        val sinceStartMs = System.currentTimeMillis() - sessionStartElapsed
                        // 启动前 8 秒内给予目标应用冷启动缓冲窗口，避免刚点击启动还在桌面过渡动画时误判退出
                        if (hasConfirmedTargetEntered || sinceStartMs > 8_500L) {
                            consecutiveNonCompanionChecks++
                            if (consecutiveNonCompanionChecks >= 2) {
                                triggerRestoreAndStop(manualTrigger = false)
                                return@launch
                            }
                        }
                    }
                }
            }
        }
    }

    private fun triggerRestoreAndStop(manualTrigger: Boolean) {
        if (isRestoring) return
        isRestoring = true
        monitorJob?.cancel()

        scope.launch {
            val result = LocalAdbManager.getInstance(applicationContext)
                .runEnvironmentTask(PrivilegedAction.RESTORE_HOME_TIMEZONE)

            if (result.isSuccess) {
                _sessionState.value = CompanionTzSessionState.Idle
                StatesideApp.instance.prefs.reload()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                isRestoring = false
                val zoneLabel = UserPrefsRepository(applicationContext).settings.value.selectedUsTimezone.cityLabel
                val errMsg = if (manualTrigger) {
                    "请确认已连上 Wi-Fi 且开启无线调试后再点重试归位"
                } else {
                    "检测到已退出应用，连接无线调试后点此一键归位原时区"
                }
                _sessionState.value = CompanionTzSessionState.WaitingRestoreRetry(
                    zoneLabel = zoneLabel,
                    reason = errMsg
                )
                promoteForegroundSafely(buildRetryNotification(zoneLabel, errMsg))
            }
        }
    }

    private fun queryLatestResumedNonTransientPackage(
        usageManager: UsageStatsManager,
        imePackages: Set<String>
    ): String? {
        val now = System.currentTimeMillis()
        val events = runCatching {
            usageManager.queryEvents(now - 12_000L, now)
        }.getOrNull() ?: return null

        val event = UsageEvents.Event()
        var latestPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName.orEmpty()
                val cls = event.className.orEmpty()
                if (pkg.isNotEmpty() && !isTransientOverlay(pkg, cls, imePackages)) {
                    latestPkg = pkg
                }
            }
        }
        return latestPkg
    }

    private fun isTransientOverlay(
        pkg: String,
        className: String,
        imePackages: Set<String>
    ): Boolean {
        if (pkg in imePackages) return true
        if (pkg == packageName && className.contains("SafeLaunchActivity")) return true
        return pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg.contains("permissioncontroller", ignoreCase = true) ||
            pkg.contains("securitypermission", ignoreCase = true) ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("screenshot", ignoreCase = true)
    }

    private fun queryEnabledImePackages(): Set<String> = runCatching {
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            ?.toSet()
            .orEmpty()
    }.getOrDefault(emptySet())

    private fun promoteForegroundSafely(notification: Notification) {
        val ok = runCatching {
            startForeground(
                NOTIF_ID_COMPANION_TZ,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            true
        }.getOrDefault(false)

        if (!ok) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID_COMPANION_TZ, notification)
        }
    }

    private fun buildActiveNotification(appTitle: String, zoneLabel: String): Notification {
        val restoreIntent = Intent(this, CompanionTimezoneService::class.java).apply {
            action = ACTION_RESTORE_NOW
        }
        val restorePending = PendingIntent.getForegroundService(
            this,
            201,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentPending = PendingIntent.getActivity(
            this,
            202,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionRestore = Notification.Action.Builder(
            null,
            "立即归位原时区",
            restorePending
        ).build()

        return Notification.Builder(this, StatesideApp.CHANNEL_COMPANION_TZ)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("伴随时区运行中 · $zoneLabel")
            .setContentText("正在为 $appTitle 维持美区时区，退出应用后将自动归位")
            .setContentIntent(contentPending)
            .setOngoing(true)
            .addAction(actionRestore)
            .build()
    }

    private fun buildRetryNotification(zoneLabel: String, tip: String): Notification {
        val restoreIntent = Intent(this, CompanionTimezoneService::class.java).apply {
            action = ACTION_RESTORE_NOW
        }
        val restorePending = PendingIntent.getForegroundService(
            this,
            203,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionRetry = Notification.Action.Builder(
            null,
            "重试归位",
            restorePending
        ).build()

        return Notification.Builder(this, StatesideApp.CHANNEL_COMPANION_TZ)
            .setSmallIcon(R.drawable.ic_stat_stateside)
            .setContentTitle("美境时区待归位（当前：$zoneLabel）")
            .setContentText(tip)
            .setOngoing(false)
            .addAction(actionRetry)
            .build()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID_COMPANION_TZ = 1096
        private const val ACTION_START_COMPANION = "app.stateside.env.ACTION_START_COMPANION_TZ"
        private const val ACTION_RESTORE_NOW = "app.stateside.env.ACTION_RESTORE_TZ_NOW"
        private const val EXTRA_PKG = "extra_pkg"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ZONE_LABEL = "extra_zone_label"

        private val _sessionState = MutableStateFlow<CompanionTzSessionState>(CompanionTzSessionState.Idle)
        val sessionState: StateFlow<CompanionTzSessionState> = _sessionState.asStateFlow()

        fun startSession(context: Context, targetPackage: String, appTitle: String, zoneLabel: String) {
            val intent = Intent(context, CompanionTimezoneService::class.java).apply {
                action = ACTION_START_COMPANION
                putExtra(EXTRA_PKG, targetPackage)
                putExtra(EXTRA_TITLE, appTitle)
                putExtra(EXTRA_ZONE_LABEL, zoneLabel)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun requestImmediateRestore(context: Context) {
            val intent = Intent(context, CompanionTimezoneService::class.java).apply {
                action = ACTION_RESTORE_NOW
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun stopQuietly(context: Context) {
            _sessionState.value = CompanionTzSessionState.Idle
            runCatching { context.stopService(Intent(context, CompanionTimezoneService::class.java)) }
        }

        @Suppress("DEPRECATION")
        fun hasUsageStatsPermission(context: Context): Boolean = runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
