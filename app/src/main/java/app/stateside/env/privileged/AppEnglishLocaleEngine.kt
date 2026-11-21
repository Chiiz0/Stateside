package app.stateside.env.privileged

import android.app.ActivityManager
import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.LocaleList
import android.os.Parcel
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import app.stateside.env.model.StatesideDefaults
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Android 14 ~ 17 单应用美国英语（en-US）批量设定引擎：
 * 自动将已安装的 TikTok、X、Claude、ChatGPT（含国产系统双开分身）及用户扩展应用锁定为 en-US，
 * 手机全局系统继续保持简体中文不受影响。
 */
object AppEnglishLocaleEngine {

    private const val DESCRIPTOR_LOCALE_MANAGER = "android.app.ILocaleManager"
    private const val TRANSACTION_SET_APPLICATION_LOCALES = IBinder.FIRST_CALL_TRANSACTION + 0

    fun applyUsEnglishToPackages(
        context: Context,
        candidatePackages: Collection<String>,
        forceStopAfterApply: Boolean = true
    ): List<String> {
        return updatePackagesLocale(
            context = context,
            candidatePackages = candidatePackages,
            locales = LocaleList.forLanguageTags(StatesideDefaults.US_ENGLISH_TAG),
            forceStopAfterApply = forceStopAfterApply
        )
    }

    fun resetPackagesToSystemDefault(
        context: Context,
        candidatePackages: Collection<String>
    ): List<String> {
        return updatePackagesLocale(
            context = context,
            candidatePackages = candidatePackages,
            locales = LocaleList.getEmptyLocaleList(),
            forceStopAfterApply = true
        )
    }

    private fun updatePackagesLocale(
        context: Context,
        candidatePackages: Collection<String>,
        locales: LocaleList,
        forceStopAfterApply: Boolean
    ): List<String> {
        val smClass = Class.forName("android.os.ServiceManager")
        val binder = HiddenApiBypass.invoke(smClass, null, "getService", "locale") as? IBinder
        val service = binder?.let {
            runCatching {
                val stubClass = Class.forName("android.app.ILocaleManager\$Stub")
                HiddenApiBypass.invoke(stubClass, null, "asInterface", it)
            }.getOrNull()
        }
        val publicLocaleManager = context.getSystemService(LocaleManager::class.java)

        val mainUserId = Process.myUid() / 100_000
        val userIds = discoverTargetUserIds(context, mainUserId)
        val pm = context.packageManager
        val am = context.getSystemService(ActivityManager::class.java)
        val targetTagsNormalized = normalizeLanguageTags(locales.toLanguageTags())
        val updated = mutableListOf<String>()

        for (pkg in candidatePackages.distinct()) {
            // 逐个用户空间（主空间 0 + 国产系统双开分身 999 等）独立检测安装状态，
            // 彻底修复「微信开启双开但 Claude 未双开时，user 999 读回空串导致每次都误强停 Claude」的真机缺陷！
            val installedUsers = userIds.filter { uid ->
                isPackageInstalledForUser(pm, pkg, uid, mainUserId)
            }
            if (installedUsers.isEmpty()) continue

            var anyUserSucceeded = false
            var anyUserChangedLocale = false

            for (userId in installedUsers) {
                val beforeTags = normalizeLanguageTags(
                    readCurrentLocaleTags(service, publicLocaleManager, pkg, userId, mainUserId)
                )
                val ok = runCatching {
                    setLocalesWithFallbacks(
                        binder = binder,
                        service = service,
                        publicLocaleManager = publicLocaleManager,
                        packageName = pkg,
                        userId = userId,
                        mainUserId = mainUserId,
                        locales = locales
                    )
                    true
                }.getOrDefault(false)

                if (ok) {
                    anyUserSucceeded = true
                    if (beforeTags != targetTagsNormalized) {
                        anyUserChangedLocale = true
                    }
                }
            }

            if (anyUserSucceeded) {
                // 仅当该应用本次确实发生了语言变更时才强停重载，保障开机静默补写时绝不打断正在对话的 Claude/ChatGPT
                if (forceStopAfterApply && anyUserChangedLocale && am != null && pkg != context.packageName) {
                    runCatching {
                        HiddenApiBypass.invoke(
                            ActivityManager::class.java,
                            am,
                            "forceStopPackage",
                            pkg
                        )
                    }
                }
                updated.add(pkg)
            }
        }
        return updated
    }

    private fun normalizeLanguageTags(raw: String): String =
        raw.trim().lowercase()

    private fun readCurrentLocaleTags(
        service: Any?,
        publicLocaleManager: LocaleManager?,
        packageName: String,
        userId: Int,
        mainUserId: Int
    ): String {
        if (service != null) {
            val fromBinder = runCatching {
                val list = HiddenApiBypass.invoke(
                    service.javaClass,
                    service,
                    "getApplicationLocales",
                    packageName,
                    userId
                ) as? LocaleList
                list?.toLanguageTags()
            }.getOrNull()
            if (fromBinder != null) return fromBinder
        }
        if (userId == mainUserId && publicLocaleManager != null) {
            return runCatching {
                publicLocaleManager.getApplicationLocales(packageName).toLanguageTags()
            }.getOrDefault("")
        }
        return ""
    }

    private fun setLocalesWithFallbacks(
        binder: IBinder?,
        service: Any?,
        publicLocaleManager: LocaleManager?,
        packageName: String,
        userId: Int,
        mainUserId: Int,
        locales: LocaleList
    ) {
        if (service != null) {
            val reflectionResult = runCatching {
                HiddenApiBypass.invoke(
                    service.javaClass,
                    service,
                    "setApplicationLocales",
                    packageName,
                    userId,
                    locales,
                    false
                )
            }
            if (reflectionResult.isSuccess) return
        }

        if (userId == mainUserId && publicLocaleManager != null) {
            val sysApiResult = runCatching {
                HiddenApiBypass.invoke(
                    LocaleManager::class.java,
                    publicLocaleManager,
                    "setApplicationLocales",
                    packageName,
                    locales
                )
            }
            if (sysApiResult.isSuccess) return
        }

        val nonNullBinder = binder ?: error("未找到系统 locale 服务")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR_LOCALE_MANAGER)
            data.writeString(packageName)
            data.writeInt(userId)
            data.writeTypedObject(locales, 0)
            data.writeBoolean(false)
            nonNullBinder.transact(TRANSACTION_SET_APPLICATION_LOCALES, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun discoverTargetUserIds(context: Context, mainUserId: Int): List<Int> {
        val ids = linkedSetOf(mainUserId)
        runCatching {
            val um = context.getSystemService(UserManager::class.java)
            um?.userProfiles?.forEach { handle ->
                val uid = HiddenApiBypass.invoke(UserHandle::class.java, handle, "getIdentifier") as? Int
                if (uid != null && uid >= 0) {
                    ids.add(uid)
                }
            }
        }
        return ids.toList()
    }

    private fun isPackageInstalledForUser(
        pm: PackageManager,
        packageName: String,
        userId: Int,
        mainUserId: Int
    ): Boolean {
        val perUserCheck = runCatching {
            HiddenApiBypass.invoke(
                pm.javaClass,
                pm,
                "getPackageInfoAsUser",
                packageName,
                0,
                userId
            ) != null
        }
        if (perUserCheck.isSuccess) {
            return perUserCheck.getOrDefault(false)
        }
        if (userId == mainUserId) {
            return runCatching {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                true
            }.getOrDefault(false)
        }
        return false
    }
}
