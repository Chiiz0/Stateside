package app.stateside.env.shortcut

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import app.stateside.env.R
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.ui.SafeLaunchActivity

object AppLaunchGuardHelper {

    const val EXTRA_TARGET_PKG = "extra_target_pkg"
    const val EXTRA_TARGET_TITLE = "extra_target_title"

    fun resolveInstalledPackage(context: Context, suiteApp: CoreSuiteApp): String? {
        val pm = context.packageManager
        return suiteApp.packageNames.firstOrNull { pkg ->
            isPackageInstalled(pm, pkg)
        }
    }

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            true
        }.getOrDefault(false)

    fun launchPackageDirectly(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return false
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    /**
     * 毫秒级安全启动守卫检查：
     * 1. 若已授 READ_PHONE_STATE：核验全部激活电话卡是否均处于 US 状态（无卡设备则核验用户已启用美境）；
     * 2. 若未授 READ_PHONE_STATE：核验当前开机周期（BOOT_COUNT）内是否已由特权组件完成过美境写入。
     */
    @SuppressLint("MissingPermission")
    fun isStatesideReadyForFastLaunch(context: Context, targetPackage: String? = null): Boolean {
        val prefs = UserPrefsRepository(context).settings.value
        if (!targetPackage.isNullOrBlank() &&
            targetPackage in prefs.effectiveEnglishPackages() &&
            targetPackage !in prefs.confirmedEnglishPackages
        ) {
            // 目标应用（如刚从 Play 商店新装的 TikTok）尚未完成 en-US 锁定，先拦截补写一次英语与美区配置
            return false
        }
        val hasChannel = prefs.isAdbPaired || LocalAdbManager.getInstance(context).cachedHasRootAccess()
        val bootCount = UserPrefsRepository.readCurrentBootCount(context)

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            return prefs.isReadyForCurrentSession(
                hasPrivilegeChannel = hasChannel,
                hasPhonePermission = false,
                simCardsCount = 0,
                allSimsUsSpoofed = false,
                currentBootCount = bootCount
            )
        }

        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return false
        val baseTm = context.getSystemService(TelephonyManager::class.java) ?: return false
        val subs = runCatching { subManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        val allUs = subs.isNotEmpty() && subs.all { info ->
            val subTm = baseTm.createForSubscriptionId(info.subscriptionId)
            subTm.simCountryIso.equals("us", ignoreCase = true)
        }

        return prefs.isReadyForCurrentSession(
            hasPrivilegeChannel = hasChannel,
            hasPhonePermission = true,
            simCardsCount = subs.size,
            allSimsUsSpoofed = allUs,
            currentBootCount = bootCount
        )
    }

    /**
     * 为任意指定目标应用（TikTok、Claude、ChatGPT、X 及自定义应用）创建带真实图标 + US 角标的桌面防漏改图标。
     */
    fun requestPinGuardedShortcut(
        context: Context,
        targetPackage: String,
        appTitle: String
    ): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false

        val launchGuardIntent = Intent(context, SafeLaunchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_TARGET_PKG, targetPackage)
            putExtra(EXTRA_TARGET_TITLE, appTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val shortcutId = "stateside_guard_${targetPackage.replace('.', '_')}"
        val shortcutIcon = buildBadgedShortcutIcon(context, targetPackage)
        val info = ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel("$appTitle · 美境")
            .setLongLabel("校验美境并打开 $appTitle")
            .setIcon(shortcutIcon)
            .setIntent(launchGuardIntent)
            .build()

        return runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)
    }

    private fun buildBadgedShortcutIcon(context: Context, targetPackage: String): Icon {
        return runCatching {
            val pm = context.packageManager
            val appDrawable = pm.getApplicationIcon(targetPackage)
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val pad = 12
            appDrawable.setBounds(pad, pad, size - pad, size - pad)
            appDrawable.draw(canvas)

            // 右下角绘制深海蓝底 + 亮青色描边的「US」防漏改盾牌角标
            val badgeRect = RectF(size * 0.52f, size * 0.62f, size - 4f, size - 4f)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(badgeRect, 22f, 22f, bgPaint)
            canvas.drawRoundRect(badgeRect, 22f, 22f, borderPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6FFFE9")
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val textY = badgeRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("US", badgeRect.centerX(), textY, textPaint)

            Icon.createWithBitmap(bmp)
        }.getOrElse {
            Icon.createWithResource(context, R.drawable.ic_shortcut_guard)
        }
    }
}
