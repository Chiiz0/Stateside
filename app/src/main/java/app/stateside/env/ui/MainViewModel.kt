package app.stateside.env.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stateside.env.BuildConfig
import app.stateside.env.StatesideApp
import app.stateside.env.adb.AdbPairingService
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.adb.PairingUiPhase
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.data.UserSettings
import app.stateside.env.model.AppThemeMode
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.MccIsoResolver
import app.stateside.env.model.TimezoneMode
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.model.UsTimezonePreset
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.oem.RomSetupGuide
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.privileged.TimezoneOverrideEngine
import app.stateside.env.shortcut.AppLaunchGuardHelper
import app.stateside.env.timezone.CompanionTzSessionState
import app.stateside.env.timezone.CompanionTimezoneService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SimCardUiModel(
    val slotIndex: Int,
    val slotLabel: String,
    val rawMccMnc: String,
    val realCountryLabel: String,
    val currentReportedIso: String,
    val networkCountryIso: String,
    val currentReportedCarrier: String,
    val isUsSpoofActive: Boolean
)

data class EnvironmentTelemetry(
    val deviceModel: String = "${Build.BRAND.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
    val androidRelease: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val systemLocaleTag: String = "zh-CN",
    val systemTimezoneId: String = "Asia/Shanghai",
    val systemTimezoneOffset: String = "UTC+08:00",
    val isDeveloperOptionsEnabled: Boolean = false
) {
    val isUsTimezone: Boolean
        get() = systemTimezoneId.startsWith("America/", ignoreCase = true) ||
            systemTimezoneId.startsWith("US/", ignoreCase = true) ||
            systemTimezoneId.equals("Pacific/Honolulu", ignoreCase = true)
}

data class CoreSuiteUiItem(
    val suiteApp: CoreSuiteApp,
    val installedPackage: String?,
    val isEnabled: Boolean,
    val isEnglishConfirmed: Boolean,
    val isCompanionTzEnabled: Boolean
)

data class ExtraEnglishAppUiItem(
    val packageName: String,
    val appLabel: String,
    val isInstalled: Boolean,
    val isEnglishConfirmed: Boolean,
    val isCompanionTzEnabled: Boolean
)

data class SelectableAppItem(
    val packageName: String,
    val appLabel: String
)

data class DockAppUiItem(
    val packageName: String,
    val title: String,
    val isEnglishConfirmed: Boolean,
    val usesCompanionTz: Boolean
)

data class MainUiState(
    val hasPhonePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasLocalNetworkPermission: Boolean = true,
    val hasRootAccess: Boolean = false,
    val currentBootCount: Int = -1,
    val simCards: List<SimCardUiModel> = emptyList(),
    val telemetry: EnvironmentTelemetry = EnvironmentTelemetry(),
    val coreSuiteItems: List<CoreSuiteUiItem> = emptyList(),
    val extraAppItems: List<ExtraEnglishAppUiItem> = emptyList(),
    val userSettings: UserSettings = UserSettings(),
    val romGuide: RomSetupGuide = ChineseRomGuide.detectCurrentRom(),
    val pairingPhase: PairingUiPhase = PairingUiPhase.Idle,
    val companionSession: CompanionTzSessionState = CompanionTzSessionState.Idle,
    val selectableApps: List<SelectableAppItem> = emptyList(),
    val isWorking: Boolean = false,
    val workingMessage: String = "",
    val toastBanner: String? = null,
    val errorDetail: String? = null
) {
    val hasAllRecommendedPermissions: Boolean
        get() = hasPhonePermission && hasNotificationPermission && hasLocalNetworkPermission

    val grantedPermissionCount: Int
        get() = listOf(hasPhonePermission, hasNotificationPermission, hasLocalNetworkPermission).count { it }

    val hasPrivilegeChannel: Boolean
        get() = hasRootAccess || userSettings.isAdbPaired

    val privilegeChannelTitle: String
        get() = when {
            hasRootAccess -> "Root 直通通道"
            userSettings.isAdbPaired -> "本机无线调试"
            else -> "尚未绑定特权通道"
        }

    val areSimsAlreadyReportingUs: Boolean
        get() = hasPhonePermission && simCards.isNotEmpty() && simCards.all { it.isUsSpoofActive }

    val isEnvironmentReady: Boolean
        get() = areSimsAlreadyReportingUs || userSettings.isReadyForCurrentSession(
            hasPrivilegeChannel = hasPrivilegeChannel,
            hasPhonePermission = hasPhonePermission,
            simCardsCount = simCards.size,
            allSimsUsSpoofed = areSimsAlreadyReportingUs,
            currentBootCount = currentBootCount
        )

    val confirmedEnglishAppsCount: Int
        get() {
            val coreLocked = coreSuiteItems.count {
                it.installedPackage != null && it.isEnabled && it.isEnglishConfirmed
            }
            val extraLocked = extraAppItems.count { it.isInstalled && it.isEnglishConfirmed }
            return coreLocked + extraLocked
        }

    val installedTargetEnglishAppsCount: Int
        get() {
            val coreInstalled = coreSuiteItems.count { it.installedPackage != null && it.isEnabled }
            val extraInstalled = extraAppItems.count { it.isInstalled }
            return coreInstalled + extraInstalled
        }

    val unconfirmedEnglishAppsCount: Int
        get() = (installedTargetEnglishAppsCount - confirmedEnglishAppsCount).coerceAtLeast(0)

    val enabledCompanionAppsCount: Int
        get() {
            val coreComp = coreSuiteItems.count { it.isCompanionTzEnabled }
            val extraComp = extraAppItems.count { it.isCompanionTzEnabled }
            return coreComp + extraComp
        }

    val lastAppliedFormatted: String
        get() {
            val ts = userSettings.lastAppliedTimestamp
            if (ts <= 0L) return "尚未记录"
            return runCatching {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
            }.getOrDefault("已同步")
        }

    val safeLaunchDockItems: List<DockAppUiItem>
        get() {
            val coreDock = coreSuiteItems
                .filter { it.installedPackage != null && it.isEnabled }
                .map {
                    DockAppUiItem(
                        packageName = it.installedPackage!!,
                        title = it.suiteApp.title,
                        isEnglishConfirmed = it.isEnglishConfirmed,
                        usesCompanionTz = userSettings.shouldSwitchCompanionTimezoneOnLaunch(it.installedPackage)
                    )
                }
            val extraDock = extraAppItems
                .filter { it.isInstalled }
                .map {
                    DockAppUiItem(
                        packageName = it.packageName,
                        title = it.appLabel,
                        isEnglishConfirmed = it.isEnglishConfirmed,
                        usesCompanionTz = userSettings.shouldSwitchCompanionTimezoneOnLaunch(it.packageName)
                    )
                }
            return (coreDock + extraDock).distinctBy { it.packageName }
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StatesideApp
    private val prefs = app.prefs

    private data class InternalState(
        val hasPhonePerm: Boolean = false,
        val hasNotifPerm: Boolean = false,
        val hasLocalNetPerm: Boolean = true,
        val hasRoot: Boolean = false,
        val bootCount: Int = -1,
        val simCards: List<SimCardUiModel> = emptyList(),
        val telemetry: EnvironmentTelemetry = EnvironmentTelemetry(),
        val installedSuiteMap: Map<CoreSuiteApp, String?> = emptyMap(),
        val extraLabelsMap: Map<String, Pair<String, Boolean>> = emptyMap(),
        val selectableApps: List<SelectableAppItem> = emptyList(),
        val isWorking: Boolean = false,
        val workingMessage: String = "",
        val toastBanner: String? = null,
        val errorDetail: String? = null
    )

    private val internal = MutableStateFlow(InternalState())
    private val romGuide = ChineseRomGuide.detectCurrentRom()

    val uiState: StateFlow<MainUiState> = combine(
        internal,
        prefs.settings,
        AdbPairingService.phase,
        CompanionTimezoneService.sessionState
    ) { local, settings, phase, compSession ->
        val suiteItems = CoreSuiteApp.entries.map { suite ->
            val pkg = local.installedSuiteMap[suite]
            CoreSuiteUiItem(
                suiteApp = suite,
                installedPackage = pkg,
                isEnabled = settings.isSuiteAppEnabled(suite),
                isEnglishConfirmed = pkg != null && pkg in settings.confirmedEnglishPackages,
                isCompanionTzEnabled = settings.isCompanionEnabledForSuite(suite)
            )
        }
        val extraItems = settings.extraEnglishPackages.sorted().map { pkg ->
            val meta = local.extraLabelsMap[pkg]
            ExtraEnglishAppUiItem(
                packageName = pkg,
                appLabel = meta?.first ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                isInstalled = meta?.second ?: false,
                isEnglishConfirmed = pkg in settings.confirmedEnglishPackages,
                isCompanionTzEnabled = settings.isCompanionEnabledForPackage(pkg)
            )
        }
        MainUiState(
            hasPhonePermission = local.hasPhonePerm,
            hasNotificationPermission = local.hasNotifPerm,
            hasLocalNetworkPermission = local.hasLocalNetPerm,
            hasRootAccess = local.hasRoot,
            currentBootCount = local.bootCount,
            simCards = local.simCards,
            telemetry = local.telemetry,
            coreSuiteItems = suiteItems,
            extraAppItems = extraItems,
            userSettings = settings,
            romGuide = romGuide,
            pairingPhase = phase,
            companionSession = compSession,
            selectableApps = local.selectableApps,
            isWorking = local.isWorking,
            workingMessage = local.workingMessage,
            toastBanner = local.toastBanner,
            errorDetail = local.errorDetail
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(romGuide = romGuide)
    )

    init {
        refreshSystemState()
    }

    @SuppressLint("MissingPermission")
    fun refreshSystemState() {
        viewModelScope.launch {
            prefs.reload()

            val hasPhone = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val hasNotif = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            val hasLocalNet = if (Build.VERSION.SDK_INT >= 37) {
                ContextCompat.checkSelfPermission(
                    app,
                    "android.permission.ACCESS_LOCAL_NETWORK"
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val bootCount = UserPrefsRepository.readCurrentBootCount(app)
            val hasRoot = LocalAdbManager.getInstance(app).detectRootAccess()
            val telemetrySnapshot = queryEnvironmentTelemetry()

            val sims = if (hasPhone) {
                withContext(Dispatchers.IO) { querySimCards() }
            } else {
                emptyList()
            }

            val (suiteMap, extraMap) = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val suites = CoreSuiteApp.entries.associateWith { suite ->
                    AppLaunchGuardHelper.resolveInstalledPackage(app, suite)
                }
                val extras = prefs.settings.value.extraEnglishPackages.associateWith { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                        pm.getApplicationLabel(info).toString() to true
                    }.getOrElse { pkg to false }
                }
                suites to extras
            }

            internal.update {
                it.copy(
                    hasPhonePerm = hasPhone,
                    hasNotifPerm = hasNotif,
                    hasLocalNetPerm = hasLocalNet,
                    hasRoot = hasRoot,
                    bootCount = bootCount,
                    simCards = sims,
                    telemetry = telemetrySnapshot,
                    installedSuiteMap = suiteMap,
                    extraLabelsMap = extraMap
                )
            }
        }
    }

    private fun queryEnvironmentTelemetry(): EnvironmentTelemetry {
        val liveZoneId = TimezoneOverrideEngine.readLiveSystemTimezone()
        val tz = TimeZone.getTimeZone(liveZoneId)
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val totalMinutes = abs(offsetMillis) / 60_000
        val sign = if (offsetMillis >= 0) "+" else "-"
        val offsetText = String.format(Locale.US, "UTC%s%02d:%02d", sign, totalMinutes / 60, totalMinutes % 60)
        val sysLocale = LocaleList.getDefault().let { list ->
            if (list.isEmpty) "zh-CN" else list[0].toLanguageTag()
        }
        return EnvironmentTelemetry(
            deviceModel = "${Build.BRAND.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidRelease = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            systemLocaleTag = sysLocale,
            systemTimezoneId = liveZoneId,
            systemTimezoneOffset = offsetText,
            isDeveloperOptionsEnabled = ChineseRomGuide.isDeveloperOptionsEnabled(app)
        )
    }

    fun loadSelectableUserAppsIfNeeded() {
        if (internal.value.selectableApps.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                .asSequence()
                .filter { info ->
                    val isUserApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    isUserApp &&
                        info.packageName != app.packageName &&
                        info.packageName !in CoreSuiteApp.ALL_BUILTIN_PACKAGES &&
                        pm.getLaunchIntentForPackage(info.packageName) != null
                }
                .map { info ->
                    SelectableAppItem(
                        packageName = info.packageName,
                        appLabel = pm.getApplicationLabel(info).toString()
                    )
                }
                .sortedBy { it.appLabel.lowercase() }
                .toList()

            internal.update { it.copy(selectableApps = apps) }
        }
    }

    fun applyStatesideEnv(
        packageToLaunchAfter: String? = null,
        appTitleForCompanion: String? = null
    ) {
        if (internal.value.isWorking) return
        viewModelScope.launch {
            val settings = prefs.settings.value
            val needsCompanionTz = !packageToLaunchAfter.isNullOrBlank() &&
                settings.shouldSwitchCompanionTimezoneOnLaunch(packageToLaunchAfter)

            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在同步美境配置（双卡美区 + 应用原生英语引擎）…",
                    errorDetail = null,
                    toastBanner = null
                )
            }

            val result = LocalAdbManager.getInstance(app).runEnvironmentTask(
                action = PrivilegedAction.APPLY_US_ENV,
                explicitTimezoneId = if (needsCompanionTz) settings.selectedUsTimezone.zoneId else null
            )

            refreshSystemState()

            if (result.isSuccess) {
                val summary = result.getOrThrow().summaryText
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        toastBanner = summary
                    )
                }
                if (!packageToLaunchAfter.isNullOrBlank()) {
                    if (needsCompanionTz) {
                        CompanionTimezoneService.startSession(
                            context = app,
                            targetPackage = packageToLaunchAfter,
                            appTitle = appTitleForCompanion ?: resolveAppDisplayTitle(packageToLaunchAfter),
                            zoneLabel = settings.selectedUsTimezone.cityLabel
                        )
                    }
                    AppLaunchGuardHelper.launchPackageDirectly(app, packageToLaunchAfter)
                }
            } else {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorDetail = result.exceptionOrNull()?.message ?: "开启未完成，请检查无线调试状态"
                    )
                }
            }
        }
    }

    fun connectManualPortAndApply(manualPortInput: String, onSuccessCloseSheet: () -> Unit) {
        val parsedPort = manualPortInput.trim().filter { it.isDigit() }.toIntOrNull() ?: 0
        if (parsedPort in 1024..65535) {
            prefs.setManualConnectPort(parsedPort)
        } else if (manualPortInput.isBlank()) {
            prefs.setManualConnectPort(0)
        }
        if (internal.value.isWorking) return
        viewModelScope.launch {
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在连接本机无线调试并开启美境…",
                    errorDetail = null,
                    toastBanner = null
                )
            }
            val result = LocalAdbManager.getInstance(app)
                .runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)
            refreshSystemState()

            if (result.isSuccess) {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        toastBanner = result.getOrThrow().summaryText
                    )
                }
                onSuccessCloseSheet()
            } else {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorDetail = result.exceptionOrNull()?.message ?: "无线调试连接失败"
                    )
                }
            }
        }
    }

    fun safeLaunchTargetApp(packageName: String) {
        val state = uiState.value
        val settings = state.userSettings
        val appTitle = resolveAppDisplayTitle(packageName)
        val needsEnglishInit = packageName in settings.effectiveEnglishPackages() &&
            packageName !in settings.confirmedEnglishPackages
        val needsCompanionTz = settings.shouldSwitchCompanionTimezoneOnLaunch(packageName)

        if (state.isEnvironmentReady && !needsEnglishInit) {
            if (!needsCompanionTz) {
                AppLaunchGuardHelper.launchPackageDirectly(app, packageName)
                return
            }
            val liveTz = TimezoneOverrideEngine.readLiveSystemTimezone()
            if (liveTz.equals(settings.selectedUsTimezone.zoneId, ignoreCase = true)) {
                CompanionTimezoneService.startSession(
                    context = app,
                    targetPackage = packageName,
                    appTitle = appTitle,
                    zoneLabel = settings.selectedUsTimezone.cityLabel
                )
                AppLaunchGuardHelper.launchPackageDirectly(app, packageName)
                return
            }
            // 美境双卡与语言已就绪，仅需在启动前快速切入美区伴随时区
            if (internal.value.isWorking) return
            viewModelScope.launch {
                internal.update {
                    it.copy(
                        isWorking = true,
                        workingMessage = "正在为 $appTitle 切入美区伴随时区 (${settings.selectedUsTimezone.cityLabel})…",
                        errorDetail = null,
                        toastBanner = null
                    )
                }
                val result = LocalAdbManager.getInstance(app).runEnvironmentTask(
                    action = PrivilegedAction.SWITCH_US_TIMEZONE,
                    explicitTimezoneId = settings.selectedUsTimezone.zoneId
                )
                refreshSystemState()
                if (result.isSuccess) {
                    internal.update { it.copy(isWorking = false, workingMessage = "") }
                    CompanionTimezoneService.startSession(
                        context = app,
                        targetPackage = packageName,
                        appTitle = appTitle,
                        zoneLabel = settings.selectedUsTimezone.cityLabel
                    )
                    AppLaunchGuardHelper.launchPackageDirectly(app, packageName)
                } else {
                    internal.update {
                        it.copy(
                            isWorking = false,
                            workingMessage = "",
                            errorDetail = result.exceptionOrNull()?.message ?: "伴随时区切换失败，请确认无线调试已开启"
                        )
                    }
                }
            }
        } else {
            applyStatesideEnv(
                packageToLaunchAfter = packageName,
                appTitleForCompanion = appTitle
            )
        }
    }

    private fun resolveAppDisplayTitle(packageName: String): String {
        val s = uiState.value
        s.coreSuiteItems.firstOrNull { it.installedPackage == packageName }?.let { return it.suiteApp.title }
        s.extraAppItems.firstOrNull { it.packageName == packageName }?.let { return it.appLabel }
        return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    fun restoreToDomesticDefault() {
        if (internal.value.isWorking) return
        viewModelScope.launch {
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在执行无损还原，恢复真实归属地、原机时区与跟随系统语言…",
                    errorDetail = null,
                    toastBanner = null
                )
            }

            val result = LocalAdbManager.getInstance(app)
                .runEnvironmentTask(PrivilegedAction.RESTORE_CN_DEFAULT)

            refreshSystemState()

            if (result.isSuccess) {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        toastBanner = result.getOrThrow().summaryText
                    )
                }
            } else {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorDetail = result.exceptionOrNull()?.message ?: "还原未完成，请确认无线调试已开启"
                    )
                }
            }
        }
    }

    fun selectTimezoneMode(mode: TimezoneMode) {
        prefs.setTimezoneMode(mode)
        val state = uiState.value
        when (mode) {
            TimezoneMode.GLOBAL_US -> {
                CompanionTimezoneService.stopQuietly(app)
                if (state.hasPrivilegeChannel && state.isEnvironmentReady && !internal.value.isWorking) {
                    switchUsTimezoneNow()
                } else {
                    internal.update {
                        it.copy(toastBanner = "已设为「全局跟随美境切时区」；开启美境时将同步切至 ${state.userSettings.selectedUsTimezone.cityLabel}")
                    }
                }
            }

            TimezoneMode.KEEP_NATIVE -> {
                CompanionTimezoneService.stopQuietly(app)
                if (state.hasPrivilegeChannel && (state.userSettings.isTimezoneOverridden || state.telemetry.isUsTimezone) && !internal.value.isWorking) {
                    restoreHomeTimezoneNow()
                } else {
                    internal.update { it.copy(toastBanner = "已设为「保持本机原时区」") }
                }
            }

            TimezoneMode.COMPANION_APP -> {
                if (state.hasPrivilegeChannel &&
                    state.userSettings.isTimezoneOverridden &&
                    state.companionSession is CompanionTzSessionState.Idle &&
                    !internal.value.isWorking
                ) {
                    restoreHomeTimezoneNow(customSuccessBanner = "已启用「随开随切」动态伴随模式，平时保持原机时区")
                } else {
                    internal.update {
                        it.copy(toastBanner = "已启用方案二「随开随切 · 退出即还」，从快启坞或桌面守卫图标打开目标应用即自动生效")
                    }
                }
            }
        }
    }

    fun selectUsTimezonePreset(preset: UsTimezonePreset) {
        prefs.setSelectedUsTimezone(preset)
        val state = uiState.value
        val shouldApplyImmediately = state.hasPrivilegeChannel && !internal.value.isWorking && (
            (state.userSettings.timezoneMode == TimezoneMode.GLOBAL_US && state.isEnvironmentReady) ||
                state.companionSession is CompanionTzSessionState.Active
            )
        if (shouldApplyImmediately) {
            switchUsTimezoneNow()
        } else {
            internal.update {
                it.copy(toastBanner = "已选定目标美区时区：${preset.chipLabel}")
            }
        }
    }

    fun switchUsTimezoneNow() {
        if (internal.value.isWorking) return
        viewModelScope.launch {
            val preset = prefs.settings.value.selectedUsTimezone
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在切换系统时区至 ${preset.chipLabel}…",
                    errorDetail = null,
                    toastBanner = null
                )
            }
            val result = LocalAdbManager.getInstance(app).runEnvironmentTask(
                action = PrivilegedAction.SWITCH_US_TIMEZONE,
                explicitTimezoneId = preset.zoneId
            )
            refreshSystemState()
            if (result.isSuccess) {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        toastBanner = result.getOrThrow().summaryText
                    )
                }
            } else {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorDetail = result.exceptionOrNull()?.message ?: "时区切换未成功，请确认无线调试处于开启状态"
                    )
                }
            }
        }
    }

    fun restoreHomeTimezoneNow(customSuccessBanner: String? = null) {
        if (internal.value.isWorking) return
        CompanionTimezoneService.stopQuietly(app)
        viewModelScope.launch {
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在归位本机原时区…",
                    errorDetail = null,
                    toastBanner = null
                )
            }
            val result = LocalAdbManager.getInstance(app)
                .runEnvironmentTask(PrivilegedAction.RESTORE_HOME_TIMEZONE)
            refreshSystemState()
            if (result.isSuccess) {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        toastBanner = customSuccessBanner ?: result.getOrThrow().summaryText
                    )
                }
            } else {
                internal.update {
                    it.copy(
                        isWorking = false,
                        workingMessage = "",
                        errorDetail = result.exceptionOrNull()?.message ?: "时区归位未完成，请确认无线调试处于开启状态"
                    )
                }
            }
        }
    }

    fun toggleCompanionTimezoneForSuite(suite: CoreSuiteApp) {
        val wasKeepNative = prefs.settings.value.timezoneMode == TimezoneMode.KEEP_NATIVE
        prefs.toggleCompanionTimezoneSuite(suite)
        val nowEnabled = prefs.settings.value.isCompanionEnabledForSuite(suite)
        if (nowEnabled && wasKeepNative) {
            prefs.setTimezoneMode(TimezoneMode.COMPANION_APP)
            internal.update {
                it.copy(toastBanner = "已为 ${suite.title} 开启「随开随切」动态伴随时区")
            }
        }
    }

    fun toggleCompanionTimezoneForPackage(packageName: String, appLabel: String) {
        val wasKeepNative = prefs.settings.value.timezoneMode == TimezoneMode.KEEP_NATIVE
        prefs.toggleCompanionTimezonePackage(packageName)
        val nowEnabled = prefs.settings.value.isCompanionEnabledForPackage(packageName)
        if (nowEnabled && wasKeepNative) {
            prefs.setTimezoneMode(TimezoneMode.COMPANION_APP)
            internal.update {
                it.copy(toastBanner = "已为 $appLabel 开启「随开随切」动态伴随时区")
            }
        }
    }

    fun selectCarrier(preset: UsCarrierPreset) {
        prefs.setSelectedCarrier(preset)
        val state = uiState.value
        if (state.hasPrivilegeChannel && !internal.value.isWorking) {
            applyStatesideEnv(null)
        } else if (!state.hasPrivilegeChannel) {
            internal.update {
                it.copy(
                    toastBanner = "已选定「${preset.chipLabel}」；绑定无线调试后即可一键生效"
                )
            }
        }
    }

    fun applyCustomCarrierLabel(customLabel: String) {
        prefs.setCustomCarrierLabel(customLabel)
        val state = uiState.value
        val display = customLabel.trim().ifEmpty { "X" }
        if (state.hasPrivilegeChannel && !internal.value.isWorking) {
            applyStatesideEnv(null)
        } else if (!state.hasPrivilegeChannel) {
            internal.update {
                it.copy(
                    toastBanner = "已设定全局名称「$display」；绑定无线调试后即可生效"
                )
            }
        }
    }

    fun updateSlotDisplayOverride(slotIndex: Int, rawOverride: String) {
        prefs.setSlotDisplayOverride(slotIndex, rawOverride)
        val state = uiState.value
        if (state.hasPrivilegeChannel && !internal.value.isWorking) {
            applyStatesideEnv(null)
        } else if (!state.hasPrivilegeChannel) {
            val tip = when {
                rawOverride.isBlank() -> "卡 ${slotIndex + 1} 已恢复跟随全局策略"
                rawOverride == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN -> "卡 ${slotIndex + 1} 已设为保持原运营商（绑定无线调试后生效）"
                else -> "卡 ${slotIndex + 1} 已设为显示「${rawOverride.trim()}」（绑定无线调试后生效）"
            }
            internal.update { it.copy(toastBanner = tip) }
        }
    }

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.setAutoRestoreOnBoot(enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.setThemeMode(mode)
    }

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.setUseDynamicColor(enabled)
    }

    fun saveManualConnectPort(rawInput: String) {
        val parsed = rawInput.trim().filter { it.isDigit() }.toIntOrNull() ?: 0
        val cleanPort = if (parsed in 1024..65535) parsed else 0
        prefs.setManualConnectPort(cleanPort)
        internal.update {
            it.copy(
                toastBanner = if (cleanPort > 0) {
                    "已保存固定连接端口：$cleanPort"
                } else {
                    "已恢复自动发现本机无线调试端口"
                }
            )
        }
    }

    fun resetAdbPairingState() {
        prefs.resetAdbPairingRecord()
        internal.update {
            it.copy(toastBanner = "已重置本机无线调试配对状态，可随时在激活向导中重新配对")
        }
    }

    fun toggleCoreSuiteApp(suiteApp: CoreSuiteApp) {
        prefs.toggleCoreSuiteApp(suiteApp)
    }

    fun toggleExtraEnglishPackage(packageName: String) {
        prefs.toggleExtraEnglishPackage(packageName)
        refreshSystemState()
    }

    fun requestPinShortcutForApp(packageName: String, title: String) {
        val ok = AppLaunchGuardHelper.requestPinGuardedShortcut(app, packageName, title)
        internal.update {
            it.copy(
                toastBanner = if (ok) {
                    "已请求添加桌面「$title · 美境」防漏改图标（请留意系统桌面快捷方式授权提示）"
                } else {
                    "当前桌面启动器不支持自动固定快捷方式"
                }
            )
        }
    }

    fun notifyBanner(message: String) {
        internal.update { it.copy(toastBanner = message) }
    }

    fun buildDiagnosticSummary(): String {
        val s = uiState.value
        val simSummary = if (s.simCards.isEmpty()) {
            "无已读取电话卡（权限=${s.hasPhonePermission}）"
        } else {
            s.simCards.joinToString(" | ") { sim ->
                "${sim.slotLabel}[MCC=${sim.rawMccMnc.take(3)}xx, SIM_ISO=${sim.currentReportedIso}, NET_ISO=${sim.networkCountryIso}, 显示='${sim.currentReportedCarrier}']"
            }
        }
        return buildString {
            appendLine("Stateside 美境诊断摘要 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("机型系统：${s.telemetry.deviceModel} · ${s.telemetry.androidRelease}")
            appendLine("ROM 识别：${s.romGuide.brandTitle}")
            appendLine("特权通道：${s.privilegeChannelTitle} (已配对=${s.userSettings.isAdbPaired}, Root=${s.hasRootAccess}, 开发者选项=${s.telemetry.isDeveloperOptionsEnabled})")
            appendLine("双卡状态：$simSummary")
            appendLine("时区策略：${s.userSettings.timezoneMode.shortBadge} (目标=${s.userSettings.selectedUsTimezone.zoneId}, 当前生效=${s.telemetry.systemTimezoneId} ${s.telemetry.systemTimezoneOffset})")
            appendLine("系统与应用语言：系统=${s.telemetry.systemLocaleTag}, 已锁定英语应用=${s.confirmedEnglishAppsCount}/${s.installedTargetEnglishAppsCount} 款")
            append("权限核查：电话=${s.hasPhonePermission}, 通知=${s.hasNotificationPermission}, 本地网络=${s.hasLocalNetworkPermission}")
        }
    }

    fun clearMessages() {
        internal.update { it.copy(toastBanner = null, errorDetail = null) }
    }

    @SuppressLint("MissingPermission")
    private fun querySimCards(): List<SimCardUiModel> {
        val subManager = app.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val baseTm = app.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val subs = runCatching { subManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        return subs.map { info ->
            val subTm = baseTm.createForSubscriptionId(info.subscriptionId)
            val mccMnc = subTm.simOperator.orEmpty()
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(mccMnc)
            val currentIso = subTm.simCountryIso.orEmpty()
            val netIso = subTm.networkCountryIso.orEmpty()
            val currentName = info.carrierName?.toString()?.trim().orEmpty().ifBlank {
                subTm.simOperatorName.orEmpty()
            }
            SimCardUiModel(
                slotIndex = info.simSlotIndex,
                slotLabel = "卡 ${info.simSlotIndex + 1}",
                rawMccMnc = mccMnc,
                realCountryLabel = MccIsoResolver.formatChineseCountryLabel(realIso),
                currentReportedIso = currentIso.uppercase().ifBlank { "未知" },
                networkCountryIso = netIso.uppercase().ifBlank { "—" },
                currentReportedCarrier = currentName.ifBlank { "默认运营商" },
                isUsSpoofActive = currentIso.equals("us", ignoreCase = true)
            )
        }
    }
}
