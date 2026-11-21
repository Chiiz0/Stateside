package app.stateside.env.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.components.IconBadgeBox
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentInspectorSheet(
    state: MainUiState,
    onBuildDiagnostics: () -> String,
    onCopiedNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        icon = StatesideIcons.RadarScan,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "环境透视与合规自检",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "实时读取海外与 AI 应用关注的底层设备维度（全程脱敏，不含卡号或 IMSI）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. SIM 国家码与显标维度
                SectionGroupCard(
                    title = "1. SIM 卡区域与显标参数",
                    trailing = {
                        val simOk = state.areSimsAlreadyReportingUs || (state.simCards.isEmpty() && state.isEnvironmentReady)
                        StatusPillBadge(
                            text = if (simOk) "US 已就绪" else "待切换",
                            containerColor = if (simOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (simOk) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) {
                    if (state.simCards.isEmpty()) {
                        Text(
                            text = if (state.hasPhonePermission) {
                                "当前设备未插入实体 SIM / eSIM，不会向应用暴露国内运营商国家码。"
                            } else {
                                "尚未授予电话状态权限，无法读取实时卡槽参数。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.simCards.forEachIndexed { idx, sim ->
                            if (idx > 0) HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${sim.slotLabel}（物理归属：${sim.realCountryLabel}）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "SIM ISO: ${sim.currentReportedIso}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sim.isUsSpoofActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "状态栏裁决名称：${sim.currentReportedCarrier}  ·  基站注册 ISO：${sim.networkCountryIso}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. 应用语言隔离维度
                SectionGroupCard(
                    title = "2. 系统与应用语言沙盒",
                    trailing = {
                        StatusPillBadge(
                            text = "${state.confirmedEnglishAppsCount}/${state.installedTargetEnglishAppsCount} 款已锁定",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                ) {
                    InspectorKeyValueRow(
                        label = "手机系统全局语言",
                        value = "${state.telemetry.systemLocaleTag}（保持母语不变）"
                    )
                    InspectorKeyValueRow(
                        label = "受控海外/AI 应用语言",
                        value = "en-US（Android 原生 ILocaleManager 独立隔离）"
                    )
                }

                // 3. 系统时区维度（附快捷跳转）
                SectionGroupCard(
                    title = "3. 系统时区与区域线索",
                    trailing = {
                        StatusPillBadge(
                            text = if (state.telemetry.isUsTimezone) "美区时区" else "本地时区",
                            containerColor = if (state.telemetry.isUsTimezone) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (state.telemetry.isUsTimezone) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                ) {
                    InspectorKeyValueRow(
                        label = "当前时区 ID",
                        value = "${state.telemetry.systemTimezoneId} (${state.telemetry.systemTimezoneOffset})"
                    )
                    Text(
                        text = "提示：绝大多数 AI 与社交应用无需更改手机时区；若个别严格风控场景需对齐美西/美东时间，可按需前往系统设置调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { ChineseRomGuide.openDateTimeSettings(context) }
                    ) {
                        Text("打开系统「日期与时间」设置")
                    }
                }

                // 4. 特权执行通道维度
                SectionGroupCard(title = "4. 本机特权执行器状态") {
                    InspectorKeyValueRow(
                        label = "当前生效通道",
                        value = state.privilegeChannelTitle
                    )
                    InspectorKeyValueRow(
                        label = "设备与系统环境",
                        value = "${state.telemetry.deviceModel} · ${state.telemetry.androidRelease}"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val summary = onBuildDiagnostics()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Stateside Diagnostics", summary))
                            onCopiedNotice("已复制脱敏诊断摘要到剪贴板")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(StatesideIcons.Copy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("复制诊断摘要", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun InspectorKeyValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.62f)
        )
    }
}
