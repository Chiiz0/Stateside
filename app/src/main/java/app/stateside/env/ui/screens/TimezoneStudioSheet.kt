package app.stateside.env.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.TimezoneMode
import app.stateside.env.model.UsTimezonePreset
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.components.IconBadgeBox
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimezoneStudioSheet(
    state: MainUiState,
    onSelectMode: (TimezoneMode) -> Unit,
    onSelectPreset: (UsTimezonePreset) -> Unit,
    onToggleCompanionSuite: (CoreSuiteApp) -> Unit,
    onToggleCompanionExtraPkg: (String, String) -> Unit,
    onSwitchUsTimezoneNow: () -> Unit,
    onRestoreHomeTimezoneNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = Date()

    val currentClock = formatClockInZone(now, state.telemetry.systemTimezoneId)
    val targetPreset = state.userSettings.selectedUsTimezone
    val targetClock = formatClockInZone(now, targetPreset.zoneId)
    val targetOffset = formatUtcOffset(targetPreset.zoneId, now.time)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPad = if (maxWidth < 365.dp) 14.dp else 20.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPad, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadgeBox(
                        icon = StatesideIcons.ClockTimezone,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "美区时区引擎",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "支持全局同步切时区（方案一）或单应用随开随切、退出即还（方案二）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. 双时区实时时钟对照条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shortCurrentZone = state.telemetry.systemTimezoneId.substringAfterLast('/')
                    ClockPreviewTile(
                        caption = "当前系统生效时区",
                        timeText = currentClock,
                        zoneSub = "$shortCurrentZone (${state.telemetry.systemTimezoneOffset})",
                        highlighted = state.telemetry.isUsTimezone,
                        modifier = Modifier.weight(1f)
                    )
                    ClockPreviewTile(
                        caption = "目标美区时区预览",
                        timeText = targetClock,
                        zoneSub = "${targetPreset.cityLabel} ($targetOffset)",
                        highlighted = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2. 三种工作模式单选卡片
                SectionGroupCard(
                    title = "选择时区工作策略",
                    contentPadding = 14.dp
                ) {
                    TimezoneMode.entries.forEach { mode ->
                        val selected = state.userSettings.timezoneMode == mode
                        TimezoneModeOptionCard(
                            mode = mode,
                            selected = selected,
                            enabled = !state.isWorking,
                            onClick = { onSelectMode(mode) }
                        )
                    }

                    // 选中方案二时展开「触发伴随时区的应用清单」
                    AnimatedVisibility(visible = state.userSettings.timezoneMode == TimezoneMode.COMPANION_APP) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "勾选需要「随开随切」的目标应用",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    StatusPillBadge(
                                        text = "已选 ${state.enabledCompanionAppsCount} 款",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.coreSuiteItems.forEach { item ->
                                        FilterChip(
                                            selected = item.isCompanionTzEnabled,
                                            onClick = { onToggleCompanionSuite(item.suiteApp) },
                                            label = { Text(item.suiteApp.title, softWrap = false) },
                                            leadingIcon = if (item.isCompanionTzEnabled) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                    state.extraAppItems.forEach { extra ->
                                        FilterChip(
                                            selected = extra.isCompanionTzEnabled,
                                            onClick = { onToggleCompanionExtraPkg(extra.packageName, extra.appLabel) },
                                            label = {
                                                Text(
                                                    text = extra.appLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            leadingIcon = if (extra.isCompanionTzEnabled) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }

                                Text(
                                    text = "使用方式：从美境首页「安全快启坞」或桌面「· 美境」防漏改图标启动上述应用时，将在进程启动前自动切入美区时区；退出应用回桌面约 4 秒后自动归位原时区（${state.userSettings.savedHomeTimezoneId}）。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. 目标美国时区预设
                SectionGroupCard(
                    title = "目标美国时区预设",
                    subtitle = "方案一与方案二共用此目标时区（默认推荐硅谷/美西洛杉矶时区）",
                    contentPadding = 14.dp
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UsTimezonePreset.entries.forEach { preset ->
                            val selected = state.userSettings.selectedUsTimezone == preset
                            FilterChip(
                                selected = selected,
                                enabled = !state.isWorking,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.chipLabel, softWrap = false) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // 4. 底部手动即时控制与关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.telemetry.isUsTimezone || state.userSettings.isTimezoneOverridden) {
                        OutlinedButton(
                            enabled = !state.isWorking && state.hasPrivilegeChannel,
                            onClick = onRestoreHomeTimezoneNow,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            Icon(StatesideIcons.RestoreReset, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("立即归位原时区", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (state.userSettings.timezoneMode == TimezoneMode.GLOBAL_US && state.hasPrivilegeChannel) {
                        Button(
                            enabled = !state.isWorking,
                            onClick = onSwitchUsTimezoneNow,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            Text("立即切至美区时区", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text("完成", maxLines = 1)
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ClockPreviewTile(
    caption: String,
    timeText: String,
    zoneSub: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = zoneSub,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TimezoneModeOptionCard(
    mode: TimezoneMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatClockInZone(date: Date, zoneId: String): String = runCatching {
    SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(date)
}.getOrDefault("--:--")

private fun formatUtcOffset(zoneId: String, epochMillis: Long): String {
    val tz = TimeZone.getTimeZone(zoneId)
    val offsetMillis = tz.getOffset(epochMillis)
    val totalMinutes = abs(offsetMillis) / 60_000
    val sign = if (offsetMillis >= 0) "+" else "-"
    return String.format(Locale.US, "UTC%s%02d:%02d", sign, totalMinutes / 60, totalMinutes % 60)
}
