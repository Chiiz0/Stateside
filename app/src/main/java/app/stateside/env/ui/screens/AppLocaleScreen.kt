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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.ui.CoreSuiteUiItem
import app.stateside.env.ui.ExtraEnglishAppUiItem
import app.stateside.env.ui.MainUiState
import app.stateside.env.ui.components.AppMonogramPlaceholder
import app.stateside.env.ui.components.InstalledAppIcon
import app.stateside.env.ui.components.SectionGroupCard
import app.stateside.env.ui.components.StatesideIcons
import app.stateside.env.ui.components.StatusPillBadge

@Composable
fun AppLocaleScreen(
    state: MainUiState,
    cardPadding: Dp,
    onApplyStateside: () -> Unit,
    onSafeLaunchApp: (String) -> Unit,
    onToggleCoreSuiteApp: (CoreSuiteApp) -> Unit,
    onOpenAppPicker: () -> Unit,
    onRemoveExtraPkg: (String) -> Unit,
    onPinShortcutForApp: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 语言隔离架构状态顶卡
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "应用独立语言沙盒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPillBadge(
                        text = "已锁定 ${state.confirmedEnglishAppsCount}/${state.installedTargetEnglishAppsCount} 款",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocaleCompareTile(
                        title = "手机全局系统",
                        value = "保持中文母语",
                        tag = state.telemetry.systemLocaleTag,
                        highlight = false,
                        modifier = Modifier.weight(1f)
                    )
                    LocaleCompareTile(
                        title = "受控海外与 AI 应用",
                        value = "纯正美式英语",
                        tag = "en-US 隔离",
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.hasPrivilegeChannel && state.unconfirmedEnglishAppsCount > 0) {
                    Button(
                        onClick = onApplyStateside,
                        enabled = !state.isWorking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(StatesideIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即同步 ${state.unconfirmedEnglishAppsCount} 款待生效应用为 en-US")
                    }
                }
            }
        }

        // 2. 预设四大海外与 AI 核心应用套件
        SectionGroupCard(
            title = "核心海外与 AI 套件",
            subtitle = "开箱默认启用，自动识别 Google Play 动态英文分包并固定 en-US",
            contentPadding = cardPadding
        ) {
            state.coreSuiteItems.forEach { item ->
                CoreSuiteEngineCard(
                    item = item,
                    canPinShortcut = state.hasPrivilegeChannel,
                    onToggle = { onToggleCoreSuiteApp(item.suiteApp) },
                    onSafeLaunch = { pkg -> onSafeLaunchApp(pkg) },
                    onPinShortcut = { pkg -> onPinShortcutForApp(pkg, item.suiteApp.title) }
                )
            }
        }

        // 3. 用户自定义扩展英语应用
        SectionGroupCard(
            title = "自定义扩展应用",
            subtitle = "可将任意其他第三方应用加入美式英语驱动与主界面快启坞",
            contentPadding = cardPadding,
            trailing = {
                val count = state.userSettings.extraEnglishPackages.size
                if (count > 0) {
                    StatusPillBadge(
                        text = "$count 款",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        ) {
            if (state.extraAppItems.isEmpty()) {
                Text(
                    text = "暂未添加额外应用。点击下方按钮可从本机已安装应用中自由勾选（如 Reddit、Discord、Perplexity 等）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.extraAppItems.forEach { extra ->
                    ExtraAppEngineCard(
                        extra = extra,
                        canPinShortcut = state.hasPrivilegeChannel,
                        onSafeLaunch = { onSafeLaunchApp(extra.packageName) },
                        onPinShortcut = { onPinShortcutForApp(extra.packageName, extra.appLabel) },
                        onRemove = { onRemoveExtraPkg(extra.packageName) }
                    )
                }
            }

            FilledTonalButton(
                onClick = onOpenAppPicker,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.extraAppItems.isEmpty()) "添加扩展英语应用" else "管理扩展英语应用",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LocaleCompareTile(
    title: String,
    value: String,
    tag: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreSuiteEngineCard(
    item: CoreSuiteUiItem,
    canPinShortcut: Boolean,
    onToggle: () -> Unit,
    onSafeLaunch: (String) -> Unit,
    onPinShortcut: (String) -> Unit
) {
    val installedPkg = item.installedPackage
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (installedPkg != null) {
                    InstalledAppIcon(packageName = installedPkg, size = 42.dp)
                } else {
                    AppMonogramPlaceholder(letter = item.suiteApp.title.take(1), size = 42.dp)
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
                            text = item.suiteApp.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        StatusPillBadge(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = when {
                                installedPkg != null && item.isEnglishConfirmed -> "en-US 已锁定"
                                installedPkg != null && item.isEnabled -> "待同步"
                                installedPkg != null -> "已暂停"
                                else -> "未安装"
                            },
                            containerColor = when {
                                installedPkg != null && item.isEnglishConfirmed -> MaterialTheme.colorScheme.secondaryContainer
                                installedPkg != null && item.isEnabled -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                installedPkg != null && item.isEnglishConfirmed -> MaterialTheme.colorScheme.onSecondaryContainer
                                installedPkg != null && item.isEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = item.suiteApp.roleDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            if (installedPkg != null && item.isEnabled) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionMicroChip(
                        icon = Icons.Default.PlayArrow,
                        label = "安全启动",
                        onClick = { onSafeLaunch(installedPkg) }
                    )
                    if (canPinShortcut) {
                        ActionMicroChip(
                            icon = StatesideIcons.ShieldCheck,
                            label = "桌面防漏改图标",
                            onClick = { onPinShortcut(installedPkg) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraAppEngineCard(
    extra: ExtraEnglishAppUiItem,
    canPinShortcut: Boolean,
    onSafeLaunch: () -> Unit,
    onPinShortcut: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 13.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (extra.isInstalled) {
                    InstalledAppIcon(packageName = extra.packageName, size = 38.dp)
                } else {
                    AppMonogramPlaceholder(letter = extra.appLabel.take(1), size = 38.dp)
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = extra.appLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
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
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "移除扩展应用",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (extra.isInstalled) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionMicroChip(
                        icon = Icons.Default.PlayArrow,
                        label = "安全启动",
                        onClick = onSafeLaunch
                    )
                    if (canPinShortcut) {
                        ActionMicroChip(
                            icon = StatesideIcons.ShieldCheck,
                            label = "桌面防漏改图标",
                            onClick = onPinShortcut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMicroChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}
