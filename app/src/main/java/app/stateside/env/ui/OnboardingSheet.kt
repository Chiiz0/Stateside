package app.stateside.env.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stateside.env.adb.AdbPairingService
import app.stateside.env.adb.PairingUiPhase
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.oem.RomSetupGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    romGuide: RomSetupGuide,
    pairingPhase: PairingUiPhase,
    hasNotificationPermission: Boolean,
    savedManualConnectPort: Int,
    isWorking: Boolean,
    onRequestPermissions: () -> Unit,
    onConnectManualPortAndApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var manualCode by remember { mutableStateOf("") }
    var showAdvancedPortPanel by remember { mutableStateOf(false) }
    var manualPairingPort by remember { mutableStateOf("") }
    var manualPairingCode by remember { mutableStateOf("") }
    var manualConnectPort by remember {
        mutableStateOf(if (savedManualConnectPort > 0) savedManualConnectPort.toString() else "")
    }
    var devEnabled by remember { mutableStateOf(ChineseRomGuide.isDeveloperOptionsEnabled(context)) }

    // 用户从系统「关于手机 / 开发者选项」返回时实时刷新解锁状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                devEnabled = ChineseRomGuide.isDeveloperOptionsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "美境 1 分钟首次激活向导（免电脑 · 免 Root）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "已自动识别您的手机：${romGuide.brandTitle}。只需首次完成一次本机无线调试绑定，后续即可一键驱动美区电话卡与 TikTok、X、Claude、ChatGPT 英语环境。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasNotificationPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "请先允许通知权限",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "开启通知后，在系统设置页面直接下拉通知栏即可填入 6 位配对码（无需切回本应用导致弹窗消失）。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                onRequestPermissions()
                                ChineseRomGuide.openAppNotificationSettings(context)
                            }
                        ) {
                            Text("去开启")
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = romGuide.criticalExtraToggleTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = romGuide.criticalExtraToggleDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            StepGuideCard(
                stepNumber = "1",
                title = if (devEnabled) "开发者选项已开启" else "开启「开发者选项」",
                description = if (devEnabled) {
                    "系统检测到您已开启开发者选项，点击下方主按钮将自动定位至「无线调试」。"
                } else {
                    "若尚未开启，点击下方按钮进入系统页面，按「${romGuide.buildNumberPath}」即可解锁。"
                }
            )

            StepGuideCard(
                stepNumber = "2",
                title = "点开「无线调试 → 使用配对码配对设备」",
                description = "连接任意 Wi-Fi 后打开「无线调试」开关（勾选“始终允许在此网络”），再点「使用配对码配对设备」。"
            )

            StepGuideCard(
                stepNumber = "3",
                title = "直接在下拉通知栏输入 6 位配对码",
                description = "看到屏幕上的 6 位数字后保持弹窗不关，直接下拉顶部通知栏点「提交配对码」输入即可自动完成激活。"
            )

            PairingRadarPanel(
                phase = pairingPhase,
                manualCode = manualCode,
                onManualCodeChange = { if (it.length <= 6) manualCode = it.filter(Char::isDigit) },
                onSubmitManualCode = { port ->
                    AdbPairingService.submitCodeFromUi(context, port, manualCode)
                },
                onDone = onDismiss
            )

            Button(
                onClick = {
                    AdbPairingService.startWatching(context)
                    ChineseRomGuide.openDeveloperOptionsOrAboutPhone(context, highlightWirelessAdb = true)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (devEnabled) "启动配对雷达并跳转「无线调试」" else "去开启「开发者选项」并启动雷达",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 高级兜底面板：解决校园网 AP 隔离 / VPN tun 拦截组播或已配对设备一键重连
            TextButton(
                onClick = { showAdvancedPortPanel = !showAdvancedPortPanel },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showAdvancedPortPanel) {
                        "收起高级连接选项"
                    } else {
                        "校园网 / VPN 未自动识别端口，或此前已完成配对？点此手动连接"
                    }
                )
            }

            AnimatedVisibility(visible = showAdvancedPortPanel) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "方式 A：分屏/小窗手动填写配对端口与 6 位配对码",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualPairingPort,
                                onValueChange = { manualPairingPort = it.filter(Char::isDigit).take(5) },
                                label = { Text("配对端口") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = manualPairingCode,
                                onValueChange = { manualPairingCode = it.filter(Char::isDigit).take(6) },
                                label = { Text("6位配对码") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            enabled = manualPairingPort.length in 4..5 && manualPairingCode.length == 6,
                            onClick = {
                                val p = manualPairingPort.toIntOrNull() ?: 0
                                AdbPairingService.submitCodeFromUi(context, p, manualPairingCode)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("使用手动端口立即配对")
                        }

                        HorizontalDivider()

                        Text(
                            text = "方式 B：已配对设备直接连接并开启美境（可选填无线调试主页显示的端口，留空则自动扫描）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualConnectPort,
                                onValueChange = { manualConnectPort = it.filter(Char::isDigit).take(5) },
                                label = { Text("连接端口（可留空自动识别）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = !isWorking,
                                onClick = { onConnectManualPortAndApply(manualConnectPort) }
                            ) {
                                Text("直接连接")
                            }
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭向导")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepGuideCard(
    stepNumber: String,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "0$stepNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PairingRadarPanel(
    phase: PairingUiPhase,
    manualCode: String,
    onManualCodeChange: (String) -> Unit,
    onSubmitManualCode: (Int) -> Unit,
    onDone: () -> Unit
) {
    when (phase) {
        PairingUiPhase.Idle -> Unit

        PairingUiPhase.SearchingPairingDialog -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "后台配对雷达运行中：正在等待您在无线调试里点开「使用配对码配对设备」…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        is PairingUiPhase.ReadyForCode -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "已捕获无线调试弹窗（端口 ${phase.pairingPort}）！可在通知栏输入，也可分屏在下方直接输入：",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = onManualCodeChange,
                            label = { Text("6 位配对码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = manualCode.length == 6,
                            onClick = { onSubmitManualCode(phase.pairingPort) }
                        ) {
                            Text("立即配对")
                        }
                    }
                }
            }
        }

        is PairingUiPhase.PairingInProgress -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在完成配对并自动开启美境（Stateside）…")
                }
            }
        }

        is PairingUiPhase.PairedAndApplied -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("美境首次激活成功！", fontWeight = FontWeight.Bold)
                    }
                    Text(phase.summary, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("返回美境工作台")
                    }
                }
            }
        }

        is PairingUiPhase.Failed -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "提示：${phase.reason}",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
