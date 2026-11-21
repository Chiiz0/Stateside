package app.stateside.env.data

import android.content.Context
import android.provider.Settings
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.StatesideDefaults
import app.stateside.env.model.UsCarrierPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val isAdbPaired: Boolean = false,
    val lastConnectPort: Int = 0,
    val manualConnectPort: Int = 0,
    val selectedCarrier: UsCarrierPreset = UsCarrierPreset.TMOBILE,
    val autoRestoreOnBoot: Boolean = true,
    // 记录用户当前期望处于美境（开启美境时置 true，主动执行「恢复国内默认」时置 false）
    val isStatesideDesired: Boolean = false,
    // 记录最近一次成功执行美境覆盖时的系统 BOOT_COUNT，避免未授电话权限时误判已生效
    val appliedBootCount: Int = -1,
    // 默认为空集合，代表 TikTok、X、Claude、ChatGPT 四大预设应用开箱全部自动勾选启用
    val disabledSuiteIds: Set<String> = emptySet(),
    val extraEnglishPackages: Set<String> = emptySet(),
    val confirmedEnglishPackages: Set<String> = emptySet(),
    val lastAppliedTimestamp: Long = 0L
) {
    fun isSuiteAppEnabled(app: CoreSuiteApp): Boolean = app.id !in disabledSuiteIds

    fun effectiveEnglishPackages(): List<String> =
        StatesideDefaults.buildTargetEnglishPackages(disabledSuiteIds, extraEnglishPackages)

    /**
     * 严谨判断当前真机是否已处于美境就绪状态：
     * 1. 若已获电话状态权限且手机插有实体卡/eSIM：必须所有激活卡槽均对外上报为 US；
     * 2. 若已获电话状态权限且确实无卡（Wi-Fi 平板/备用机）：需用户曾成功执行过美境驱动；
     * 3. 若尚未授予电话状态权限：仅当本次开机周期（BOOT_COUNT 匹配）内特权组件已核验写入成功时才视为就绪，彻底杜绝无权限时误判放行！
     */
    fun isReadyForCurrentSession(
        hasPrivilegeChannel: Boolean,
        hasPhonePermission: Boolean,
        simCardsCount: Int,
        allSimsUsSpoofed: Boolean,
        currentBootCount: Int
    ): Boolean {
        if (!hasPrivilegeChannel) return false
        return if (hasPhonePermission) {
            if (simCardsCount > 0) {
                allSimsUsSpoofed
            } else {
                isStatesideDesired && lastAppliedTimestamp > 0L
            }
        } else {
            isStatesideDesired && currentBootCount >= 0 && appliedBootCount == currentBootCount
        }
    }
}

/**
 * 使用 Device-Protected Storage 存储配置，确保手机重启后尚未解锁屏幕（LOCKED_BOOT_COMPLETED）时，
 * 后台自愈组件也能正常读取用户偏好与上次无线调试端口。
 */
class UserPrefsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dpContext = appContext.createDeviceProtectedStorageContext()
    private val prefs = dpContext.getSharedPreferences("stateside_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun load(): UserSettings {
        val carrierId = prefs.getString(KEY_CARRIER, UsCarrierPreset.TMOBILE.id)
        val disabledSet = prefs.getStringSet(KEY_DISABLED_SUITE_IDS, emptySet())?.toSet() ?: emptySet()
        val extraSet = prefs.getStringSet(KEY_EXTRA_ENGLISH_PKGS, emptySet())?.toSet() ?: emptySet()
        val confirmedSet = prefs.getStringSet(KEY_CONFIRMED_ENGLISH_PKGS, emptySet())?.toSet() ?: emptySet()
        val lastAppliedTs = prefs.getLong(KEY_LAST_APPLIED_TS, 0L)
        return UserSettings(
            isAdbPaired = prefs.getBoolean(KEY_PAIRED, false),
            lastConnectPort = prefs.getInt(KEY_LAST_PORT, 0),
            manualConnectPort = prefs.getInt(KEY_MANUAL_PORT, 0),
            selectedCarrier = UsCarrierPreset.fromId(carrierId),
            autoRestoreOnBoot = prefs.getBoolean(KEY_AUTO_BOOT, true),
            isStatesideDesired = prefs.getBoolean(KEY_STATESIDE_DESIRED, lastAppliedTs > 0L),
            appliedBootCount = prefs.getInt(KEY_APPLIED_BOOT_COUNT, -1),
            disabledSuiteIds = disabledSet,
            extraEnglishPackages = extraSet,
            confirmedEnglishPackages = confirmedSet,
            lastAppliedTimestamp = lastAppliedTs
        )
    }

    fun reload() {
        _settings.value = load()
    }

    fun markAdbPaired(paired: Boolean, connectPort: Int = _settings.value.lastConnectPort) {
        prefs.edit()
            .putBoolean(KEY_PAIRED, paired)
            .apply {
                if (connectPort in 1..65535) putInt(KEY_LAST_PORT, connectPort)
            }
            .apply()
        _settings.value = load()
    }

    fun setManualConnectPort(port: Int) {
        prefs.edit().putInt(KEY_MANUAL_PORT, port.coerceIn(0, 65535)).apply()
        _settings.value = load()
    }

    fun setSelectedCarrier(preset: UsCarrierPreset) {
        prefs.edit().putString(KEY_CARRIER, preset.id).apply()
        _settings.value = load()
    }

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
        _settings.value = load()
    }

    fun toggleCoreSuiteApp(suiteApp: CoreSuiteApp) {
        val disabled = _settings.value.disabledSuiteIds.toMutableSet()
        if (!disabled.add(suiteApp.id)) {
            disabled.remove(suiteApp.id)
        }
        prefs.edit().putStringSet(KEY_DISABLED_SUITE_IDS, disabled).apply()
        _settings.value = load()
    }

    fun toggleExtraEnglishPackage(packageName: String) {
        val current = _settings.value.extraEnglishPackages.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_EXTRA_ENGLISH_PKGS, current).apply()
        _settings.value = load()
    }

    fun recordAppliedSuccess(updatedEnglishPackages: Collection<String> = emptyList()) {
        val bootCount = readCurrentBootCount(appContext)
        val mergedConfirmed = (_settings.value.confirmedEnglishPackages + updatedEnglishPackages).toSet()
        prefs.edit()
            .putBoolean(KEY_STATESIDE_DESIRED, true)
            .putInt(KEY_APPLIED_BOOT_COUNT, bootCount)
            .putLong(KEY_LAST_APPLIED_TS, System.currentTimeMillis())
            .putStringSet(KEY_CONFIRMED_ENGLISH_PKGS, mergedConfirmed)
            .apply()
        _settings.value = load()
    }

    fun recordRestoredDefault() {
        prefs.edit()
            .putBoolean(KEY_STATESIDE_DESIRED, false)
            .putInt(KEY_APPLIED_BOOT_COUNT, -1)
            .putStringSet(KEY_CONFIRMED_ENGLISH_PKGS, emptySet())
            .apply()
        _settings.value = load()
    }

    companion object {
        private const val KEY_PAIRED = "adb_paired"
        private const val KEY_LAST_PORT = "last_connect_port"
        private const val KEY_MANUAL_PORT = "manual_connect_port"
        private const val KEY_CARRIER = "selected_carrier_id"
        private const val KEY_AUTO_BOOT = "auto_restore_on_boot"
        private const val KEY_STATESIDE_DESIRED = "stateside_desired_active"
        private const val KEY_APPLIED_BOOT_COUNT = "applied_boot_count"
        private const val KEY_DISABLED_SUITE_IDS = "disabled_suite_ids"
        private const val KEY_EXTRA_ENGLISH_PKGS = "extra_english_pkgs"
        private const val KEY_CONFIRMED_ENGLISH_PKGS = "confirmed_english_pkgs"
        private const val KEY_LAST_APPLIED_TS = "last_applied_ts"

        fun readCurrentBootCount(context: Context): Int =
            runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            }.getOrDefault(-1)
    }
}
