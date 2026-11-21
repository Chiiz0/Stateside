package app.stateside.env.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import app.stateside.env.data.UserSettings
import app.stateside.env.model.MccIsoResolver
import app.stateside.env.model.UsCarrierPreset
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Android 14 ~ 17 免 Root 双卡美国区域与状态栏运营商显示引擎：
 *
 * 1. 当卡槽设为「保持原运营商（推荐）」（`targetCarrierLabel == null`）：
 *    仅覆盖 `sim_country_iso_override_string = "us"`，绝不改动状态栏与控制中心原有的运营商显示；
 * 2. 当卡槽设为自定义名称（如 `X`）或指定美国运营商（`T-Mobile` / `AT&T` / `Verizon`）：
 *    同时注入 `carrier_name_string` + `pnn_override_string_array` (3GPP EF_PNN) + `spn_display_condition_override_int = 1`
 *    + `force_home_network_bool = true` + `config_carrier_support_nitz = false` + 清空 OEM PLMN 锁定表，
 *    可完整穿透中国移动「全球通钻卡 / 白金卡 / 动感地带」等 5G SA 核心网 NITZ 尊享显标对状态栏的硬锁！
 */
object SimRegionOverrideEngine {

    private const val KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING = "sim_country_iso_override_string"
    private const val KEY_PNN_OVERRIDE_STRING_ARRAY = "pnn_override_string_array"
    private const val KEY_OPL_OVERRIDE_STRING_ARRAY = "opl_override_opl_string_array"
    private const val KEY_EHPLMN_OVERRIDE_STRING_ARRAY = "ehplmn_override_string_array"
    private const val KEY_SPN_DISPLAY_CONDITION_OVERRIDE_INT = "spn_display_condition_override_int"
    private const val KEY_FORCE_HOME_NETWORK_BOOL = "force_home_network_bool"
    private const val KEY_CONFIG_CARRIER_SUPPORT_NITZ = "config_carrier_support_nitz"
    private const val KEY_OPLUS_UNRELATED_PLMN = "carrier_unrelated_with_plmn"

    @SuppressLint("MissingPermission")
    fun applyUsCarrierToAllSims(
        context: Context,
        settings: UserSettings,
        cliCarrierOverride: UsCarrierPreset? = null,
        cliSlot0Label: String? = null,
        cliSlot1Label: String? = null
    ): List<SimSlotOutcome> {
        val subManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()
        val ccManager = context.getSystemService(CarrierConfigManager::class.java)
            ?: return emptyList()
        val baseTm = context.getSystemService(TelephonyManager::class.java)

        val activeSubs = runCatching { subManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        if (activeSubs.isEmpty()) return emptyList()

        val effectiveSettings = if (cliCarrierOverride != null || cliSlot0Label != null || cliSlot1Label != null) {
            settings.copy(
                selectedCarrier = cliCarrierOverride ?: settings.selectedCarrier,
                slot0DisplayOverride = cliSlot0Label ?: settings.slot0DisplayOverride,
                slot1DisplayOverride = cliSlot1Label ?: settings.slot1DisplayOverride
            )
        } else {
            settings
        }

        val targetIso = effectiveSettings.selectedCarrier.countryIso.lowercase()

        return activeSubs.map { subInfo ->
            val subId = subInfo.subscriptionId
            val slotIndex = subInfo.simSlotIndex
            val subTm = baseTm?.createForSubscriptionId(subId)
            val simOperator = subTm?.simOperator.orEmpty()
            val netOperator = subTm?.networkOperator.orEmpty()
            val realIso = MccIsoResolver.resolveRealIsoFromMccMnc(simOperator)
            val realCarrierName = subTm?.simCarrierIdName?.toString()
                ?.takeIf { it.isNotBlank() && !isKnownOverrideBrand(it, effectiveSettings) }
                ?: MccIsoResolver.resolveCarrierDisplayName(simOperator)

            val targetCarrierLabel = effectiveSettings.resolveTargetCarrierLabelForSlot(slotIndex)

            runCatching {
                // 始终先清除 UiccProfile 中可能持久化的 operator_branding_<iccid>
                clearOperatorBrandOverride(subTm)

                if (targetCarrierLabel == null && hasResidualOverrideBrand(subTm, subInfo.carrierName?.toString(), effectiveSettings)) {
                    // 从自定义/美国运营商名称切回「保持原运营商」时，先传 null 重置覆盖层使原运营商名称立即归位
                    invokeOverrideConfig(ccManager, subId, null)
                    repeat(8) {
                        val refreshed = queryLiveStatusBarCarrierName(subManager, subId)
                        if (!hasResidualOverrideBrand(subTm, refreshed, effectiveSettings)) return@repeat
                        Thread.sleep(60L)
                    }
                }

                val bundle = PersistableBundle().apply {
                    putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, targetIso)

                    if (targetCarrierLabel != null) {
                        val safePnnName = targetCarrierLabel.replace(',', ' ').trim().ifEmpty { targetCarrierLabel }
                        putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                        putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, targetCarrierLabel)
                        // 穿透 5G SA 核心网 NITZ 尊享显标（如「中国移动全球通钻卡」）：
                        // 1. 注入 EF_PNN 首选网络全称与简称（AOSP CDNR 优先级高于 ServiceState 基站 AlphaLong）
                        putStringArray(KEY_PNN_OVERRIDE_STRING_ARRAY, arrayOf("$safePnnName,$safePnnName"))
                        putStringArray(KEY_OPL_OVERRIDE_STRING_ARRAY, emptyArray())
                        // 2. 将当前注册 PLMN 纳入等效归属网络并强制非漫游态，确保 CDNR 命中 EF_PNN 分支
                        val homePlmns = listOf(netOperator, simOperator)
                            .map { it.trim() }
                            .filter { it.length in 5..6 && it.all(Char::isDigit) }
                            .distinct()
                        if (homePlmns.isNotEmpty()) {
                            putStringArray(KEY_EHPLMN_OVERRIDE_STRING_ARRAY, homePlmns.toTypedArray())
                        }
                        putBoolean(KEY_FORCE_HOME_NETWORK_BOOL, true)
                        // 3. 位掩码 1 (SPN_RULE_SHOW_PLMN)：仅展示单一名（避免 SPN 与 PLMN 同时开启拼接为 "X - X"）
                        putInt(KEY_SPN_DISPLAY_CONDITION_OVERRIDE_INT, 1)
                        // 4. 关闭基站 NITZ 覆盖与 OEM 特定 PLMN 显示规则拦截
                        putBoolean(KEY_CONFIG_CARRIER_SUPPORT_NITZ, false)
                        putStringArray(KEY_OPLUS_UNRELATED_PLMN, emptyArray())
                        if (netOperator.length in 5..6 && netOperator.all(Char::isDigit)) {
                            putStringArray(netOperator, emptyArray())
                        }
                    }
                }

                invokeOverrideConfig(ccManager, subId, bundle)

                var effectiveIso = subTm?.simCountryIso.orEmpty()
                var liveBarName = queryLiveStatusBarCarrierName(subManager, subId)
                repeat(20) {
                    val isoReady = effectiveIso.equals(targetIso, ignoreCase = true)
                    val nameReady = targetCarrierLabel == null ||
                        liveBarName.equals(targetCarrierLabel, ignoreCase = true)
                    if (isoReady && nameReady) return@repeat
                    Thread.sleep(100L)
                    effectiveIso = subTm?.simCountryIso.orEmpty()
                    liveBarName = queryLiveStatusBarCarrierName(subManager, subId)
                }

                val verified = effectiveIso.equals(targetIso, ignoreCase = true)
                val reportedCarrier = liveBarName.ifBlank {
                    subTm?.simOperatorName?.takeIf { it.isNotBlank() }
                        ?: targetCarrierLabel
                        ?: realCarrierName
                }

                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = effectiveIso.ifBlank { targetIso },
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
                    currentCarrierName = queryLiveStatusBarCarrierName(subManager, subId)
                        .ifBlank { subTm?.simOperatorName.orEmpty() },
                    success = false,
                    error = unwrapExceptionMessage(e)
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun restoreAllSimsToRealCountry(context: Context, settings: UserSettings? = null): List<SimSlotOutcome> {
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
                ?.takeIf { it.isNotBlank() && !isKnownOverrideBrand(it, settings) }
                ?: MccIsoResolver.resolveCarrierDisplayName(simOperator)

            runCatching {
                clearOperatorBrandOverride(subTm)

                // 直接传 null 清空 CarrierConfig 全部会话覆盖项（含 PNN / NITZ / 国家码），让系统原生配置与 5G 显标自然归位
                invokeOverrideConfig(ccManager, subId, null)
                Thread.sleep(220L)

                var finalIso = subTm?.simCountryIso.orEmpty()
                repeat(12) {
                    if (finalIso.equals(realIso, ignoreCase = true)) return@repeat
                    Thread.sleep(80L)
                    finalIso = subTm?.simCountryIso.orEmpty()
                }

                // 针对极少数空白 EF_SPN 卡在清空覆盖层后仍残留旧名称的情况做一次真实名称脉冲修复
                val afterClearBarName = queryLiveStatusBarCarrierName(subManager, subId)
                if (hasResidualOverrideBrand(subTm, afterClearBarName, settings) && realCarrierName.isNotBlank()) {
                    val pulse = PersistableBundle().apply {
                        putString(KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, realIso)
                        putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                        putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, realCarrierName)
                    }
                    invokeOverrideConfig(ccManager, subId, pulse)
                    Thread.sleep(180L)
                    invokeOverrideConfig(ccManager, subId, null)
                    Thread.sleep(150L)
                    finalIso = subTm?.simCountryIso.orEmpty()
                }

                val verified = finalIso.isBlank() ||
                    finalIso.equals(realIso, ignoreCase = true) ||
                    (realIso != "us" && !finalIso.equals("us", ignoreCase = true))

                val restoredBarName = queryLiveStatusBarCarrierName(subManager, subId)
                    .ifBlank { subTm?.simOperatorName?.takeIf { it.isNotBlank() } ?: realCarrierName }

                SimSlotOutcome(
                    slotIndex = slotIndex,
                    subId = subId,
                    originalIso = realIso,
                    currentIso = finalIso.ifBlank { realIso },
                    currentCarrierName = restoredBarName,
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

    @SuppressLint("MissingPermission")
    private fun queryLiveStatusBarCarrierName(subManager: SubscriptionManager, subId: Int): String =
        runCatching {
            subManager.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == subId }
                ?.carrierName
                ?.toString()
                ?.trim()
                .orEmpty()
        }.getOrDefault("")

    private fun isKnownOverrideBrand(name: String, settings: UserSettings?): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val dynamicNames = buildSet {
            addAll(UsCarrierPreset.OVERRIDE_BRAND_NAMES)
            settings?.customCarrierLabel?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            settings?.slot0DisplayOverride?.trim()
                ?.takeIf { it.isNotEmpty() && it != app.stateside.env.data.UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN }
                ?.let(::add)
            settings?.slot1DisplayOverride?.trim()
                ?.takeIf { it.isNotEmpty() && it != app.stateside.env.data.UserPrefsRepository.SLOT_KEEP_NATIVE_TOKEN }
                ?.let(::add)
        }
        return dynamicNames.any { it.equals(trimmed, ignoreCase = true) }
    }

    private fun hasResidualOverrideBrand(
        subTm: TelephonyManager?,
        statusBarName: String?,
        settings: UserSettings?
    ): Boolean {
        val simName = subTm?.simOperatorName.orEmpty()
        val barName = statusBarName.orEmpty()
        if (isKnownOverrideBrand(simName, settings) || isKnownOverrideBrand(barName, settings)) {
            return true
        }
        val gsmAlpha = readSystemProperty("gsm.operator.alpha")
        return gsmAlpha.split(',').any { isKnownOverrideBrand(it, settings) }
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
