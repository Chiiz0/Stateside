package app.stateside.env.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.stateside.env.StatesideApp
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
import app.stateside.env.timezone.CompanionTimezoneService
import app.stateside.env.ui.components.StatesideBrandEmblem
import app.stateside.env.ui.theme.StatesideTheme
import kotlinx.coroutines.launch

/**
 * 通用桌面「美境安全启动」透明守卫页：
 * 1. 若美区电话卡、en-US 与伴随时区均已就绪，0.1 秒无感直达目标应用；
 * 2. 若需补写美区电话卡或切换「随开随切」动态伴随时区，当场自动完成后再拉起目标应用。
 */
class SafeLaunchActivity : ComponentActivity() {

    private var statusMessage by mutableStateOf("正在快速核验美境状态…")
    private var errorReason by mutableStateOf<String?>(null)

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedPrefs = (application as StatesideApp).prefs.settings.value
        val targetPkg = intent?.getStringExtra(AppLaunchGuardHelper.EXTRA_TARGET_PKG)
            ?.takeIf { it.isNotBlank() }
            ?: CoreSuiteApp.entries.firstNotNullOfOrNull {
                AppLaunchGuardHelper.resolveInstalledPackage(this, it)
            }

        val targetTitle = intent?.getStringExtra(AppLaunchGuardHelper.EXTRA_TARGET_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "目标应用"

        val needsCompanionTz = !targetPkg.isNullOrBlank() &&
            savedPrefs.shouldSwitchCompanionTimezoneOnLaunch(targetPkg)

        if (AppLaunchGuardHelper.isStatesideReadyForFastLaunch(this, targetPkg)) {
            if (needsCompanionTz) {
                CompanionTimezoneService.startSession(
                    context = applicationContext,
                    targetPackage = targetPkg,
                    appTitle = targetTitle,
                    zoneLabel = savedPrefs.selectedUsTimezone.cityLabel
                )
            }
            val launched = targetPkg != null && AppLaunchGuardHelper.launchPackageDirectly(this, targetPkg)
            if (!launched) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
            return
        }

        setContent {
            StatesideTheme(
                themeMode = savedPrefs.themeMode,
                dynamicColor = savedPrefs.useDynamicColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatesideBrandEmblem(size = 32.dp, isActive = false)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "美境安全启动 · $targetTitle",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (errorReason == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Text(
                                    text = errorReason.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = { finish() }) {
                                        Text("取消", softWrap = false, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            startActivity(
                                                Intent(this@SafeLaunchActivity, MainActivity::class.java)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                            finish()
                                        }
                                    ) {
                                        Text("主面板", softWrap = false, maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            ChineseRomGuide.openDeveloperOptionsOrAboutPhone(this@SafeLaunchActivity)
                                            finish()
                                        }
                                    ) {
                                        Text("打开无线调试", softWrap = false, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            val baseEnvReadyWithoutCompanion = AppLaunchGuardHelper.isStatesideReadyForFastLaunch(
                this@SafeLaunchActivity,
                targetPackage = null
            ) && (targetPkg == null ||
                targetPkg !in savedPrefs.effectiveEnglishPackages() ||
                targetPkg in savedPrefs.confirmedEnglishPackages)

            val result = if (baseEnvReadyWithoutCompanion && needsCompanionTz) {
                statusMessage = "正在为 $targetTitle 切换美区伴随时区 (${savedPrefs.selectedUsTimezone.cityLabel})…"
                LocalAdbManager.getInstance(applicationContext).runEnvironmentTask(
                    action = PrivilegedAction.SWITCH_US_TIMEZONE,
                    explicitTimezoneId = savedPrefs.selectedUsTimezone.zoneId
                )
            } else {
                statusMessage = "正在同步美境配置与环境后再为您打开 $targetTitle…"
                LocalAdbManager.getInstance(applicationContext).runEnvironmentTask(
                    action = PrivilegedAction.APPLY_US_ENV,
                    explicitTimezoneId = if (needsCompanionTz) savedPrefs.selectedUsTimezone.zoneId else null
                )
            }

            if (result.isSuccess) {
                if (needsCompanionTz) {
                    CompanionTimezoneService.startSession(
                        context = applicationContext,
                        targetPackage = targetPkg,
                        appTitle = targetTitle,
                        zoneLabel = savedPrefs.selectedUsTimezone.cityLabel
                    )
                }
                if (targetPkg == null || !AppLaunchGuardHelper.launchPackageDirectly(this@SafeLaunchActivity, targetPkg)) {
                    startActivity(Intent(this@SafeLaunchActivity, MainActivity::class.java))
                }
                finish()
            } else {
                errorReason = "未能自动完成 $targetTitle 启动前美境同步：${result.exceptionOrNull()?.message ?: "无线调试未开启"}。"
            }
        }
    }
}
