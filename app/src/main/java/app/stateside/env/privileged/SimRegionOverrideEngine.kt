package app.stateside.env.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import app.stateside.env.model.MccIsoResolver
import app.stateside.env.model.UsCarrierPreset
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Android 14 ~ 17 免 Root SIM 卡美国运营商覆盖引擎：
 * 运行在 StatesideEnvInstrumentation 借权后的本应用 10xxx UID 下，天然绕过 TeleService 的 isShell 拦截。
 */
object SimRegionOverrideEngine {

    private const val KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING = "sim_country_iso_override_string"

    @SuppressLint("MissingPermission")
    fun applyUsCarrierToAllSims(context: Context, preset: UsCarrierPreset): List<SimSlotOutcome> {
        val subManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()
        val ccManager = context.getSystemService(CarrierConfigManager::class.java)
            ?: return emptyList()
        val baseTm = context.getSystemService(TelephonyManager::class.java)

        val activeSubs = runCatching { subManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        if (activeSubs.isEmpty()) return emptyList()

        return activeSubs.map { subInfo ->
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex
            val subTm = baseTm?.createForSubscriptionId(subId)
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(subTm?.simOperator)

            runCatching {
                val bundle = PersistableBundle().apply {
                    putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, preset.countryIso.lowercase())
                    putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                    putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, preset.carrierNameOverride)
                }

                invokeOverrideConfig(ccManager, subId, bundle)

                if (subTm != null) {
                    invokeSetOperatorBrandOverride(subTm, preset.carrierNameOverride)
                }

                var effectiveIso = subTm?.simCountryIso.orEmpty()
                var effectiveName = subTm?.simOperatorName.orEmpty()
                repeat(20) {
                    val isoReady = effectiveIso.equals(preset.countryIso, ignoreCase = true)
                    val nameReady = effectiveName.equals(preset.carrierNameOverride, ignoreCase = true)
                    if (isoReady && nameReady) return@repeat
                    Thread.sleep(100L)
                    effectiveIso = subTm?.simCountryIso.orEmpty()
                    effectiveName = subTm?.simOperatorName.orEmpty()
                }

                val verified = effectiveIso.equals(preset.countryIso, ignoreCase = true)
                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = effectiveIso.ifBlank { preset.countryIso },
                    currentCarrierName = subTm?.simOperatorName?.takeIf { it.isNotBlank() }
                        ?: preset.carrierNameOverride,
                    success = verified,
                    error = if (verified) "" else "写入未报错但读回国家码仍为 ${effectiveIso.ifBlank { "空" }}（请检查开发者选项安全开关）"
                )
            }.getOrElse { e ->
                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = subTm?.simCountryIso.orEmpty(),
                    currentCarrierName = subTm?.simOperatorName.orEmpty(),
                    success = false,
                    error = unwrapExceptionMessage(e)
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun restoreAllSimsToRealCountry(context: Context): List<SimSlotOutcome> {
        val subManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()
        val ccManager = context.getSystemService(CarrierConfigManager::class.java)
            ?: return emptyList()
        val baseTm = context.getSystemService(TelephonyManager::class.java)

        val activeSubs = runCatching { subManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        return activeSubs.map { subInfo ->
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex
            val subTm = baseTm?.createForSubscriptionId(subId)
            val simOperator = subTm?.simOperator
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(simOperator)
            val realCarrierName = subTm?.simCarrierIdName?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: MccIsoResolver.resolveCarrierDisplayName(simOperator)

            runCatching {
                // 第一步：显式写回真实国家码与运营商名，促使 SubscriptionManagerService 与 gsm.sim.operator.alpha 立即重算
                val stepOneBundle = PersistableBundle().apply {
                    putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, realIso)
                    if (realCarrierName.isNotBlank()) {
                        putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                        putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, realCarrierName)
                    } else {
                        putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)
                        putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, "")
                    }
                }
                invokeOverrideConfig(ccManager, subId, stepOneBundle)

                if (subTm != null) {
                    if (realCarrierName.isNotBlank()) {
                        invokeSetOperatorBrandOverride(subTm, realCarrierName)
                    }
                    invokeSetOperatorBrandOverride(subTm, "")
                }

                // 轮询等待第一步真实归属地与运营商名称在异步 Phone Handler 中落盘生效后，再执行第二步清空覆盖表
                repeat(12) {
                    val isoMatched = subTm?.simCountryIso.orEmpty().equals(realIso, ignoreCase = true)
                    val nameMatched = realCarrierName.isBlank() ||
                        subTm?.simOperatorName.orEmpty().equals(realCarrierName, ignoreCase = true)
                    if (isoMatched && nameMatched) return@repeat
                    Thread.sleep(100L)
                }

                // 第二步：传 null 彻底清空 CarrierConfig 会话覆盖层
                invokeOverrideConfig(ccManager, subId, null)
                Thread.sleep(200L)

                var finalIso = subTm?.simCountryIso.orEmpty()
                repeat(12) {
                    if (finalIso.equals(realIso, ignoreCase = true)) return@repeat
                    Thread.sleep(80L)
                    finalIso = subTm?.simCountryIso.orEmpty()
                }
                val verified = finalIso.isBlank() ||
                    finalIso.equals(realIso, ignoreCase = true) ||
                    (realIso != "us" && !finalIso.equals("us", ignoreCase = true))

                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = finalIso.ifBlank { realIso },
                    currentCarrierName = subTm?.simOperatorName?.takeIf { it.isNotBlank() } ?: realCarrierName,
                    success = verified,
                    error = if (verified) "" else "恢复后国家码仍显示为 $finalIso"
                )
            }.getOrElse { e ->
                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = subTm?.simCountryIso.orEmpty(),
                    currentCarrierName = subTm?.simOperatorName.orEmpty(),
                    success = false,
                    error = unwrapExceptionMessage(e)
                )
            }
        }
    }

    private fun invokeOverrideConfig(
        ccManager: CarrierConfigManager,
        subId: Int,
        bundle: PersistableBundle?
    ) {
        val threeArgResult = runCatching {
            val method = CarrierConfigManager::class.java.getMethod(
                "overrideConfig",
                Int::class.javaPrimitiveType,
                PersistableBundle::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(ccManager, subId, bundle, false)
        }
        if (threeArgResult.isSuccess) return

        val bypassThreeArg = runCatching {
            HiddenApiBypass.invoke(
                CarrierConfigManager::class.java,
                ccManager,
                "overrideConfig",
                subId,
                bundle,
                false
            )
        }
        if (bypassThreeArg.isFailure) {
            HiddenApiBypass.invoke(
                CarrierConfigManager::class.java,
                ccManager,
                "overrideConfig",
                subId,
                bundle
            )
        }
    }

    private fun invokeSetOperatorBrandOverride(subTm: TelephonyManager, brand: String?) {
        val direct = runCatching {
            val m = TelephonyManager::class.java.getMethod(
                "setOperatorBrandOverride",
                String::class.java
            )
            m.invoke(subTm, brand)
        }
        if (direct.isFailure && brand != null) {
            runCatching {
                HiddenApiBypass.invoke(
                    TelephonyManager::class.java,
                    subTm,
                    "setOperatorBrandOverride",
                    brand
                )
            }
        }
    }

    private fun unwrapExceptionMessage(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}: ${root.message ?: "未知系统异常"}"
    }
}
