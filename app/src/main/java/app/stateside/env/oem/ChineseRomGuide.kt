package app.stateside.env.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

data class RomSetupGuide(
    val brandTitle: String,
    val buildNumberPath: String,
    val criticalExtraToggleTitle: String,
    val criticalExtraToggleDesc: String,
    val keepAliveTip: String,
    val requiresExtraSecurityToggle: Boolean = true,
    val isUnsupportedHarmonyWarning: Boolean = false
)

object ChineseRomGuide {

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    private const val PREF_KEY_WIRELESS_ADB = "toggle_adb_wireless"

    fun detectCurrentRom(): RomSetupGuide {
        val brand = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val display = Build.DISPLAY.lowercase()

        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> RomSetupGuide(
                brandTitle = "小米 / REDMI（澎湃 HyperOS）",
                buildNumberPath = "设置 → 我的设备 → 全部参数与信息 → 连续点击 7 次「OS 版本」",
                criticalExtraToggleTitle = "必开开关：USB 调试（安全设置）",
                criticalExtraToggleDesc = "在「开发者选项」内除了开启「无线调试」，请务必往下滑找到并开启「USB 调试（安全设置）」（需登录小米账号点三次允许），否则系统会拦截美区运营商与英语配置修改！",
                keepAliveTip = "日常请勿使用「手机管家 → 一键优化/体检」，该功能会自动关闭开发者选项。"
            )

            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") -> {
                if (Build.VERSION.SDK_INT >= 36) {
                    RomSetupGuide(
                        brandTitle = "OPPO / 一加 / 真我（ColorOS 16 / 17）",
                        buildNumberPath = "设置 → 关于本机 → 版本信息 → 连续点击 7 次「版本号」",
                        criticalExtraToggleTitle = "仅需开启「无线调试」（ColorOS 16/17 免额外开关）",
                        criticalExtraToggleDesc = "ColorOS 16 / 17 已移除旧版「禁止权限监控」并原生支持无线调试特权委托，直接开启「无线调试」（勾选始终允许在此网络上调试）即可！",
                        keepAliveTip = "建议在应用详情「耗电管理」中设为「允许完全后台行为」，保障重启自动恢复美境。",
                        requiresExtraSecurityToggle = false
                    )
                } else {
                    RomSetupGuide(
                        brandTitle = "OPPO / 一加 / 真我（ColorOS 14 / 15）",
                        buildNumberPath = "设置 → 关于本机 → 版本信息 → 连续点击 7 次「版本号」",
                        criticalExtraToggleTitle = "若遇权限拦截：开启底部「禁止权限监控」",
                        criticalExtraToggleDesc = "旧版 ColorOS 14 / 15 若提示权限委托被拦截，可滑到「开发者选项」最底部开启「禁止权限监控」（若您的系统无此项说明已原生免开）。",
                        keepAliveTip = "建议在应用详情「耗电管理」中设为「允许完全后台行为」，保障重启自动恢复美境。"
                    )
                }
            }

            brand.contains("vivo") || brand.contains("iqoo") -> RomSetupGuide(
                brandTitle = "vivo / iQOO（OriginOS）",
                buildNumberPath = "设置 → 系统管理 → 关于手机 → 版本信息 → 连续点击 7 次「软件版本号」",
                criticalExtraToggleTitle = "必开开关：无线调试 + USB 调试",
                criticalExtraToggleDesc = "在「开发者选项」中同时打开「USB 调试」与「无线调试」，并在无线调试弹窗勾选「始终允许在此网络上调试」。",
                keepAliveTip = "若开启开机自动恢复，请在「电池 → 后台耗电管理」允许美境后台高耗电运行。"
            )

            brand.contains("honor") -> RomSetupGuide(
                brandTitle = "荣耀（MagicOS）",
                buildNumberPath = "设置 → 关于手机 → 连续点击 7 次「版本号」",
                criticalExtraToggleTitle = "必开开关：“仅充电”模式下允许 ADB 调试",
                criticalExtraToggleDesc = "在「开发者选项」中先开启「“仅充电”模式下允许 ADB 调试」，再开启「无线调试」，否则锁屏后无线调试可能自动断开。",
                keepAliveTip = "在应用启动管理中关闭「自动管理」，手动勾选允许自启动与后台活动。"
            )

            brand.contains("huawei") || display.contains("harmony") -> RomSetupGuide(
                brandTitle = "华为机型提示",
                buildNumberPath = "设置 → 关于手机 → 连续点击 7 次「版本号」",
                criticalExtraToggleTitle = "系统框架兼容性提示",
                criticalExtraToggleDesc = "部分定制系统在框架层移除了标准 Android 运营商覆盖接口；若激活后提示接口受限，属于系统底层封锁现象。",
                keepAliveTip = "请开启「“仅充电”模式下允许 ADB 调试」后再尝试连接。",
                isUnsupportedHarmonyWarning = true
            )

            brand.contains("samsung") -> RomSetupGuide(
                brandTitle = "三星（One UI）",
                buildNumberPath = "设置 → 关于手机 → 软件信息 → 连续点击 7 次「编译编号」",
                criticalExtraToggleTitle = "关闭自动拦截器并开启「无线调试」",
                criticalExtraToggleDesc = "请确认「安全与隐私 → 自动拦截器（Auto Blocker）」处于关闭状态，随后在开发者选项内开启「无线调试」。",
                keepAliveTip = "在无线调试弹窗勾选「始终允许在此网络上调试」，重启连 Wi-Fi 即可自动恢复。"
            )

            else -> RomSetupGuide(
                brandTitle = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}（Android ${Build.VERSION.RELEASE}）",
                buildNumberPath = "设置 → 关于手机 → 连续点击 7 次「版本号」",
                criticalExtraToggleTitle = "开启「无线调试」",
                criticalExtraToggleDesc = "进入「设置 → 系统 → 开发者选项 → 无线调试」，打开开关并点击「使用配对码配对设备」。",
                keepAliveTip = "配对时勾选「始终允许在此网络上调试」，后续即可一键秒开。",
                requiresExtraSecurityToggle = false
            )
        }
    }

    fun isDeveloperOptionsEnabled(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        }.getOrDefault(false)

    /**
     * 若已开启开发者选项，直接跳转并携带系统高亮锚点自动滚动至「无线调试」条目；
     * 若尚未开启开发者选项，则跳转至「关于手机」引导连点 7 次版本号。
     */
    fun openDeveloperOptionsOrAboutPhone(context: Context, highlightWirelessAdb: Boolean = true) {
        val targetIntent = if (isDeveloperOptionsEnabled(context)) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                if (highlightWirelessAdb) {
                    putExtra(EXTRA_FRAGMENT_ARG_KEY, PREF_KEY_WIRELESS_ADB)
                    putExtra(
                        EXTRA_SHOW_FRAGMENT_ARGS,
                        Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, PREF_KEY_WIRELESS_ADB) }
                    )
                }
            }
        } else {
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(targetIntent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openDateTimeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_DATE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
