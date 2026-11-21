package app.stateside.env

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.stateside.env.data.UserPrefsRepository
import org.lsposed.hiddenapibypass.HiddenApiBypass

class StatesideApp : Application() {

    lateinit var prefs: UserPrefsRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 解除 Android 14 ~ 17 对 CarrierConfigManager.overrideConfig、ILocaleManager 与系统原生 Conscrypt.exportKeyingMaterial 的隐藏 API 限制
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = UserPrefsRepository(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val pairingChannel = NotificationChannel(
            CHANNEL_PAIRING,
            getString(R.string.notif_channel_pairing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "首次无线调试激活时，支持在系统设置页下拉通知栏直接输入 6 位配对码"
            enableVibration(true)
        }
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            getString(R.string.notif_channel_status),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "展示开机自动恢复美境环境及安全启动提示"
        }
        val companionTzChannel = NotificationChannel(
            CHANNEL_COMPANION_TZ,
            "动态伴随时区守护",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在指定应用前台运行期间静默显示美区伴随时区状态与一键归位按钮"
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannels(listOf(pairingChannel, statusChannel, companionTzChannel))
    }

    companion object {
        const val CHANNEL_PAIRING = "stateside_wireless_pairing"
        const val CHANNEL_STATUS = "stateside_env_status"
        const val CHANNEL_COMPANION_TZ = "stateside_companion_timezone"

        @Volatile
        lateinit var instance: StatesideApp
            private set
    }
}
