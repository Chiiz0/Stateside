package app.stateside.env.privileged

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import app.stateside.env.StatesideApp
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.UsCarrierPreset
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 美境（Stateside）单进程一体化特权批处理入口：
 * 运行在 app.stateside.env 自身进程 UID 下，借用 UiAutomation.adoptShellPermissionIdentity() 一次性完成：
 * 1. 双卡美国运营商 (US) 覆盖与读回核验；
 * 2. TikTok、X、Claude、ChatGPT 及扩展应用组强制设为美国英语 en-US。
 */
class StatesideEnvInstrumentation : Instrumentation() {

    private var arguments: Bundle = Bundle.EMPTY

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle.EMPTY
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        start()
    }

    override fun onStart() {
        super.onStart()
        val ctx = targetContext
        val actionName = arguments.getString("action", PrivilegedAction.APPLY_US_ENV.name)
        val action = runCatching { PrivilegedAction.valueOf(actionName) }
            .getOrDefault(PrivilegedAction.APPLY_US_ENV)
        val carrierArg = arguments.getString("carrier_id")?.takeIf { it.isNotBlank() }
        val cliCarrier = carrierArg?.let { UsCarrierPreset.fromId(it) }
        val cliSlot0Label = arguments.getString("slot0_label")
        val cliSlot1Label = arguments.getString("slot1_label")
        val savedSettings = UserPrefsRepository(ctx).settings.value
        val targetPkgs = arguments.getString("target_pkgs")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .ifEmpty { CoreSuiteApp.ALL_BUILTIN_PACKAGES.toList() }

        var uiAutomation: UiAutomation? = null
        val report = try {
            // 使用 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES 避免中断用户手机上的无障碍服务（如李跳跳/GKD/密码管理器）
            uiAutomation = runCatching {
                getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            }.getOrNull()

            if (uiAutomation == null) {
                // 清理可能处于半连接 CONNECTING 状态的内部 mUiAutomation 字段，防止 finish() 时抛出 IllegalStateException 崩溃
                clearBrokenUiAutomationField()
                error("系统拒绝建立 UiAutomation 特权委托通道，请在开发者选项中开启「USB 调试（安全设置）」或「禁止权限监控」后重试")
            }

            uiAutomation.adoptShellPermissionIdentity()

            when (action) {
                PrivilegedAction.APPLY_US_ENV -> {
                    val simResults = SimRegionOverrideEngine.applyUsCarrierToAllSims(
                        context = ctx,
                        settings = savedSettings,
                        cliCarrierOverride = cliCarrier,
                        cliSlot0Label = cliSlot0Label,
                        cliSlot1Label = cliSlot1Label
                    )
                    val updatedPkgs = AppEnglishLocaleEngine.applyUsEnglishToPackages(
                        context = ctx,
                        candidatePackages = targetPkgs,
                        forceStopAfterApply = true
                    )

                    val hasNoSim = simResults.isEmpty()
                    val allSimsOk = simResults.isNotEmpty() && simResults.all { it.success }
                    val firstError = simResults.firstOrNull { !it.success }?.error.orEmpty()

                    val carrierSummary = simResults.joinToString(" / ") {
                        "卡${it.slotIndex + 1}:${it.currentCarrierName}"
                    }
                    val summary = when {
                        hasNoSim -> "无插卡模式就绪，已将 ${updatedPkgs.size} 款已安装应用锁定为美国英语 (en-US)"
                        allSimsOk -> {
                            "已将 ${simResults.size} 张电话卡设为美国区域 ($carrierSummary)，并驱动 ${updatedPkgs.size} 款应用为美国英语"
                        }
                        else -> "部分卡槽覆盖未通过：$firstError"
                    }

                    EnvExecutionReport(
                        action = action,
                        overallSuccess = hasNoSim || allSimsOk,
                        simOutcomes = simResults,
                        updatedEnglishPackages = updatedPkgs,
                        summaryText = summary,
                        errorMessage = if (hasNoSim || allSimsOk) "" else firstError
                    )
                }

                PrivilegedAction.RESTORE_CN_DEFAULT -> {
                    val simResults = SimRegionOverrideEngine.restoreAllSimsToRealCountry(ctx, savedSettings)
                    val resetPkgs = AppEnglishLocaleEngine.resetPackagesToSystemDefault(
                        context = ctx,
                        candidatePackages = targetPkgs
                    )
                    val ok = simResults.all { it.success }
                    val firstErr = simResults.firstOrNull { !it.success }?.error.orEmpty()

                    EnvExecutionReport(
                        action = action,
                        overallSuccess = ok,
                        simOutcomes = simResults,
                        updatedEnglishPackages = resetPkgs,
                        summaryText = "已恢复电话卡真实归属地，并将 ${resetPkgs.size} 款应用还原为跟随系统中文",
                        errorMessage = firstErr
                    )
                }
            }
        } catch (t: Throwable) {
            val root = generateSequence(t) { it.cause }.last()
            EnvExecutionReport(
                action = action,
                overallSuccess = false,
                simOutcomes = emptyList(),
                updatedEnglishPackages = emptyList(),
                summaryText = "美境特权执行异常",
                errorMessage = "${root.javaClass.simpleName}: ${root.message ?: "未知错误"}"
            )
        } finally {
            runCatching { uiAutomation?.dropShellPermissionIdentity() }
        }

        if (report.overallSuccess) {
            runCatching {
                val repo = UserPrefsRepository(ctx)
                when (action) {
                    PrivilegedAction.APPLY_US_ENV -> repo.recordAppliedSuccess(report.updatedEnglishPackages)
                    PrivilegedAction.RESTORE_CN_DEFAULT -> repo.recordRestoredDefault()
                }
                StatesideApp.instance.prefs.reload()
            }
        }

        PrivilegedResultBus.publish(ctx, report)

        val resultBundle = Bundle().apply {
            putBoolean("overallSuccess", report.overallSuccess)
            putString("summary", report.summaryText)
            putString("error", report.errorMessage)
        }
        runCatching {
            finish(if (report.overallSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultBundle)
        }.onFailure {
            clearBrokenUiAutomationField()
            runCatching {
                finish(if (report.overallSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultBundle)
            }
        }
    }

    private fun clearBrokenUiAutomationField() {
        runCatching {
            val field = Instrumentation::class.java.getDeclaredField("mUiAutomation")
            field.isAccessible = true
            field.set(this, null)
        }
    }
}
