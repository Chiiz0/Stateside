package app.stateside.env.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.BuildConfig
import app.stateside.env.model.AppThemeMode
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.SettingsActionRow
import app.stateside.env.ui.components.SettingsSwitchRow
import app.stateside.env.ui.components.StatesideBrandEmblem
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    cardPadding: Dp,
    onRequestPermissions: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenInspector: () -> Unit,
    onForceReapply: () -> Unit,
    onOpenRestoreConfirm: () -> Unit,
    onToggleAutoBoot: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSaveManualPort: (String) -> Unit,
    onResetPairingState: () -> Unit,
    onBuildDiagnostics: () -> String,
    onNotifyBanner: (String) -> Unit
) {
    val context = LocalContext.current
    var showManualPortDialog by remember { mutableStateOf(false) }
    var showResetPairingConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 特权通道与无线调试管理
        SectionGroupCard(
            title = "特权通道与连接",
            subtitle = "管理 127.0.0.1 本机无线调试绑定与免电脑执行通道",
            contentPadding = cardPadding
        ) {
            SettingsActionRow(
                icon = StatesideIcons.TerminalLink,
                title = "无线调试配对与激活向导",
                subtitle = when {
                    state.hasRootAccess -> "已检测到 Root 直通权限，免无线调试配对"
                    state.userSettings.isAdbPaired && state.userSettings.lastConnectPort > 0 ->
                        "本机密钥已永久绑定 · 最近连接端口 ${state.userSettings.lastConnectPort}"
                    state.userSettings.isAdbPaired -> "本机无线调试密钥已绑定，点击可查看向导或重连"
                    else -> "首次使用或更换系统后点此按步骤完成 6 位码配对"
                },
                badgeText = when {
                    state.hasRootAccess -> "Root"
                    state.userSettings.isAdbPaired -> "已绑定"
                    else -> "待配对"
                },
                badgeContainerColor = if (state.hasPrivilegeChannel) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                badgeContentColor = if (state.hasPrivilegeChannel) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                onClick = onOpenOnboarding
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.ExternalOpen,
                title = "跳转系统「无线调试」设置",
                subtitle = if (state.telemetry.isDeveloperOptionsEnabled) {
                    "开发者选项已开启，点击直达系统无线调试开关"
                } else {
                    "开发者选项尚未开启，点击跳转关于本机解锁"
                },
                onClick = {
                    ChineseRomGuide.openDeveloperOptionsOrAboutPhone(context, highlightWirelessAdb = true)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.SlidersTune,
                title = "高级固定连接端口",
                subtitle = if (state.userSettings.manualConnectPort > 0) {
                    "当前手动指定端口：${state.userSettings.manualConnectPort}（适合 TCP 5555 或校园网隔离场景）"
                } else {
                    "自动发现本机无线调试端口（默认推荐）"
                },
                onClick = { showManualPortDialog = true }
            )

            if (state.userSettings.isAdbPaired) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsActionRow(
                    icon = StatesideIcons.RestoreReset,
                    title = "重置本机配对状态记录",
                    subtitle = "若曾在系统开发者选项中执行“撤销无线调试授权”，可在此清除旧状态后重新配对",
                    showChevron = false,
                    onClick = { showResetPairingConfirm = true }
                )
            }
        }

        // 2. 自动化、权限与厂商保活
        SectionGroupCard(
            title = "无感守护与系统适配",
            subtitle = "已自动识别当前系统：${state.romGuide.brandTitle}",
            contentPadding = cardPadding
        ) {
            SettingsSwitchRow(
                icon = StatesideIcons.Bolt,
                title = "重启连上 Wi-Fi 后自动恢复美境",
                subtitle = "手机重启后一旦连接已授权 Wi-Fi 自动补写美区国家码（应用英语配置永久保留）",
                checked = state.userSettings.autoRestoreOnBoot,
                onCheckedChange = onToggleAutoBoot
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.ShieldCheck,
                title = "运行时权限自检",
                subtitle = "双卡读取 (${if (state.hasPhonePermission) "已授" else "未授"}) · 通知栏配对 (${if (state.hasNotificationPermission) "已授" else "未授"}) · 本地网络 (${if (state.hasLocalNetworkPermission) "已授" else "未授"})",
                badgeText = "${state.grantedPermissionCount}/3",
                badgeContainerColor = if (state.hasAllRecommendedPermissions) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                badgeContentColor = if (state.hasAllRecommendedPermissions) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                onClick = {
                    if (state.hasAllRecommendedPermissions) {
                        ChineseRomGuide.openAppDetailsSettings(context)
                    } else {
                        onRequestPermissions()
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.ExternalOpen,
                title = "后台耗电与保活管理",
                subtitle = state.romGuide.keepAliveTip,
                onClick = { ChineseRomGuide.openAppDetailsSettings(context) }
            )
        }

        // 3. 外观与个性化
        SectionGroupCard(
            title = "外观与个性化",
            subtitle = "定制深浅色视觉主题与色彩风格",
            contentPadding = cardPadding
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "深浅色模式",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.userSettings.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppThemeMode.entries.size
                            )
                        ) {
                            Text(text = mode.label, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsSwitchRow(
                icon = StatesideIcons.Palette,
                title = "壁纸动态取色 (Material You)",
                subtitle = "关闭时使用美境专属深海蓝与极光青精密配色；开启后跟随系统壁纸主色调",
                checked = state.userSettings.useDynamicColor,
                onCheckedChange = onToggleDynamicColor
            )
        }

        // 4. 环境维护与安全还原
        SectionGroupCard(
            title = "环境维护与安全还原",
            contentPadding = cardPadding
        ) {
            SettingsActionRow(
                icon = Icons.Default.Refresh,
                title = "强制全量重刷美境环境",
                subtitle = "重新向系统注入双卡美区参数与受控应用的 en-US 语言配置",
                enabled = !state.isWorking && state.hasPrivilegeChannel,
                onClick = onForceReapply
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.RadarScan,
                title = "环境透视自检",
                subtitle = "查看 SIM 国家码、基站 ISO、语言沙盒与系统时区完整核验详情",
                onClick = onOpenInspector
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.Copy,
                title = "复制脱敏诊断摘要",
                subtitle = "一键导出机型、系统版本、通道状态与双卡摘要（不含手机号或隐私标识）",
                showChevron = false,
                onClick = {
                    val summary = onBuildDiagnostics()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Stateside Diagnostics", summary))
                    onNotifyBanner("已复制脱敏诊断摘要到剪贴板")
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsActionRow(
                icon = StatesideIcons.RestoreReset,
                title = "一键恢复国内默认状态",
                subtitle = "还原所有电话卡真实归属地与原运营商显示，并将受控应用恢复跟随系统中文",
                iconTint = MaterialTheme.colorScheme.error,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                titleColor = MaterialTheme.colorScheme.error,
                enabled = !state.isWorking,
                showChevron = false,
                onClick = onOpenRestoreConfirm
            )
        }

        // 5. 关于美境品牌展示卡
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatesideBrandEmblem(
                        size = 50.dp,
                        isActive = state.isEnvironmentReady
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "美境 · Stateside",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android 14–17",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPillBadge(
                        text = "127.0.0.1 纯本机回环",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPillBadge(
                        text = "零外网请求",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPillBadge(
                        text = "内存态无损可逆",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsActionRow(
                    icon = StatesideIcons.ExternalOpen,
                    title = "GitHub 开源项目主页",
                    subtitle = "github.com/Chiiz0/Stateside · 查看架构文档与最新版本",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Chiiz0/Stateside"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showManualPortDialog) {
        var portDraft by remember(state.userSettings.manualConnectPort) {
            mutableStateOf(
                if (state.userSettings.manualConnectPort > 0) {
                    state.userSettings.manualConnectPort.toString()
                } else {
                    ""
                }
            )
        }
        AlertDialog(
            onDismissRequest = { showManualPortDialog = false },
            title = { Text("高级固定连接端口") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "默认留空即可自动扫描识别本机无线调试端口。若您使用固定 TCP 5555 或特殊网络隔离环境，可手动指定端口（1024–65535）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = portDraft,
                        onValueChange = { portDraft = it.filter(Char::isDigit).take(5) },
                        label = { Text("连接端口（留空恢复自动发现）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveManualPort(portDraft)
                        showManualPortDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPortDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showResetPairingConfirm) {
        AlertDialog(
            onDismissRequest = { showResetPairingConfirm = false },
            title = { Text("重置本机配对状态？") },
            text = {
                Text("重置后界面将回到待配对状态（若系统无线调试内仍保留 Stateside 授权记录，直接点击开启美境仍可自动重连）。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetPairingConfirm = false
                        onResetPairingState()
                    }
                ) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPairingConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
