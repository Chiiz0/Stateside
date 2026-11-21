package app.stateside.env.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stateside.env.StatesideApp
import app.stateside.env.adb.AdbPairingService
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.adb.PairingUiPhase
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.data.UserSettings
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.MccIsoResolver
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.oem.RomSetupGuide
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
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
    val slotLabel: String,
    val realCountryLabel: String,
    val currentReportedIso: String,
    val currentReportedCarrier: String,
    val isUsSpoofActive: Boolean
)

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
    val title: String
)

data class MainUiState(
    val hasPhonePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasLocalNetworkPermission: Boolean = true,
    val hasRootAccess: Boolean = false,
    val currentBootCount: Int = -1,
    val simCards: List<SimCardUiModel> = emptyList(),
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
    val hasPrivilegeChannel: Boolean
        get() = hasRootAccess || userSettings.isAdbPaired

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

    val unconfirmedEnglishAppsCount: Int
        get() {
            val unconfirmedCore = coreSuiteItems.count {
                it.installedPackage != null && it.isEnabled && !it.isEnglishConfirmed
            }
            val unconfirmedExtra = extraAppItems.count {
                it.isInstalled && !it.isEnglishConfirmed
            }
            return unconfirmedCore + unconfirmedExtra
        }

    val safeLaunchDockItems: List<DockAppUiItem>
        get() {
            val coreDock = coreSuiteItems
                .filter { it.installedPackage != null && it.isEnabled }
                .map { DockAppUiItem(it.installedPackage!!, it.suiteApp.title) }
            val extraDock = extraAppItems
                .filter { it.isInstalled }
                .map { DockAppUiItem(it.packageName, it.appLabel) }
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
                    installedSuiteMap = suiteMap,
                    extraLabelsMap = extraMap
                )
            }
        }
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
                    workingMessage = "正在开启美境（美国运营商 + 英语应用组同步驱动）…",
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

    /**
     * 在激活向导中供已配对过（或已开启 TCP 5555 / 手填无线调试连接端口）的用户直接重连并开启美境。
     */
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

    /**
     * 在主界面快捷应用坞点击某个应用（TikTok / X / Claude / ChatGPT 及扩展应用）：
     * 若美境已生效则直接毫秒级打开；若因重启未生效则先自动补写美境再打开该应用。
     */
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
                    workingMessage = "正在执行两步干净还原，恢复国内电话卡与跟随系统中文…",
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
                    toastBanner = "已选定「${preset.chipLabel}」；完成上方本机无线调试绑定后即可一键生效"
                )
            }
        }
    }

    fun setAutoRestoreOnBoot(enabled: Boolean) {
        prefs.setAutoRestoreOnBoot(enabled)
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
                    "已请求创建桌面「$title · 美境」防漏改图标（国产桌面请留意“桌面快捷方式”权限提示）"
                } else {
                    "当前桌面启动器不支持自动固定快捷方式"
                }
            )
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
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(subTm.simOperator)
            val currentIso = subTm.simCountryIso.orEmpty()
            val currentName = subTm.simOperatorName.orEmpty().ifBlank {
                info.carrierName?.toString().orEmpty()
            }
            SimCardUiModel(
                slotLabel = "卡 ${info.simSlotIndex + 1}",
                realCountryLabel = MccIsoResolver.formatChineseCountryLabel(realIso),
                currentReportedIso = currentIso.uppercase().ifBlank { "未知" },
                currentReportedCarrier = currentName.ifBlank { "默认运营商" },
                isUsSpoofActive = currentIso.equals("us", ignoreCase = true)
            )
        }
    }
}
