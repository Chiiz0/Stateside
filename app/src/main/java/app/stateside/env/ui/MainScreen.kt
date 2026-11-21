package app.stateside.env.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.data.UserPrefsRepository
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
    onApplyCustomCarrierLabel: (String) -> Unit,
    onUpdateSlotDisplayOverride: (Int, String) -> Unit,
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
    var editingSlotSim by remember { mutableStateOf<SimCardUiModel?>(null) }
    var customCarrierDraft by remember(state.userSettings.customCarrierLabel) {
        mutableStateOf(state.userSettings.customCarrierLabel)
    }

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
                expandedHeight = 68.dp,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "美境 · Stateside",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "美区电话卡 · 应用原生英语工作台",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 自适应小屏旗舰（如 347dp 紧凑屏）、常规直板机（360~430dp）与折叠屏/平板展开态（>=600dp）
            val isCompactScreen = maxWidth < 365.dp
            val outerHorizontalPad = if (isCompactScreen) 12.dp else 16.dp
            val cardInnerPad = if (isCompactScreen) 14.dp else 16.dp

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = outerHorizontalPad, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.hasPhonePermission || !state.hasNotificationPermission || !state.hasLocalNetworkPermission) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(cardInnerPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "授予双卡、通知与本地网络权限",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "用于实时核验双卡 US 状态，并在无线调试页面下拉通知栏直接完成配对。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = onRequestPermissions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                            ) {
                                Text("一键授予所需权限", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // 顶部主控卡片 + 2 列等宽对齐安全快启坞
                HeroWorkspaceCard(
                    state = state,
                    cardPadding = cardInnerPad,
                    onStartOnboarding = {
                        onRequestPermissions()
                        showOnboarding = true
                    },
                    onApplyStateside = onApplyStateside,
                    onSafeLaunchApp = onSafeLaunchApp
                )

                // 卡片一：默认自动勾选的「美境核心英语应用组」+ 扩展应用
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(cardInnerPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "英语驱动应用组",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            StatusPillBadge(
                                text = "固定 en-US",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "默认勾选四大海外与 AI 工具，开启美境时自动锁定美式英语（手机系统保持简体中文不变）：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        state.coreSuiteItems.forEach { item ->
                            CoreSuiteAppRowCard(
                                item = item,
                                canPinShortcut = state.hasPrivilegeChannel,
                                onToggle = { onToggleCoreSuiteApp(item.suiteApp) },
                                onPinShortcut = { pkg ->
                                    onPinShortcutForApp(pkg, item.suiteApp.title)
                                }
                            )
                        }

                        if (state.extraAppItems.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = "扩展英语应用",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.extraAppItems.forEach { extra ->
                                ExtraEnglishAppRowCard(
                                    extra = extra,
                                    canPinShortcut = state.hasPrivilegeChannel,
                                    onRemove = { onToggleExtraPkg(extra.packageName) },
                                    onPinShortcut = {
                                        onPinShortcutForApp(extra.packageName, extra.appLabel)
                                    }
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                onOpenAppPicker()
                                showAppPicker = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            val extraCount = state.userSettings.extraEnglishPackages.size
                            Text(
                                text = if (extraCount == 0) {
                                    "添加其他英语应用"
                                } else {
                                    "管理扩展英语应用（已选 $extraCount 款）"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 卡片二：电话卡目标区域（默认保持原状态栏运营商显示）
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(cardInnerPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "电话卡目标区域",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.userSettings.selectedCarrier == UsCarrierPreset.CUSTOM) {
                                        "当前全局模式：自定义「${state.userSettings.customCarrierLabel}」"
                                    } else {
                                        "当前全局模式：${state.userSettings.selectedCarrier.chipLabel}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { showCarrierOptions = !showCarrierOptions },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (showCarrierOptions) "收起" else "切换模式",
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }

                        AnimatedVisibility(visible = showCarrierOptions) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "默认「保持原运营商」仅将 SIM 国家码设为美国 (US)；若选择自定义或指定运营商（也可点击下方卡槽单独设置某张卡显示为 X），将自动穿透移动全球通钻卡等 5G 显标锁定：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    UsCarrierPreset.entries.forEach { preset ->
                                        val selected = state.userSettings.selectedCarrier == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectCarrier(preset) },
                                            label = {
                                                Text(
                                                    text = preset.chipLabel,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }

                                if (state.userSettings.selectedCarrier == UsCarrierPreset.CUSTOM) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customCarrierDraft,
                                            onValueChange = { customCarrierDraft = it.take(24) },
                                            label = { Text("自定义运营商显示（如 X）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            enabled = !state.isWorking,
                                            onClick = { onApplyCustomCarrierLabel(customCarrierDraft) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("应用", softWrap = false, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.simCards.isEmpty()) {
                            Text(
                                text = if (state.hasPhonePermission) {
                                    "未检测到实体电话卡 / eSIM（无卡状态不会触发国内运营商锁区，可直接驱动应用英语环境）"
                                } else {
                                    "授予电话状态权限后可实时查看双卡物理归属地与当前对外国家码"
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
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${sim.slotLabel} · 物理卡：${sim.realCountryLabel}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            StatusPillBadge(
                                                text = if (sim.isUsSpoofActive) "美境生效" else "国内原卡",
                                                containerColor = if (sim.isUsSpoofActive) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                contentColor = if (sim.isUsSpoofActive) {
                                                    MaterialTheme.colorScheme.onSecondary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = if (sim.isUsSpoofActive) {
                                                    "国家码：美国 (US)"
                                                } else {
                                                    "国家码：${sim.currentReportedIso}"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "状态栏显示：${sim.currentReportedCarrier}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        val slotRule = state.userSettings.slotOverrideFor(sim.slotIndex)
                                        val slotChipText = when {
                                            slotRule.isBlank() -> "单独设${sim.slotLabel}显示名（如设为 X）"
                                            slotRule == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN -> "${sim.slotLabel}专属：保持原运营商（点此修改）"
                                            else -> "${sim.slotLabel}专属：显示为「$slotRule」（点此修改）"
                                        }
                                        Surface(
                                            onClick = { editingSlotSim = sim },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                    text = slotChipText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 卡片三：重启自愈守护
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(cardInnerPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "无感守护",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "重启连上 Wi-Fi 后自动恢复美境",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "手机重启后一旦连上已授权 Wi-Fi 将自动静默补写美区国家码（应用英语设置永久保留）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "一键恢复国内默认状态",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(18.dp))
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

    val slotTarget = editingSlotSim
    if (slotTarget != null) {
        val currentRule = state.userSettings.slotOverrideFor(slotTarget.slotIndex)
        var draftText by remember(slotTarget.slotIndex, currentRule) {
            mutableStateOf(
                if (currentRule.isNotBlank() && currentRule != UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN) {
                    currentRule
                } else {
                    "X"
                }
            )
        }
        AlertDialog(
            onDismissRequest = { editingSlotSim = null },
            title = { Text("单独设置 ${slotTarget.slotLabel} 状态栏显示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "可单独为 ${slotTarget.slotLabel} 指定状态栏运营商文案（自动穿透移动全球通钻卡等 5G 核心网显标锁定，另一张卡不受影响）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = currentRule == "X",
                            onClick = {
                                onUpdateSlotDisplayOverride(slotTarget.slotIndex, "X")
                                editingSlotSim = null
                            },
                            label = { Text("设为 X", softWrap = false) }
                        )
                        FilterChip(
                            selected = currentRule == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN,
                            onClick = {
                                onUpdateSlotDisplayOverride(
                                    slotTarget.slotIndex,
                                    UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN
                                )
                                editingSlotSim = null
                            },
                            label = { Text("保持原运营商", softWrap = false) }
                        )
                        FilterChip(
                            selected = currentRule == "T-Mobile",
                            onClick = {
                                onUpdateSlotDisplayOverride(slotTarget.slotIndex, "T-Mobile")
                                editingSlotSim = null
                            },
                            label = { Text("T-Mobile", softWrap = false) }
                        )
                        FilterChip(
                            selected = currentRule.isBlank(),
                            onClick = {
                                onUpdateSlotDisplayOverride(slotTarget.slotIndex, "")
                                editingSlotSim = null
                            },
                            label = { Text("跟随全局", softWrap = false) }
                        )
                    }
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it.take(24) },
                        label = { Text("自定义文字（如 X）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSlotDisplayOverride(slotTarget.slotIndex, draftText.trim().ifEmpty { "X" })
                        editingSlotSim = null
                    }
                ) {
                    Text("保存并生效")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSlotSim = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复国内默认状态？") },
            text = {
                Text("将把所有电话卡还原为真实国内归属地，并将 TikTok、X、Claude、ChatGPT 及扩展应用恢复为跟随手机系统中文。随时可再次一键开启美境。")
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
                    Text("打开向导")
                }
            }
        )
    }
}

@Composable
private fun StatusPillBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreSuiteAppRowCard(
    item: CoreSuiteUiItem,
    canPinShortcut: Boolean,
    onToggle: () -> Unit,
    onPinShortcut: (String) -> Unit
) {
    val installedPkg = item.installedPackage
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (installedPkg != null) {
                InstalledAppIcon(
                    packageName = installedPkg,
                    size = 40.dp
                )
            } else {
                AppMonogramPlaceholder(
                    letter = item.suiteApp.title.take(1),
                    size = 40.dp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.suiteApp.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    StatusPillBadge(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = when {
                            installedPkg != null && item.isEnglishConfirmed -> "en-US 已锁定"
                            installedPkg != null -> "已安装"
                            else -> "未安装"
                        },
                        containerColor = when {
                            installedPkg != null && item.isEnglishConfirmed -> {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            installedPkg != null -> {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                            else -> {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        },
                        contentColor = when {
                            installedPkg != null && item.isEnglishConfirmed -> {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            installedPkg != null -> {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                            else -> {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                    )
                }

                Text(
                    text = item.suiteApp.roleDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (installedPkg != null && canPinShortcut) {
                    PinGuardShortcutChip(
                        onClick = { onPinShortcut(installedPkg) }
                    )
                }
            }

            Checkbox(
                checked = item.isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraEnglishAppRowCard(
    extra: ExtraEnglishAppUiItem,
    canPinShortcut: Boolean,
    onRemove: () -> Unit,
    onPinShortcut: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (extra.isInstalled) {
                InstalledAppIcon(
                    packageName = extra.packageName,
                    size = 36.dp
                )
            } else {
                AppMonogramPlaceholder(
                    letter = extra.appLabel.take(1),
                    size = 36.dp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = extra.appLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    if (extra.isEnglishConfirmed) {
                        StatusPillBadge(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = "en-US 已锁定",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = extra.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (extra.isInstalled && canPinShortcut) {
                    PinGuardShortcutChip(onClick = onPinShortcut)
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除扩展应用",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PinGuardShortcutChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "添加桌面防漏改图标",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppMonogramPlaceholder(
    letter: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = letter.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroWorkspaceCard(
    state: MainUiState,
    cardPadding: Dp,
    onStartOnboarding: () -> Unit,
    onApplyStateside: () -> Unit,
    onSafeLaunchApp: (String) -> Unit
) {
    val containerColor = when {
        state.isEnvironmentReady -> MaterialTheme.colorScheme.secondaryContainer
        !state.hasPrivilegeChannel -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (state.isEnvironmentReady) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when {
                            state.isEnvironmentReady && state.hasPrivilegeChannel -> "美境生效中（US · English）"
                            state.isEnvironmentReady && !state.hasPrivilegeChannel -> "美境已生效（待绑定无线调试）"
                            !state.hasPrivilegeChannel -> "欢迎使用美境 · 1 分钟首次激活"
                            else -> "待一键开启美境"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when {
                            state.isEnvironmentReady && state.hasPrivilegeChannel -> {
                                val channelTag = if (state.hasRootAccess) "Root 直通" else "本机无线调试"
                                val carrierDesc = when {
                                    state.userSettings.slot0DisplayOverride.isNotBlank() ||
                                        state.userSettings.slot1DisplayOverride.isNotBlank() -> {
                                        "双卡美国 (US · 已启用分卡独立显示)"
                                    }
                                    state.userSettings.selectedCarrier == UsCarrierPreset.CUSTOM -> {
                                        "双卡美国 · 显示「${state.userSettings.customCarrierLabel}」"
                                    }
                                    state.userSettings.selectedCarrier.keepsOriginalCarrierName -> {
                                        "双卡美国 (US · 保留原运营商)"
                                    }
                                    else -> {
                                        "双卡美国 ${state.userSettings.selectedCarrier.carrierNameOverride} (US)"
                                    }
                                }
                                if (state.unconfirmedEnglishAppsCount > 0) {
                                    "通道：$channelTag · $carrierDesc · ${state.unconfirmedEnglishAppsCount} 款新应用待同步"
                                } else {
                                    "通道：$channelTag · $carrierDesc · 英语应用已就绪"
                                }
                            }
                            state.isEnvironmentReady && !state.hasPrivilegeChannel -> {
                                "双卡与英语应用已处于美区状态；建议绑定一次手机自身无线调试，脱离电脑后也能随时在手机上切换或恢复"
                            }
                            !state.hasPrivilegeChannel -> "免装 Shizuku 等极客插件，绑定一次本机无线调试即可一键驱动美区卡（保留原状态栏运营商）与英语应用"
                            state.hasRootAccess -> "已检测到本机 Root 免配对通道，点击下方按钮立即驱动美境环境"
                            else -> "点击下方按钮自动重连本机无线调试，2 秒内切换为美区电话卡与英语环境"
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
                    Text(
                        text = state.workingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (!state.hasPrivilegeChannel) {
                Button(
                    onClick = onStartOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Text(
                        text = if (state.isEnvironmentReady) "绑定本机无线调试" else "开始 1 分钟首次激活",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val dockItems = state.safeLaunchDockItems
                if (state.isEnvironmentReady && dockItems.isNotEmpty()) {
                    SafeLaunchSymmetricGrid(
                        title = "安全快启坞（当前美区环境已就绪）：",
                        dockItems = dockItems,
                        onSafeLaunchApp = onSafeLaunchApp
                    )
                }
            } else {
                Button(
                    onClick = onApplyStateside,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.isEnvironmentReady && state.unconfirmedEnglishAppsCount > 0 -> {
                                "同步新装应用英语环境 (${state.unconfirmedEnglishAppsCount} 款)"
                            }
                            state.isEnvironmentReady -> "同步刷新美境配置"
                            else -> "一键开启美境"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val dockItems = state.safeLaunchDockItems
                if (dockItems.isNotEmpty()) {
                    SafeLaunchSymmetricGrid(
                        title = "安全快启坞（自动核验美区环境后秒开）：",
                        dockItems = dockItems,
                        onSafeLaunchApp = onSafeLaunchApp
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeLaunchSymmetricGrid(
    title: String,
    dockItems: List<DockAppUiItem>,
    onSafeLaunchApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        dockItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    FilledTonalButton(
                        onClick = { onSafeLaunchApp(item.packageName) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        InstalledAppIcon(
                            packageName = item.packageName,
                            size = 20.dp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
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
                .clip(RoundedCornerShape(8.dp))
        )
    }
}
