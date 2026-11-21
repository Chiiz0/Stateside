package app.stateside.env.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.oem.ChineseRomGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onToggleAutoBoot: (Boolean) -> Unit,
    onToggleCoreSuiteApp: (CoreSuiteApp) -> Unit,
    onOpenAppPicker: () -> Unit,
    onToggleExtraPkg: (String) -> Unit,
    onPinShortcutForApp: (String, String) -> Unit,
    onClearMessages: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showOnboarding by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showCarrierOptions by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastBanner) {
        val msg = state.toastBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("美境 · Stateside", fontWeight = FontWeight.Bold)
                        Text(
                            text = "美区电话卡环境 · 海外与 AI 应用原生英语工作台",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                    }
                    IconButton(onClick = { showOnboarding = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "无线调试与激活向导")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.hasPhonePermission || !state.hasNotificationPermission) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "授予双卡状态读取与通知权限",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "用于精准校验双卡 US 状态，并在首次激活时直接在通知栏提交 6 位配对码。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = onRequestPermissions) {
                            Text("一键允许")
                        }
                    }
                }
            }

            // 顶部主控卡片 + 已安装应用安全快启坞
            HeroWorkspaceCard(
                state = state,
                onStartOnboarding = {
                    onRequestPermissions()
                    showOnboarding = true
                },
                onApplyStateside = onApplyStateside,
                onSafeLaunchApp = onSafeLaunchApp
            )

            // 卡片一：默认自动勾选的「美境核心英语应用组」（TikTok、X、Claude、ChatGPT）+ 扩展应用
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "英语驱动应用组（固定美国英语 en-US）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "以下四大核心工具开箱默认勾选，开启美境时自动切换为纯正美式英语界面（手机系统保持简体中文不变）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.coreSuiteItems.forEach { item ->
                        val installedPkg = item.installedPackage
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (installedPkg != null) {
                                    InstalledAppIcon(
                                        packageName = installedPkg,
                                        size = 36.dp,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.suiteApp.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                installedPkg != null && item.isEnglishConfirmed -> "已安装 · en-US 已锁定"
                                                installedPkg != null -> "已安装"
                                                else -> "未安装"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (installedPkg != null) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Text(
                                        text = item.suiteApp.roleDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (installedPkg != null && state.hasPrivilegeChannel) {
                                    TextButton(
                                        onClick = { onPinShortcutForApp(installedPkg, item.suiteApp.title) }
                                    ) {
                                        Text("桌面图标")
                                    }
                                }

                                Checkbox(
                                    checked = item.isEnabled,
                                    onCheckedChange = { onToggleCoreSuiteApp(item.suiteApp) }
                                )
                            }
                        }
                    }

                    if (state.extraAppItems.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "已添加的扩展英语应用：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.extraAppItems.forEach { extra ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (extra.isInstalled) {
                                        InstalledAppIcon(
                                            packageName = extra.packageName,
                                            size = 32.dp,
                                            modifier = Modifier.padding(end = 10.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = extra.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (extra.isEnglishConfirmed) {
                                                "${extra.packageName} · en-US 已锁定"
                                            } else {
                                                extra.packageName
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (extra.isInstalled && state.hasPrivilegeChannel) {
                                        TextButton(
                                            onClick = { onPinShortcutForApp(extra.packageName, extra.appLabel) }
                                        ) {
                                            Text("桌面图标")
                                        }
                                    }
                                    IconButton(onClick = { onToggleExtraPkg(extra.packageName) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "移除扩展应用",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onOpenAppPicker()
                            showAppPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        val extraCount = state.userSettings.extraEnglishPackages.size
                        Text(
                            if (extraCount == 0) {
                                "添加其他需要驱动成美国英语的应用"
                            } else {
                                "管理扩展英语应用（已选 $extraCount 款）"
                            }
                        )
                    }
                }
            }

            // 卡片二：电话卡区域状态（默认美国 T-Mobile）
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "电话卡目标区域",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showCarrierOptions = !showCarrierOptions }) {
                            Text(if (showCarrierOptions) "收起运营商" else "目标：${state.userSettings.selectedCarrier.carrierNameOverride} (US)")
                        }
                    }

                    AnimatedVisibility(visible = showCarrierOptions) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "选择模拟的美国本土运营商（默认 T-Mobile 兼容性最佳）：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UsCarrierPreset.entries.forEach { preset ->
                                    val selected = state.userSettings.selectedCarrier == preset
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectCarrier(preset) },
                                        label = { Text(preset.carrierNameOverride) },
                                        leadingIcon = if (selected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    if (state.simCards.isEmpty()) {
                        Text(
                            text = if (state.hasPhonePermission) {
                                "未检测到实体电话卡/eSIM（无卡状态不会触发国内运营商锁区，可直接驱动应用英语环境）"
                            } else {
                                "授予电话状态权限后可实时查看卡 1 / 卡 2 的物理归属地与对外上报状态"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.simCards.forEach { sim ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (sim.isUsSpoofActive) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${sim.slotLabel} · 物理卡：${sim.realCountryLabel}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (sim.isUsSpoofActive) {
                                                "当前对外上报：美国 ${sim.currentReportedCarrier} (US)"
                                            } else {
                                                "当前对外上报：${sim.currentReportedCarrier} (${sim.currentReportedIso})"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (sim.isUsSpoofActive) "美境生效" else "国内原卡",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sim.isUsSpoofActive) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 卡片三：重启自愈守护
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "无感守护",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "手机重启连上 Wi-Fi 后自动恢复美境",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "电话卡区域覆盖在手机重启后会复原；开启后一旦连上已授权 Wi-Fi 将自动静默补写（应用英语设置永久保留无需强停）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.userSettings.autoRestoreOnBoot,
                            onCheckedChange = onToggleAutoBoot
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "当前机型贴士：${state.romGuide.keepAliveTip}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = { showRestoreConfirm = true },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("一键恢复国内默认状态（还原电话卡与系统中文语言）")
            }

            Spacer(Modifier.height(20.dp))
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

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复国内默认状态？") },
            text = {
                Text("将把所有电话卡还原为真实国内运营商归属地，并将 TikTok、X、Claude、ChatGPT 及扩展应用恢复为跟随手机系统中文。随时可再次一键开启美境。")
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
            title = { Text("需要检查无线调试或系统开关") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.errorDetail)
                    Text(
                        text = "${state.romGuide.brandTitle}重点检查：${state.romGuide.criticalExtraToggleTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    Text("打开激活向导")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroWorkspaceCard(
    state: MainUiState,
    onStartOnboarding: () -> Unit,
    onApplyStateside: () -> Unit,
    onSafeLaunchApp: (String) -> Unit
) {
    val containerColor = when {
        !state.hasPrivilegeChannel -> MaterialTheme.colorScheme.primaryContainer
        state.isEnvironmentReady -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.isEnvironmentReady) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            !state.hasPrivilegeChannel -> "欢迎进入美境 · 首次仅需 1 分钟激活"
                            state.isEnvironmentReady -> "美境生效中（US · English）"
                            else -> "待一键开启美境"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when {
                            !state.hasPrivilegeChannel -> "免装 Shizuku 等极客插件，完成一次本机无线调试绑定即可一键驱动美区卡与全套英语应用"
                            state.isEnvironmentReady -> {
                                val channelTag = if (state.hasRootAccess) "Root 直通" else "本机无线调试"
                                "通道：$channelTag · 电话卡已模拟美国 ${state.userSettings.selectedCarrier.carrierNameOverride}，英语应用组已就绪"
                            }
                            state.hasRootAccess -> "已检测到本机 Root 免配对通道，点击下方按钮立即驱动美境环境"
                            else -> "点击下方按钮即可自动重连本机无线调试，2 秒内切换为美区电话卡与英语环境"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (state.isWorking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(state.workingMessage, fontWeight = FontWeight.Medium)
                }
            } else if (!state.hasPrivilegeChannel) {
                Button(
                    onClick = onStartOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text("开始 1 分钟首次激活", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = onApplyStateside,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isEnvironmentReady) "同步刷新美境配置" else "一键开启美境",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // 已安装的核心应用与扩展应用安全快启坞
                val dockItems = state.safeLaunchDockItems
                if (dockItems.isNotEmpty()) {
                    Text(
                        text = "安全快启坞（自动核验美区环境后秒开）：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dockItems.forEach { item ->
                            FilledTonalButton(
                                onClick = { onSafeLaunchApp(item.packageName) }
                            ) {
                                InstalledAppIcon(
                                    packageName = item.packageName,
                                    size = 18.dp,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(item.title)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledAppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val px = 96
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, px, px)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    }
}
