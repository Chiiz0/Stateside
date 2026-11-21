package app.stateside.env.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.ui.DockAppUiItem
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.components.IconBadgeBox
import app.stateside.env.ui.components.InstalledAppIcon
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge

private data class CapabilityModuleSpec(
    val title: String,
    val liveStatus: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val containerColor: Color,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    state: MainUiState,
    cardPadding: Dp,
    onRequestPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onApplyStateside: () -> Unit,
    onOpenRestoreConfirm: () -> Unit,
    onSafeLaunchApp: (String) -> Unit,
    onNavigateToSimStudio: () -> Unit,
    onNavigateToAppEngine: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenEnvironmentInspector: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 权限缺省提醒条（全齐后自动隐藏，保持日常界面极致清爽）
        if (!state.hasAllRecommendedPermissions) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "基础权限待补齐 (${state.grantedPermissionCount}/3)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "用于实时核验双卡 US 状态与下拉通知栏无线配对",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onRequestPermissions,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("一键授权", softWrap = false, maxLines = 1)
                    }
                }
            }
        }

        // 2. 顶部旗舰主控舱（深海蓝宝石仪表舱）
        HeroCockpitCard(
            state = state,
            cardPadding = cardPadding,
            onOpenOnboarding = {
                onRequestPermissions()
                onOpenOnboarding()
            },
            onApplyStateside = onApplyStateside,
            onOpenRestoreConfirm = onOpenRestoreConfirm,
            onOpenInspector = onOpenEnvironmentInspector
        )

        // 3. 安全快启坞（日常高频一触即达）
        val dockItems = state.safeLaunchDockItems
        if (dockItems.isNotEmpty()) {
            SectionGroupCard(
                title = "安全快启坞",
                subtitle = "启动前毫秒级自检美区状态，掉线自动补写后再拉起",
                contentPadding = cardPadding,
                trailing = {
                    TextButton(
                        onClick = onNavigateToAppEngine,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("管理应用", softWrap = false, maxLines = 1)
                    }
                }
            ) {
                SafeLaunchDockGrid(
                    dockItems = dockItems,
                    isEnvironmentReady = state.isEnvironmentReady,
                    onSafeLaunchApp = onSafeLaunchApp
                )
            }
        }

        // 4. 可扩展「能力模块矩阵」（Bento 模块化架构，便于后续持续扩展新能力）
        val simLiveLabel = when {
            state.simCards.isEmpty() -> "免卡 / 待读取"
            else -> state.simCards.joinToString(" · ") { "${it.slotLabel} ${it.currentReportedCarrier}" }
        }
        val capabilityModules = listOf(
            CapabilityModuleSpec(
                title = "号卡显标工作坊",
                liveStatus = if (state.areSimsAlreadyReportingUs) "双卡 US 生效" else "点击配置",
                description = simLiveLabel,
                icon = StatesideIcons.SimCard,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                onClick = onNavigateToSimStudio
            ),
            CapabilityModuleSpec(
                title = "应用英语沙盒",
                liveStatus = "${state.confirmedEnglishAppsCount}/${state.installedTargetEnglishAppsCount} 款就绪",
                description = "系统中文 · 应用独立 en-US",
                icon = StatesideIcons.GlobeLanguage,
                accentColor = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                onClick = onNavigateToAppEngine
            ),
            CapabilityModuleSpec(
                title = "环境透视自检",
                liveStatus = state.telemetry.systemTimezoneOffset,
                description = "国家码 · 语言 · 时区核验",
                icon = StatesideIcons.RadarScan,
                accentColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                onClick = onOpenEnvironmentInspector
            ),
            CapabilityModuleSpec(
                title = "自愈与通道设置",
                liveStatus = if (state.userSettings.autoRestoreOnBoot) "开机自愈开启" else "手动触发",
                description = state.privilegeChannelTitle,
                icon = StatesideIcons.ShieldCheck,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = onNavigateToSettings
            )
        )

        SectionGroupCard(
            title = "核心能力矩阵",
            subtitle = "点击任一模块进入专项控制台或运行环境诊断",
            contentPadding = cardPadding
        ) {
            capabilityModules.chunked(2).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSpecs.forEach { spec ->
                        CapabilityBentoTile(
                            spec = spec,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowSpecs.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroCockpitCard(
    state: MainUiState,
    cardPadding: Dp,
    onOpenOnboarding: () -> Unit,
    onApplyStateside: () -> Unit,
    onOpenRestoreConfirm: () -> Unit,
    onOpenInspector: () -> Unit
) {
    val gradientColors = if (state.isEnvironmentReady) {
        listOf(Color(0xFF081A36), Color(0xFF0C2A4D), Color(0xFF07152B))
    } else {
        listOf(Color(0xFF0A1428), Color(0xFF132347), Color(0xFF081021))
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (state.isEnvironmentReady) Color(0xFF34D399).copy(alpha = 0.42f) else Color(0xFF38BDF8).copy(alpha = 0.28f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(cardPadding + 2.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 顶部状态胶囊行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPillBadge(
                        text = when {
                            state.isEnvironmentReady -> "美境运行中 · US"
                            !state.hasPrivilegeChannel -> "首次使用 · 待绑定"
                            else -> "待一键开启"
                        },
                        containerColor = if (state.isEnvironmentReady) {
                            Color(0xFF10B981).copy(alpha = 0.22f)
                        } else {
                            Color(0xFF38BDF8).copy(alpha = 0.18f)
                        },
                        contentColor = if (state.isEnvironmentReady) Color(0xFF6EE7B7) else Color(0xFFBAE6FD),
                        dotColor = if (state.isEnvironmentReady) Color(0xFF34D399) else Color(0xFF38BDF8)
                    )

                    StatusPillBadge(
                        text = state.privilegeChannelTitle,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color(0xFFE2E8F0)
                    )
                }

                // 主标题与副文案
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = when {
                            state.isEnvironmentReady && state.hasPrivilegeChannel -> "美区环境已全域就绪"
                            state.isEnvironmentReady && !state.hasPrivilegeChannel -> "美境已生效 · 建议绑定本机通道"
                            !state.hasPrivilegeChannel -> "欢迎使用美境 · 1 分钟免电脑激活"
                            else -> "随时一键切入美境工作台"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = when {
                            state.isEnvironmentReady && state.unconfirmedEnglishAppsCount > 0 ->
                                "检测到 ${state.unconfirmedEnglishAppsCount} 款新增目标应用尚未写入 en-US，点击下方按钮立即同步。"
                            state.isEnvironmentReady && state.hasPrivilegeChannel ->
                                "双卡对外上报美国 (US) · 海外与 AI 应用独立运行美式英语 · 上次同步 ${state.lastAppliedFormatted}"
                            state.isEnvironmentReady && !state.hasPrivilegeChannel ->
                                "当前双卡已处于美区状态；绑定一次本机无线调试后，脱离电脑也能随时在手机端切换或还原。"
                            !state.hasPrivilegeChannel ->
                                "免 Root · 免装额外框架。仅需配对一次手机自身无线调试，后续即可独立一键驱动双卡美区与原生英语环境。"
                            else ->
                                "特权通道已就绪，点击下方主按钮即可在 2 秒内完成双卡美区覆盖与应用英语锁定。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }

                // 3 格实时遥测看板
                val simMetric = when {
                    state.simCards.isEmpty() -> if (state.hasPhonePermission) "免插卡" else "待授权"
                    state.areSimsAlreadyReportingUs -> "${state.simCards.size} 张卡 · US"
                    else -> "${state.simCards.size} 张卡 · 原归属"
                }
                val carrierMetric = when {
                    state.simCards.isEmpty() -> state.userSettings.resolveTargetCarrierLabelForSlot(0) ?: "原运营商"
                    else -> state.simCards.joinToString(" / ") { it.currentReportedCarrier }
                }
                val appMetric = "${state.confirmedEnglishAppsCount}/${state.installedTargetEnglishAppsCount} 已锁定"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CockpitMetricTile(
                        label = "号卡国家码",
                        value = simMetric,
                        modifier = Modifier.weight(1f)
                    )
                    CockpitMetricTile(
                        label = "状态栏显标",
                        value = carrierMetric,
                        modifier = Modifier.weight(1f)
                    )
                    CockpitMetricTile(
                        label = "英语应用",
                        value = appMetric,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 主操作按钮与辅助快捷动作
                if (state.isWorking) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF67E8F9),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.4.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = state.workingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (!state.hasPrivilegeChannel) {
                    Button(
                        onClick = onOpenOnboarding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF061529)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(StatesideIcons.TerminalLink, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isEnvironmentReady) "绑定本机无线调试通道" else "开始 1 分钟首次激活",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onApplyStateside,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isEnvironmentReady) Color(0xFF34D399) else Color(0xFF38BDF8),
                                contentColor = Color(0xFF041A14)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) {
                            Icon(StatesideIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    state.isEnvironmentReady && state.unconfirmedEnglishAppsCount > 0 ->
                                        "同步新应用 (${state.unconfirmedEnglishAppsCount})"
                                    state.isEnvironmentReady -> "重新同步美境"
                                    else -> "一键开启美境"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (state.isEnvironmentReady) {
                            OutlinedButton(
                                onClick = onOpenRestoreConfirm,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Icon(StatesideIcons.RestoreReset, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("还原", softWrap = false, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onOpenInspector,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text("透视", softWrap = false, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SafeLaunchDockGrid(
    dockItems: List<DockAppUiItem>,
    isEnvironmentReady: Boolean,
    onSafeLaunchApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        dockItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowItems.forEach { item ->
                    Surface(
                        onClick = { onSafeLaunchApp(item.packageName) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InstalledAppIcon(
                                packageName = item.packageName,
                                size = 34.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isEnvironmentReady && item.isEnglishConfirmed) "en-US 秒开" else "校验后打开",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnvironmentReady && item.isEnglishConfirmed) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
private fun CapabilityBentoTile(
    spec: CapabilityModuleSpec,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = spec.onClick)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadgeBox(
                    icon = spec.icon,
                    tint = spec.accentColor,
                    containerColor = spec.containerColor,
                    size = 34.dp,
                    iconSize = 18.dp
                )
                Icon(
                    imageVector = StatesideIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spec.liveStatus,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = spec.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spec.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
