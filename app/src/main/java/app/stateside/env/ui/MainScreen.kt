package app.stateside.env.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stateside.env.model.AppThemeMode
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.TimezoneMode
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.model.UsTimezonePreset
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.ui.components.StatesideBrandEmblem
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.screens.AppLocaleScreen
import app.stateside.env.ui.screens.DashboardScreen
import app.stateside.env.ui.screens.EnvironmentInspectorSheet
import app.stateside.env.ui.screens.SettingsScreen
import app.stateside.env.ui.screens.SimStudioScreen
import app.stateside.env.ui.screens.TimezoneStudioSheet

enum class StatesideTab(
    val label: String,
    val pageTitle: String,
    val pageSubtitle: String,
    val icon: ImageVector
) {
    DASHBOARD(
        label = "工作台",
        pageTitle = "美境 · Stateside",
        pageSubtitle = "美区环境总控与安全快启",
        icon = StatesideIcons.Dashboard
    ),
    SIM_STUDIO(
        label = "号卡",
        pageTitle = "号卡显标工作坊",
        pageSubtitle = "双卡 US 区域与分卡槽独立显标",
        icon = StatesideIcons.SimCard
    ),
    APP_ENGINE(
        label = "应用",
        pageTitle = "应用语言沙盒",
        pageSubtitle = "系统保持中文 · 目标应用固定 en-US",
        icon = StatesideIcons.GlobeLanguage
    ),
    SETTINGS(
        label = "设置",
        pageTitle = "设置与关于",
        pageSubtitle = "特权通道 · 保活自愈 · 主题与诊断",
        icon = StatesideIcons.SlidersTune
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onApplyStateside: () -> Unit,
    onConnectManualPortAndApply: (String, () -> Unit) -> Unit,
    onSafeLaunchApp: (String) -> Unit,
    onRestoreDefault: () -> Unit,
    onSelectCarrier: (UsCarrierPreset) -> Unit,
    onApplyCustomCarrierLabel: (String) -> Unit,
    onUpdateSlotDisplayOverride: (Int, String) -> Unit,
    onSelectTimezoneMode: (TimezoneMode) -> Unit,
    onSelectTimezonePreset: (UsTimezonePreset) -> Unit,
    onToggleCompanionSuite: (CoreSuiteApp) -> Unit,
    onToggleCompanionExtraPkg: (String, String) -> Unit,
    onSwitchUsTimezoneNow: () -> Unit,
    onRestoreHomeTimezoneNow: () -> Unit,
    onToggleAutoBoot: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSaveManualPort: (String) -> Unit,
    onResetPairingState: () -> Unit,
    onToggleCoreSuiteApp: (CoreSuiteApp) -> Unit,
    onOpenAppPicker: () -> Unit,
    onToggleExtraPkg: (String) -> Unit,
    onPinShortcutForApp: (String, String) -> Unit,
    onBuildDiagnostics: () -> String,
    onNotifyBanner: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by rememberSaveable { mutableStateOf(StatesideTab.DASHBOARD) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var showTimezoneStudio by rememberSaveable { mutableStateOf(false) }
    var showInspector by rememberSaveable { mutableStateOf(false) }
    var showRestoreConfirm by rememberSaveable { mutableStateOf(false) }

    // 子页按系统返回键优雅回到工作台首页
    BackHandler(enabled = currentTab != StatesideTab.DASHBOARD) {
        currentTab = StatesideTab.DASHBOARD
    }

    LaunchedEffect(state.toastBanner) {
        val msg = state.toastBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val isCompactScreen = maxWidth < 365.dp
        val outerHorizontalPad = if (isCompactScreen) 12.dp else 16.dp
        val cardInnerPad = if (isCompactScreen) 14.dp else 16.dp
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    expandedHeight = 68.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatesideBrandEmblem(
                                size = 36.dp,
                                isActive = state.isEnvironmentReady
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = currentTab.pageTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTab.pageSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showInspector = true }) {
                            Icon(
                                imageVector = StatesideIcons.RadarScan,
                                contentDescription = "环境透视自检"
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新系统状态"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            StatesideTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabBadgeIcon(
                                            tab = tab,
                                            pendingCount = state.unconfirmedEnglishAppsCount
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.weight(0.2f))
                        StatesideTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabBadgeIcon(
                                        tab = tab,
                                        pendingCount = state.unconfirmedEnglishAppsCount
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                        },
                        label = "tab_content",
                        modifier = Modifier.fillMaxSize()
                    ) { activeTab ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 660.dp)
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = outerHorizontalPad, vertical = 8.dp)
                            ) {
                                when (activeTab) {
                                    StatesideTab.DASHBOARD -> DashboardScreen(
                                        state = state,
                                        cardPadding = cardInnerPad,
                                        onRequestPermissions = onRequestPermissions,
                                        onOpenOnboarding = { showOnboarding = true },
                                        onApplyStateside = onApplyStateside,
                                        onOpenRestoreConfirm = { showRestoreConfirm = true },
                                        onSafeLaunchApp = onSafeLaunchApp,
                                        onNavigateToSimStudio = { currentTab = StatesideTab.SIM_STUDIO },
                                        onNavigateToAppEngine = { currentTab = StatesideTab.APP_ENGINE },
                                        onNavigateToSettings = { currentTab = StatesideTab.SETTINGS },
                                        onOpenTimezoneStudio = { showTimezoneStudio = true },
                                        onRestoreCompanionTimezoneNow = onRestoreHomeTimezoneNow,
                                        onOpenEnvironmentInspector = { showInspector = true }
                                    )

                                    StatesideTab.SIM_STUDIO -> SimStudioScreen(
                                        state = state,
                                        cardPadding = cardInnerPad,
                                        onRequestPermissions = onRequestPermissions,
                                        onSelectCarrier = onSelectCarrier,
                                        onApplyCustomCarrierLabel = onApplyCustomCarrierLabel,
                                        onUpdateSlotDisplayOverride = onUpdateSlotDisplayOverride
                                    )

                                    StatesideTab.APP_ENGINE -> AppLocaleScreen(
                                        state = state,
                                        cardPadding = cardInnerPad,
                                        onApplyStateside = onApplyStateside,
                                        onSafeLaunchApp = onSafeLaunchApp,
                                        onToggleCoreSuiteApp = onToggleCoreSuiteApp,
                                        onToggleCompanionSuite = onToggleCompanionSuite,
                                        onToggleCompanionExtraPkg = onToggleCompanionExtraPkg,
                                        onOpenTimezoneStudio = { showTimezoneStudio = true },
                                        onOpenAppPicker = {
                                            onOpenAppPicker()
                                            showAppPicker = true
                                        },
                                        onRemoveExtraPkg = onToggleExtraPkg,
                                        onPinShortcutForApp = onPinShortcutForApp
                                    )

                                    StatesideTab.SETTINGS -> SettingsScreen(
                                        state = state,
                                        cardPadding = cardInnerPad,
                                        onRequestPermissions = onRequestPermissions,
                                        onOpenOnboarding = { showOnboarding = true },
                                        onOpenTimezoneStudio = { showTimezoneStudio = true },
                                        onOpenInspector = { showInspector = true },
                                        onForceReapply = onApplyStateside,
                                        onOpenRestoreConfirm = { showRestoreConfirm = true },
                                        onToggleAutoBoot = onToggleAutoBoot,
                                        onSelectThemeMode = onSelectThemeMode,
                                        onToggleDynamicColor = onToggleDynamicColor,
                                        onSaveManualPort = onSaveManualPort,
                                        onResetPairingState = onResetPairingState,
                                        onBuildDiagnostics = onBuildDiagnostics,
                                        onNotifyBanner = onNotifyBanner
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOnboarding) {
        OnboardingSheet(
            romGuide = state.romGuide,
            pairingPhase = state.pairingPhase,
            hasNotificationPermission = state.hasNotificationPermission,
            savedManualConnectPort = state.userSettings.manualConnectPort,
            isWorking = state.isWorking,
            onRequestPermissions = onRequestPermissions,
            onConnectManualPortAndApply = { manualPort ->
                onConnectManualPortAndApply(manualPort) {
                    showOnboarding = false
                }
            },
            onDismiss = {
                showOnboarding = false
                onRefresh()
            }
        )
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = state.selectableApps,
            selectedPackages = state.userSettings.extraEnglishPackages,
            onTogglePackage = onToggleExtraPkg,
            onDismiss = { showAppPicker = false }
        )
    }

    if (showTimezoneStudio) {
        TimezoneStudioSheet(
            state = state,
            onSelectMode = onSelectTimezoneMode,
            onSelectPreset = onSelectTimezonePreset,
            onToggleCompanionSuite = onToggleCompanionSuite,
            onToggleCompanionExtraPkg = onToggleCompanionExtraPkg,
            onSwitchUsTimezoneNow = onSwitchUsTimezoneNow,
            onRestoreHomeTimezoneNow = onRestoreHomeTimezoneNow,
            onDismiss = { showTimezoneStudio = false }
        )
    }

    if (showInspector) {
        EnvironmentInspectorSheet(
            state = state,
            onOpenTimezoneStudio = { showTimezoneStudio = true },
            onBuildDiagnostics = onBuildDiagnostics,
            onCopiedNotice = onNotifyBanner,
            onDismiss = { showInspector = false }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复国内默认状态？") },
            text = {
                Text("将把所有电话卡还原为真实国内归属地与原运营商显示，并将受控海外与 AI 应用恢复跟随手机系统中文。随时可再次一键开启美境。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreDefault()
                    }
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (!state.errorDetail.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onClearMessages,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("请检查无线调试状态") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.errorDetail)
                    if (state.romGuide.requiresExtraSecurityToggle) {
                        Text(
                            text = "${state.romGuide.brandTitle}重点检查：${state.romGuide.criticalExtraToggleTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearMessages()
                        ChineseRomGuide.openDeveloperOptionsOrAboutPhone(context)
                    }
                ) {
                    Text("跳转无线调试")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onClearMessages()
                        showOnboarding = true
                    }
                ) {
                    Text("配对向导")
                }
            }
        )
    }
}

@Composable
private fun TabBadgeIcon(
    tab: StatesideTab,
    pendingCount: Int
) {
    if (tab == StatesideTab.APP_ENGINE && pendingCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(pendingCount.toString()) }
            }
        ) {
            Icon(imageVector = tab.icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = tab.icon, contentDescription = tab.label)
    }
}
