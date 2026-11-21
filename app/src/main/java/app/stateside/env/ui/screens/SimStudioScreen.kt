package app.stateside.env.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.model.UsCarrierPreset
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.SimCardUiModel
import app.stateside.env.ui.components.IconBadgeBox
import app.stateside.env.ui.components.LiveStatusBarMockup
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimStudioScreen(
    state: MainUiState,
    cardPadding: Dp,
    onRequestPermissions: () -> Unit,
    onSelectCarrier: (UsCarrierPreset) -> Unit,
    onApplyCustomCarrierLabel: (String) -> Unit,
    onUpdateSlotDisplayOverride: (Int, String) -> Unit
) {
    var editingSlotSim by remember { mutableStateOf<SimCardUiModel?>(null) }
    var globalCustomDraft by remember(state.userSettings.customCarrierLabel) {
        mutableStateOf(state.userSettings.customCarrierLabel)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 状态栏与控制中心实时仿真预览窗
        LiveStatusBarMockup(
            simCards = state.simCards,
            settings = state.userSettings
        )

        // 2. 分卡槽精细化定制台
        SectionGroupCard(
            title = "双卡槽独立控制台",
            subtitle = "可为每张电话卡独立指定状态栏文案（互不干扰，支持穿透 5G 核心网尊享显标）",
            contentPadding = cardPadding
        ) {
            if (!state.hasPhonePermission) {
                Text(
                    text = "授予电话状态权限后，即可实时查看卡 1 / 卡 2 物理归属地并分别定制状态栏文案。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("授予电话状态权限")
                }
            } else if (state.simCards.isEmpty()) {
                Text(
                    text = "当前设备未检测到实体 SIM 卡或 eSIM。无卡状态下不会产生国内运营商锁区，您仍可在下方预设默认策略。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.simCards.forEach { sim ->
                    SimSlotDetailCard(
                        sim = sim,
                        slotRule = state.userSettings.slotOverrideFor(sim.slotIndex),
                        isWorking = state.isWorking,
                        onQuickSelectRule = { rule ->
                            onUpdateSlotDisplayOverride(sim.slotIndex, rule)
                        },
                        onOpenCustomDialog = { editingSlotSim = sim }
                    )
                }
            }
        }

        // 3. 全局默认策略（供选择“跟随全局”的卡槽继承）
        SectionGroupCard(
            title = "全局默认区域与显标策略",
            subtitle = "未单独设置专属文案的卡槽将自动采用此全局策略（国家码均固定为美国 US）",
            contentPadding = cardPadding
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsCarrierPreset.entries.forEach { preset ->
                    val selected = state.userSettings.selectedCarrier == preset
                    FilterChip(
                        selected = selected,
                        enabled = !state.isWorking,
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
                        value = globalCustomDraft,
                        onValueChange = { globalCustomDraft = it.take(24) },
                        label = { Text("全局自定义运营商文案（如 X）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = !state.isWorking,
                        onClick = { onApplyCustomCarrierLabel(globalCustomDraft) },
                        modifier = Modifier.heightIn(min = 50.dp)
                    ) {
                        Text("应用", softWrap = false, maxLines = 1)
                    }
                }
            }
        }

        // 4. 安全与底层技术特性说明
        SectionGroupCard(
            title = "无损内存级覆盖原理",
            contentPadding = cardPadding
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadgeBox(
                    icon = StatesideIcons.ShieldCheck,
                    tint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    size = 34.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "所有覆盖均采用 Android 系统内存态接口（persistent = false），绝不改写实体 SIM 卡硬件数据；拔卡或重启即恢复出厂原貌。针对中国移动「全球通钻卡 / 白金卡」等 5G SA 核心网 NITZ 显标，内置 3GPP EF_PNN + CDNR 优先裁决穿透机制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // 单卡自由输入定制弹窗
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
            title = { Text("自定义 ${slotTarget.slotLabel} 状态栏文案") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "输入希望在系统状态栏与下拉控制中心呈现的名称（如 X、Zero、Starlink），另一张电话卡不受影响：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it.take(24) },
                        label = { Text("自定义显示名称") },
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimSlotDetailCard(
    sim: SimCardUiModel,
    slotRule: String,
    isWorking: Boolean,
    onQuickSelectRule: (String) -> Unit,
    onOpenCustomDialog: () -> Unit
) {
    val isCustomText = slotRule.isNotBlank() && slotRule != UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (sim.isUsSpoofActive) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBadgeBox(
                        icon = StatesideIcons.SimCard,
                        tint = if (sim.isUsSpoofActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        containerColor = if (sim.isUsSpoofActive) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        },
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "SIM 0${sim.slotIndex + 1} · ${sim.slotLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "物理归属：${sim.realCountryLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusPillBadge(
                    text = if (sim.isUsSpoofActive) "US 生效中" else "原卡状态",
                    containerColor = if (sim.isUsSpoofActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (sim.isUsSpoofActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 实时生效参数双栏展示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimKvPill(
                    label = "对外国家码",
                    value = if (sim.isUsSpoofActive) "美国 (US)" else sim.currentReportedIso,
                    modifier = Modifier.weight(1f)
                )
                SimKvPill(
                    label = "状态栏当前显示",
                    value = sim.currentReportedCarrier,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            // 快捷切换胶囊与自由输入按钮
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when {
                        slotRule == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN -> "${sim.slotLabel}独立规则：保持原运营商"
                        isCustomText -> "${sim.slotLabel}独立规则：固定显示为「$slotRule」"
                        else -> "${sim.slotLabel}独立规则：跟随全局策略"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = slotRule == "X",
                        enabled = !isWorking,
                        onClick = { onQuickSelectRule("X") },
                        label = { Text("设为 X", softWrap = false) }
                    )
                    FilterChip(
                        selected = slotRule == UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN,
                        enabled = !isWorking,
                        onClick = { onQuickSelectRule(UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN) },
                        label = { Text("保持原卡名", softWrap = false) }
                    )
                    FilterChip(
                        selected = slotRule.isBlank(),
                        enabled = !isWorking,
                        onClick = { onQuickSelectRule("") },
                        label = { Text("跟随全局", softWrap = false) }
                    )
                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = onOpenCustomDialog,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Icon(StatesideIcons.EditPen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isCustomText && slotRule != "X") "已设: $slotRule" else "自定义…",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimKvPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
