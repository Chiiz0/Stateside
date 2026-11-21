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
 * Android 14 ~ 17 免 Root SIM 卡美国区域覆盖引擎：
 * 运行在 StatesideEnvInstrumentation 借权后的本应用 10xxx UID 下，天然绕过 TeleService 的 isShell 拦截。
 *
 * 默认采用与 Simlet 一致的「仅覆盖 sim_country_iso_override_string = us」无痕策略：
 * - 让 TelephonyManager.getSimCountryIso() 与 SubscriptionInfo.getCountryIso() 对外返回 `us`；
 * - 绝不触碰状态栏与下拉通知栏原有的「中国电信 / 中国联通 / 中国移动」运营商显示；
 * - 同时自动清理早期版本或第三方工具残留的 ICCID 品牌名覆盖（setOperatorBrandOverride(null)）。
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
            val simOperator = subTm?.simOperator
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(simOperator)
            val realCarrierName = subTm?.simCarrierIdName?.toString()
                ?.takeIf { it.isNotBlank() && !isOverrideBrandName(it) }
                ?: MccIsoResolver.resolveCarrierDisplayName(simOperator)

            runCatching {
                // 始终先清除 UiccProfile 中可能持久化的 operator_branding_<iccid>，防止状态栏残留 T-Mobile
                clearOperatorBrandOverride(subTm)

                if (preset.keepsOriginalCarrierName && hasResidualOverrideBrand(subTm)) {
                    // 若此前开启过覆盖运营商名称（如 T-Mobile），先重置 CarrierConfig 覆盖层使原运营商名称立即归位
                    invokeOverrideConfig(ccManager, subId, null)
                    repeat(7) {
                        if (!hasResidualOverrideBrand(subTm)) return@repeat
                        Thread.sleep(50L)
                    }
                    if (hasResidualOverrideBrand(subTm) && realCarrierName.isNotBlank()) {
                        val restoreNamePulse = PersistableBundle().apply {
                            putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, realIso)
                            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, realCarrierName)
                        }
                        invokeOverrideConfig(ccManager, subId, restoreNamePulse)
                        Thread.sleep(180L)
                        invokeOverrideConfig(ccManager, subId, null)
                        Thread.sleep(150L)
                    }
                }

                val bundle = PersistableBundle().apply {
                    putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, preset.countryIso.lowercase())
                    if (!preset.keepsOriginalCarrierName) {
                        putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                        putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, preset.carrierNameOverride)
                    }
                }

                invokeOverrideConfig(ccManager, subId, bundle)

                var effectiveIso = subTm?.simCountryIso.orEmpty()
                var effectiveName = subTm?.simOperatorName.orEmpty()
                repeat(20) {
                    val isoReady = effectiveIso.equals(preset.countryIso, ignoreCase = true)
                    val nameReady = preset.keepsOriginalCarrierName ||
                        effectiveName.equals(preset.carrierNameOverride, ignoreCase = true)
                    if (isoReady && nameReady) return@repeat
                    Thread.sleep(100L)
                    effectiveIso = subTm?.simCountryIso.orEmpty()
                    effectiveName = subTm?.simOperatorName.orEmpty()
                }

                val verified = effectiveIso.equals(preset.countryIso, ignoreCase = true)
                val reportedCarrier = subTm?.simOperatorName?.takeIf { it.isNotBlank() }
                    ?: subTm?.networkOperatorName?.takeIf { it.isNotBlank() }
                    ?: if (preset.keepsOriginalCarrierName) realCarrierName else preset.carrierNameOverride

                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = effectiveIso.ifBlank { preset.countryIso },
                    currentCarrierName = reportedCarrier,
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
                ?.takeIf { it.isNotBlank() && !isOverrideBrandName(it) }
                ?: MccIsoResolver.resolveCarrierDisplayName(simOperator)

            runCatching {
                // 第一步：彻底移除 UiccProfile 的持久化品牌名覆盖（传 null 而非空串，触发 spEditor.remove）
                clearOperatorBrandOverride(subTm)

                // 第二步：若当前仍残留美国运营商显示名，先脉冲写回真实国家码与真实运营商名，促使 gsm.sim.operator.alpha 重算
                if (hasResidualOverrideBrand(subTm) || subTm?.simCountryIso.equals("us", ignoreCase = true)) {
                    val stepOneBundle = PersistableBundle().apply {
                        putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, realIso)
                        if (realCarrierName.isNotBlank()) {
                            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, realCarrierName)
                        } else {
                            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)
                        }
                    }
                    invokeOverrideConfig(ccManager, subId, stepOneBundle)

                    repeat(10) {
                        val isoMatched = subTm?.simCountryIso.orEmpty().equals(realIso, ignoreCase = true)
                        val nameCleaned = !hasResidualOverrideBrand(subTm)
                        if (isoMatched && nameCleaned) return@repeat
                        Thread.sleep(80L)
                    }
                }

                // 第三步：传 null 彻底清空 CarrierConfig 会话覆盖层，回归 100% 系统原生状态
                invokeOverrideConfig(ccManager, subId, null)
                Thread.sleep(180L)

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

    private fun isOverrideBrandName(name: String): Boolean =
        UsCarrierPreset.OVERRIDE_BRAND_NAMES.any { it.equals(name.trim(), ignoreCase = true) }

    private fun hasResidualOverrideBrand(subTm: TelephonyManager?): Boolean {
        val simName = subTm?.simOperatorName.orEmpty()
        val netName = subTm?.networkOperatorName.orEmpty()
        if (isOverrideBrandName(simName) || isOverrideBrandName(netName)) return true

        val gsmAlpha = readSystemProperty("gsm.operator.alpha")
        val simAlpha = readSystemProperty("gsm.sim.operator.alpha")
        return UsCarrierPreset.OVERRIDE_BRAND_NAMES.any { brand ->
            gsmAlpha.contains(brand, ignoreCase = true) || simAlpha.contains(brand, ignoreCase = true)
        }
    }

    private fun readSystemProperty(key: String): String = runCatching {
        val spClass = Class.forName("android.os.SystemProperties")
        spClass.getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, "") as? String ?: ""
    }.getOrDefault("")

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

    private fun clearOperatorBrandOverride(subTm: TelephonyManager?) {
        if (subTm == null) return
        val direct = runCatching {
            val m = TelephonyManager::class.java.getMethod(
                "setOperatorBrandOverride",
                String::class.java
            )
            m.invoke(subTm, null as String?)
        }
        if (direct.isFailure) {
            runCatching {
                HiddenApiBypass.invoke(
                    TelephonyManager::class.java,
                    subTm,
                    "setOperatorBrandOverride",
                    null
                )
            }
        }
    }

    private fun unwrapExceptionMessage(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return "${root.javaClass.simpleName}: ${root.message ?: "未知系统异常"}"
    }
}
