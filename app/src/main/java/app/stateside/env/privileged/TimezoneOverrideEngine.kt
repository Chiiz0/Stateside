package app.stateside.env.privileged

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import app.stateside.env.data.UserPrefsRepository
import java.util.TimeZone

data class TimezoneOutcome(
    val success: Boolean,
    val activeZoneId: String,
    val autoTimezoneEnabled: Boolean,
    val error: String = ""
)

object TimezoneOverrideEngine {

    /**
     * 切换系统时区至指定美区时区（需在 adoptShellPermissionIdentity 下执行）：
     * 1. 若当前处于非美区原时区（如 Asia/Shanghai），自动记录原始时区与自动时区开关状态；
     * 2. 自动为本应用自身开启 GET_USAGE_STATS AppOp，供「动态伴随模式」零打扰感知应用退出；
     * 3. 先关闭 AUTO_TIME_ZONE 防止基站 NITZ 立即覆写，再调用 AlarmManager.setTimeZone 切至美区并核验生效。
     */
    fun applyUsTimezone(
        context: Context,
        repo: UserPrefsRepository,
        targetZoneId: String
    ): TimezoneOutcome {
        grantUsageStatsAppOpToSelf(context)

        val cr = context.contentResolver
        val beforeZone = readLiveSystemTimezone()
        val beforeAuto = runCatching {
            Settings.Global.getInt(cr, Settings.Global.AUTO_TIME_ZONE, 1) == 1
        }.getOrDefault(true)

        repo.captureHomeTimezoneBaselineIfNeeded(beforeZone, beforeAuto)

        return runCatching {
            Settings.Global.putInt(cr, Settings.Global.AUTO_TIME_ZONE, 0)
            val alarmManager = context.getSystemService(AlarmManager::class.java)
                ?: error("无法获取系统 AlarmManager 服务")
            alarmManager.setTimeZone(targetZoneId)

            var settledZone = readLiveSystemTimezone()
            repeat(16) {
                if (settledZone.equals(targetZoneId, ignoreCase = true)) return@repeat
                Thread.sleep(50L)
                settledZone = readLiveSystemTimezone()
            }
            TimeZone.setDefault(null)

            val ok = settledZone.equals(targetZoneId, ignoreCase = true)
            if (ok) {
                repo.markTimezoneOverridden(true)
            }
            TimezoneOutcome(
                success = ok,
                activeZoneId = settledZone,
                autoTimezoneEnabled = false,
                error = if (ok) "" else "时区切换未生效（当前仍为 $settledZone）"
            )
        }.getOrElse { e ->
            TimezoneOutcome(
                success = false,
                activeZoneId = readLiveSystemTimezone(),
                autoTimezoneEnabled = beforeAuto,
                error = e.message ?: "设置美区时区失败"
            )
        }
    }

    /**
     * 归位至用户原机时区（如 Asia/Shanghai）并恢复原有的「自动确定时区」开关状态。
     */
    fun restoreHomeTimezone(
        context: Context,
        repo: UserPrefsRepository
    ): TimezoneOutcome {
        val cr = context.contentResolver
        val settings = repo.settings.value
        val homeZone = settings.savedHomeTimezoneId.ifBlank { "Asia/Shanghai" }
        val restoreAuto = settings.savedHomeAutoTimezone

        return runCatching {
            Settings.Global.putInt(cr, Settings.Global.AUTO_TIME_ZONE, 0)
            val alarmManager = context.getSystemService(AlarmManager::class.java)
                ?: error("无法获取系统 AlarmManager 服务")
            alarmManager.setTimeZone(homeZone)

            var settledZone = readLiveSystemTimezone()
            repeat(16) {
                if (settledZone.equals(homeZone, ignoreCase = true)) return@repeat
                Thread.sleep(50L)
                settledZone = readLiveSystemTimezone()
            }
            TimeZone.setDefault(null)

            if (restoreAuto) {
                runCatching { Settings.Global.putInt(cr, Settings.Global.AUTO_TIME_ZONE, 1) }
            }
            repo.markTimezoneOverridden(false)

            val finalAuto = runCatching {
                Settings.Global.getInt(cr, Settings.Global.AUTO_TIME_ZONE, 1) == 1
            }.getOrDefault(restoreAuto)

            TimezoneOutcome(
                success = settledZone.equals(homeZone, ignoreCase = true),
                activeZoneId = settledZone,
                autoTimezoneEnabled = finalAuto
            )
        }.getOrElse { e ->
            TimezoneOutcome(
                success = false,
                activeZoneId = readLiveSystemTimezone(),
                autoTimezoneEnabled = restoreAuto,
                error = e.message ?: "归位原时区失败"
            )
        }
    }

    fun grantUsageStatsAppOpToSelf(context: Context) {
        runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return
            val setUidMode = AppOpsManager::class.java.getMethod(
                "setUidMode",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setUidMode.invoke(
                appOps,
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                AppOpsManager.MODE_ALLOWED
            )
        }
    }

    fun readLiveSystemTimezone(): String {
        val fromProp = runCatching {
            val sp = Class.forName("android.os.SystemProperties")
            sp.getMethod("get", String::class.java, String::class.java)
                .invoke(null, "persist.sys.timezone", "") as? String
        }.getOrNull()?.trim().orEmpty()
        return fromProp.ifEmpty { TimeZone.getDefault().id }
    }
}
