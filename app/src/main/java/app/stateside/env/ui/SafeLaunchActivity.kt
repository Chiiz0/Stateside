package app.stateside.env.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.oem.ChineseRomGuide
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
import app.stateside.env.ui.theme.StatesideTheme
import kotlinx.coroutines.launch

/**
 * 通用桌面「美境安全启动」透明守卫页：
 * 支持任意目标应用（TikTok / X / Claude / ChatGPT 及自定义扩展应用）：
 * 1. 若美区电话卡环境已生效，0.1 秒无感直达目标应用；
 * 2. 若手机重启后回落国内卡状态，当场拦截并花约 2 秒自动恢复美境后再拉起目标应用。
 */
class SafeLaunchActivity : ComponentActivity() {

    private var statusMessage by mutableStateOf("正在快速核验美境状态…")
    private var errorReason by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPkg = intent?.getStringExtra(AppLaunchGuardHelper.EXTRA_TARGET_PKG)
            ?.takeIf { it.isNotBlank() }
            ?: CoreSuiteApp.entries.firstNotNullOfOrNull {
                AppLaunchGuardHelper.resolveInstalledPackage(this, it)
            }

        val targetTitle = intent?.getStringExtra(AppLaunchGuardHelper.EXTRA_TARGET_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "目标应用"

        if (AppLaunchGuardHelper.isStatesideReadyForFastLaunch(this)) {
            val launched = targetPkg != null && AppLaunchGuardHelper.launchPackageDirectly(this, targetPkg)
            if (!launched) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
            return
        }

        setContent {
            StatesideTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "美境安全拦截 · $targetTitle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (errorReason == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Text(
                                    text = errorReason.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { finish() }) {
                                        Text("取消")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            startActivity(
                                                Intent(this@SafeLaunchActivity, MainActivity::class.java)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                            finish()
                                        }
                                    ) {
                                        Text("主面板")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            ChineseRomGuide.openDeveloperOptionsOrAboutPhone(this@SafeLaunchActivity)
                                            finish()
                                        }
                                    ) {
                                        Text("去打开无线调试")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            statusMessage = "检测到当前尚未处于美区卡状态，正在自动恢复美境再为您打开 $targetTitle…"
            val result = LocalAdbManager.getInstance(applicationContext)
                .runEnvironmentTask(PrivilegedAction.APPLY_US_ENV)

            if (result.isSuccess) {
                if (targetPkg == null || !AppLaunchGuardHelper.launchPackageDirectly(this@SafeLaunchActivity, targetPkg)) {
                    startActivity(Intent(this@SafeLaunchActivity, MainActivity::class.java))
                }
                finish()
            } else {
                errorReason = "已为您拦截国内卡裸奔打开 $targetTitle！当前未能自动恢复美境：${result.exceptionOrNull()?.message ?: "无线调试未开启"}。"
            }
        }
    }
}
