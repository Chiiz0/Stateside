package app.stateside.env

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.stateside.env.data.UserPrefsRepository
import org.conscrypt.Conscrypt
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.security.Security

class StatesideApp : Application() {

    lateinit var prefs: UserPrefsRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 解除 Android 16 / 17 对 CarrierConfigManager.overrideConfig 与 ILocaleManager 的隐藏 API 限制
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        // 注入 Conscrypt 提供程序以保障本机无线调试 TLS 1.3 + SPAKE2 配对握手稳定
        runCatching {
            if (Security.getProvider("Conscrypt") == null) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
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
        nm.createNotificationChannels(listOf(pairingChannel, statusChannel))
    }

    companion object {
        const val CHANNEL_PAIRING = "stateside_wireless_pairing"
        const val CHANNEL_STATUS = "stateside_env_status"

        @Volatile
        lateinit var instance: StatesideApp
            private set
    }
}
