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
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.oem.RomSetupGuide
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
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
    val isEnglishConfirmed: Boolean
)

data class ExtraEnglishAppUiItem(
    val packageName: String,
    val appLabel: String,
    val isInstalled: Boolean,
    val isEnglishConfirmed: Boolean
)

data class SelectableAppItem(
    val packageName: String,
    val appLabel: String
)

data class DockAppUiItem(
    val packageName: String,
    val title: String,
    val isEnglishConfirmed: Boolean
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
                        isEnglishConfirmed = it.isEnglishConfirmed
                    )
                }
            val extraDock = extraAppItems
                .filter { it.isInstalled }
                .map {
                    DockAppUiItem(
                        packageName = it.packageName,
                        title = it.appLabel,
                        isEnglishConfirmed = it.isEnglishConfirmed
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
        AdbPairingService.phase
    ) { local, settings, phase ->
        val suiteItems = CoreSuiteApp.entries.map { suite ->
            val pkg = local.installedSuiteMap[suite]
            CoreSuiteUiItem(
                suiteApp = suite,
                installedPackage = pkg,
                isEnabled = settings.isSuiteAppEnabled(suite),
                isEnglishConfirmed = pkg != null && pkg in settings.confirmedEnglishPackages
            )
        }
        val extraItems = settings.extraEnglishPackages.sorted().map { pkg ->
            val meta = local.extraLabelsMap[pkg]
            ExtraEnglishAppUiItem(
                packageName = pkg,
                appLabel = meta?.first ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                isInstalled = meta?.second ?: false,
                isEnglishConfirmed = pkg in settings.confirmedEnglishPackages
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
        val tz = TimeZone.getDefault()
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
            systemTimezoneId = tz.id,
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

    fun applyStatesideEnv(packageToLaunchAfter: String? = null) {
        if (internal.value.isWorking) return
        viewModelScope.launch {
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在同步美境配置（双卡美区 + 应用原生英语引擎）…",
                    errorDetail = null,
                    toastBanner = null
                )
            }

            val result = LocalAdbManager.getInstance(app)
                .runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)

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
        val needsEnglishInit = packageName in state.userSettings.effectiveEnglishPackages() &&
            packageName !in state.userSettings.confirmedEnglishPackages
        if (state.isEnvironmentReady && !needsEnglishInit) {
            AppLaunchGuardHelper.launchPackageDirectly(app, packageName)
        } else {
            applyStatesideEnv(packageToLaunchAfter = packageName)
        }
    }

    fun restoreToDomesticDefault() {
        if (internal.value.isWorking) return
        viewModelScope.launch {
            internal.update {
                it.copy(
                    isWorking = true,
                    workingMessage = "正在执行两步无损还原，恢复真实归属地与跟随系统语言…",
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
            appendLine("系统语言与时区：${s.telemetry.systemLocaleTag} · ${s.telemetry.systemTimezoneId} (${s.telemetry.systemTimezoneOffset})")
            appendLine("英语锁定应用：已锁定 ${s.confirmedEnglishAppsCount}/${s.installedTargetEnglishAppsCount} 款（扩展应用 ${s.userSettings.extraEnglishPackages.size} 款）")
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
