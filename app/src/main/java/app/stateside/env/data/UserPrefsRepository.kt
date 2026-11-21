package app.stateside.env.data

import android.content.Context
import android.provider.Settings
import app.stateside.env.model.AppThemeMode
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.StatesideDefaults
import app.stateside.env.model.TimezoneMode
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.model.UsTimezonePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val isAdbPaired: Boolean = false,
    val lastConnectPort: Int = 0,
    val manualConnectPort: Int = 0,
    val selectedCarrier: UsCarrierPreset = UsCarrierPreset.US_KEEP_CARRIER,
    val customCarrierLabel: String = "X",
    val slot0DisplayOverride: String = "",
    val slot1DisplayOverride: String = "",
    val timezoneMode: TimezoneMode = TimezoneMode.KEEP_NATIVE,
    val selectedUsTimezone: UsTimezonePreset = UsTimezonePreset.LOS_ANGELES,
    val companionTimezonePackages: Set<String> = CoreSuiteApp.TIKTOK.packageNames.toSet(),
    val savedHomeTimezoneId: String = "Asia/Shanghai",
    val savedHomeAutoTimezone: Boolean = true,
    val isTimezoneOverridden: Boolean = false,
    val autoRestoreOnBoot: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val isStatesideDesired: Boolean = false,
    val appliedBootCount: Int = -1,
    val disabledSuiteIds: Set<String> = emptySet(),
    val extraEnglishPackages: Set<String> = emptySet(),
    val confirmedEnglishPackages: Set<String> = emptySet(),
    val lastAppliedTimestamp: Long = 0L
) {
    fun isSuiteAppEnabled(app: CoreSuiteApp): Boolean = app.id !in disabledSuiteIds

    fun effectiveEnglishPackages(): List<String> =
        StatesideDefaults.buildTargetEnglishPackages(disabledSuiteIds, extraEnglishPackages)

    fun isCompanionEnabledForPackage(packageName: String): Boolean =
        packageName in companionTimezonePackages

    fun isCompanionEnabledForSuite(suite: CoreSuiteApp): Boolean =
        suite.packageNames.any { it in companionTimezonePackages }

    fun shouldSwitchCompanionTimezoneOnLaunch(packageName: String): Boolean =
        timezoneMode == TimezoneMode.COMPANION_APP && packageName in companionTimezonePackages

    fun slotOverrideFor(slotIndex: Int): String = when (slotIndex) {
        0 -> slot0DisplayOverride
        1 -> slot1DisplayOverride
        else -> ""
    }

    fun resolveTargetCarrierLabelForSlot(slotIndex: Int): String? {
        val perSlot = slotOverrideFor(slotIndex).trim()
        if (perSlot == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN) return null
        if (perSlot.isNotEmpty()) return perSlot

        return when (selectedCarrier) {
            UsCarrierPreset.US_KEEP_CARRIER -> null
            UsCarrierPreset.CUSTOM -> customCarrierLabel.trim().ifEmpty { "X" }
            else -> selectedCarrier.carrierNameOverride.takeIf { it.isNotBlank() }
        }
    }

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

class UserPrefsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dpContext = appContext.createDeviceProtectedStorageContext()
    private val prefs = dpContext.getSharedPreferences("stateside_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun load(): UserSettings {
        val carrierId = prefs.getString(KEY_CARRIER, UsCarrierPreset.US_KEEP_CARRIER.id)
        val customLabel = prefs.getString(KEY_CUSTOM_CARRIER_LABEL, "X")?.trim().orEmpty().ifEmpty { "X" }
        val slot0Override = prefs.getString(KEY_SLOT0_OVERRIDE, "").orEmpty()
        val slot1Override = prefs.getString(KEY_SLOT1_OVERRIDE, "").orEmpty()
        val tzModeId = prefs.getString(KEY_TZ_MODE, TimezoneMode.KEEP_NATIVE.id)
        val tzPresetId = prefs.getString(KEY_TZ_PRESET, UsTimezonePreset.LOS_ANGELES.id)
        val companionSet = prefs.getStringSet(KEY_COMPANION_TZ_PKGS, null)?.toSet()
            ?: CoreSuiteApp.TIKTOK.packageNames.toSet()
        val homeTzId = prefs.getString(KEY_HOME_TZ_ID, "Asia/Shanghai").orEmpty().ifBlank { "Asia/Shanghai" }
        val homeAutoTz = prefs.getBoolean(KEY_HOME_AUTO_TZ, true)
        val tzOverridden = prefs.getBoolean(KEY_TZ_OVERRIDDEN, false)

        val disabledSet = prefs.getStringSet(KEY_DISABLED_SUITE_IDS, emptySet())?.toSet() ?: emptySet()
        val extraSet = prefs.getStringSet(KEY_EXTRA_ENGLISH_PKGS, emptySet())?.toSet() ?: emptySet()
        val confirmedSet = prefs.getStringSet(KEY_CONFIRMED_ENGLISH_PKGS, emptySet())?.toSet() ?: emptySet()
        val lastAppliedTs = prefs.getLong(KEY_LAST_APPLIED_TS, 0L)
        val themeId = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.id)

        return UserSettings(
            isAdbPaired = prefs.getBoolean(KEY_PAIRED, false),
            lastConnectPort = prefs.getInt(KEY_LAST_PORT, 0),
            manualConnectPort = prefs.getInt(KEY_MANUAL_PORT, 0),
            selectedCarrier = UsCarrierPreset.fromId(carrierId),
            customCarrierLabel = customLabel,
            slot0DisplayOverride = slot0Override,
            slot1DisplayOverride = slot1Override,
            timezoneMode = TimezoneMode.fromId(tzModeId),
            selectedUsTimezone = UsTimezonePreset.fromId(tzPresetId),
            companionTimezonePackages = companionSet,
            savedHomeTimezoneId = homeTzId,
            savedHomeAutoTimezone = homeAutoTz,
            isTimezoneOverridden = tzOverridden,
            autoRestoreOnBoot = prefs.getBoolean(KEY_AUTO_BOOT, true),
            themeMode = AppThemeMode.fromId(themeId),
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
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

    fun setCustomCarrierLabel(label: String) {
        val sanitized = label.trim().take(24).ifEmpty { "X" }
        prefs.edit()
            .putString(KEY_CUSTOM_CARRIER_LABEL, sanitized)
            .putString(KEY_CARRIER, UsCarrierPreset.CUSTOM.id)
            .apply()
        _settings.value = load()
    }

    fun setSlotDisplayOverride(slotIndex: Int, rawOverride: String) {
        val key = when (slotIndex) {
            0 -> KEY_SLOT0_OVERRIDE
            1 -> KEY_SLOT1_OVERRIDE
            else -> return
        }
        val clean = if (rawOverride == SLOT_KEEP_NATIVE_TOKEN) {
            SLOT_KEEP_NATIVE_TOKEN
        } else {
            rawOverride.trim().take(24)
        }
        prefs.edit().putString(key, clean).apply()
        _settings.value = load()
    }

    fun setTimezoneMode(mode: TimezoneMode) {
        prefs.edit().putString(KEY_TZ_MODE, mode.id).apply()
        _settings.value = load()
    }

    fun setSelectedUsTimezone(preset: UsTimezonePreset) {
        prefs.edit().putString(KEY_TZ_PRESET, preset.id).apply()
        _settings.value = load()
    }

    fun toggleCompanionTimezoneSuite(suite: CoreSuiteApp) {
        val current = _settings.value.companionTimezonePackages.toMutableSet()
        val anyEnabled = suite.packageNames.any { it in current }
        if (anyEnabled) {
            current.removeAll(suite.packageNames.toSet())
        } else {
            current.addAll(suite.packageNames)
        }
        prefs.edit().putStringSet(KEY_COMPANION_TZ_PKGS, current).apply()
        _settings.value = load()
    }

    fun toggleCompanionTimezonePackage(packageName: String) {
        val current = _settings.value.companionTimezonePackages.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_COMPANION_TZ_PKGS, current).apply()
        _settings.value = load()
    }

    fun captureHomeTimezoneBaselineIfNeeded(currentZoneId: String, currentAutoTz: Boolean) {
        val clean = currentZoneId.trim()
        if (clean.isNotEmpty() && !UsTimezonePreset.isManagedUsZone(clean)) {
            prefs.edit()
                .putString(KEY_HOME_TZ_ID, clean)
                .putBoolean(KEY_HOME_AUTO_TZ, currentAutoTz)
                .apply()
            _settings.value = load()
        }
    }

    fun markTimezoneOverridden(overridden: Boolean) {
        prefs.edit().putBoolean(KEY_TZ_OVERRIDDEN, overridden).apply()
        _settings.value = load()
    }

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
        _settings.value = load()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _settings.value = load()
    }

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = load()
    }

    fun resetAdbPairingRecord() {
        prefs.edit()
            .putBoolean(KEY_PAIRED, false)
            .putInt(KEY_LAST_PORT, 0)
            .putInt(KEY_MANUAL_PORT, 0)
            .apply()
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
            .putBoolean(KEY_TZ_OVERRIDDEN, false)
            .putStringSet(KEY_CONFIRMED_ENGLISH_PKGS, emptySet())
            .apply()
        _settings.value = load()
    }

    companion object {
        const val SLOT_KEEP_NATIVE_TOKEN = "__KEEP_NATIVE__"

        private const val KEY_PAIRED = "adb_paired"
        private const val KEY_LAST_PORT = "last_connect_port"
        private const val KEY_MANUAL_PORT = "manual_connect_port"
        private const val KEY_CARRIER = "selected_carrier_preset_v2"
        private const val KEY_CUSTOM_CARRIER_LABEL = "custom_carrier_label"
        private const val KEY_SLOT0_OVERRIDE = "slot0_display_override"
        private const val KEY_SLOT1_OVERRIDE = "slot1_display_override"
        private const val KEY_TZ_MODE = "timezone_mode"
        private const val KEY_TZ_PRESET = "selected_us_timezone"
        private const val KEY_COMPANION_TZ_PKGS = "companion_tz_packages"
        private const val KEY_HOME_TZ_ID = "saved_home_timezone_id"
        private const val KEY_HOME_AUTO_TZ = "saved_home_auto_tz"
        private const val KEY_TZ_OVERRIDDEN = "is_tz_overridden"
        private const val KEY_AUTO_BOOT = "auto_restore_on_boot"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
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
