package app.stateside.env.boot

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.stateside.env.adb.LocalAdbManager
import app.stateside.env.data.UserPrefsRepository
import app.stateside.env.privileged.PrivilegedAction
import app.stateside.env.shortcut.AppLaunchGuardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 真机开机 Wi-Fi 延迟自愈服务：
 * 真机重启后通常需 5~30 秒才会连上家庭/办公室 Wi-Fi 并拉起 adbd 无线调试 TLS 端口。
 * 通过系统 JobScheduler 绑定 TRANSPORT_WIFI 网络约束，无论用户何时解锁或连上 Wi-Fi，均可精准唤醒完成美境补写。
 */
class BootRestoreJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val current = UserPrefsRepository(applicationContext).settings.value
        if (!current.autoRestoreOnBoot || !current.isStatesideDesired) {
            jobFinished(params, false)
            return false
        }

        if (AppLaunchGuardHelper.isStatesideReadyForFastLaunch(applicationContext)) {
            jobFinished(params, false)
            return false
        }

        activeJob = scope.launch {
            // Wi-Fi 刚关联成功时，等待 1.5 秒让系统 adbd 完成 _adb-tls-connect._tcp 注册
            delay(1_500L)

            val adb = LocalAdbManager.getInstance(applicationContext)
            val result = adb.runEnvironmentTask(
                action = PrivilegedAction.APPLY_US_ENV,
                carrier = current.selectedCarrier,
                targetPackagesOverride = current.effectiveEnglishPackages()
            )

            if (result.isSuccess) {
                BootRestoreReceiver.notifyBootRestored(
                    applicationContext,
                    result.getOrThrow().summaryText
                )
                jobFinished(params, false)
            } else {
                // 若首次无线调试尚未就绪，允许系统按指数退避重试一次
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID_WIFI_BOOT_RESTORE = 8101

        fun scheduleWifiRestore(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val wifiRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

                val jobInfo = JobInfo.Builder(
                    JOB_ID_WIFI_BOOT_RESTORE,
                    ComponentName(context, BootRestoreJobService::class.java)
                )
                    .setRequiredNetwork(wifiRequest)
                    .setBackoffCriteria(10_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build()

                scheduler.schedule(jobInfo)
            }
        }

        fun cancelPending(context: Context) {
            runCatching {
                context.getSystemService(JobScheduler::class.java)
                    ?.cancel(JOB_ID_WIFI_BOOT_RESTORE)
            }
        }
    }
}
