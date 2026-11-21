package app.stateside.env.boot

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.stateside.env.R
import app.stateside.env.StatesideApp
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BootRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = UserPrefsRepository(context)
        val current = prefs.settings.value
        if (!current.autoRestoreOnBoot || !current.isStatesideDesired) {
            return
        }

        // 若当前美境已生效（例如应用覆盖升级但手机并未重启），无需重复覆写
        if (AppLaunchGuardHelper.isStatesideReadyForFastLaunch(context)) {
            return
        }

        // 立即向系统注册「Wi-Fi 就绪即自动恢复美境」任务，彻底攻克刚开机时 Wi-Fi 尚未连接导致无线调试重连超时的问题
        BootRestoreJobService.scheduleWifiRestore(context)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val adb = LocalAdbManager.getInstance(context)
                val hasRoot = adb.detectRootAccess()
                if (!hasRoot && !current.isAdbPaired) {
                    return@launch
                }

                // 在 BroadcastReceiver 10s ANR 窗口内做一次快速尝试（Root 设备或 Wi-Fi 已秒连机型可当场完成）
                val result = withTimeoutOrNull(7_000L) {
                    adb.runEnvironmentTask(
                        action = PrivilegedAction.APPLY_US_ENV,
                        carrier = current.selectedCarrier,
                        targetPackagesOverride = current.effectiveEnglishPackages()
                    )
                }

                if (result?.isSuccess == true) {
                    BootRestoreJobService.cancelPending(context)
                    notifyBootRestored(context, result.getOrThrow().summaryText)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NOTIF_BOOT_RESTORED = 2001

        fun notifyBootRestored(context: Context, summary: String) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val notif = Notification.Builder(context, StatesideApp.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_stat_stateside)
                .setContentTitle("美境已自动恢复")
                .setContentText(summary)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(NOTIF_BOOT_RESTORED, notif) }
        }
    }
}
