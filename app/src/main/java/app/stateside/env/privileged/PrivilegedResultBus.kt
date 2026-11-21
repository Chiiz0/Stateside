package app.stateside.env.privileged

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class PrivilegedAction {
    APPLY_US_ENV,
    RESTORE_CN_DEFAULT,
    SWITCH_US_TIMEZONE,
    RESTORE_HOME_TIMEZONE
}

data class SimSlotOutcome(
    val slotIndex: Int,
    val subId: Int,
    val originalIso: String,
    val currentIso: String,
    val currentCarrierName: String,
    val success: Boolean,
    val error: String = ""
)

data class EnvExecutionReport(
    val action: PrivilegedAction,
    val overallSuccess: Boolean,
    val simOutcomes: List<SimSlotOutcome>,
    val updatedEnglishPackages: List<String>,
    val summaryText: String,
    val errorMessage: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("action", action.name)
        put("overallSuccess", overallSuccess)
        put("summaryText", summaryText)
        put("errorMessage", errorMessage)
        put("updatedEnglishPackages", JSONArray(updatedEnglishPackages))
        put("simOutcomes", JSONArray().apply {
            simOutcomes.forEach { s ->
                put(JSONObject().apply {
                    put("slotIndex", s.slotIndex)
                    put("subId", s.subId)
                    put("originalIso", s.originalIso)
                    put("currentIso", s.currentIso)
                    put("currentCarrierName", s.currentCarrierName)
                    put("success", s.success)
                    put("error", s.error)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(raw: String): EnvExecutionReport? = runCatching {
            val obj = JSONObject(raw)
            val pkgsJson = obj.optJSONArray("updatedEnglishPackages") ?: JSONArray()
            val pkgs = buildList {
                for (i in 0 until pkgsJson.length()) add(pkgsJson.getString(i))
            }
            val simsJson = obj.optJSONArray("simOutcomes") ?: JSONArray()
            val sims = buildList {
                for (i in 0 until simsJson.length()) {
                    val item = simsJson.getJSONObject(i)
                    add(
                        SimSlotOutcome(
                            slotIndex = item.optInt("slotIndex"),
                            subId = item.optInt("subId"),
                            originalIso = item.optString("originalIso"),
                            currentIso = item.optString("currentIso"),
                            currentCarrierName = item.optString("currentCarrierName"),
                            success = item.optBoolean("success"),
                            error = item.optString("error")
                        )
                    )
                }
            }
            EnvExecutionReport(
                action = PrivilegedAction.valueOf(obj.optString("action", PrivilegedAction.APPLY_US_ENV.name)),
                overallSuccess = obj.optBoolean("overallSuccess"),
                simOutcomes = sims,
                updatedEnglishPackages = pkgs,
                summaryText = obj.optString("summaryText"),
                errorMessage = obj.optString("errorMessage")
            )
        }.getOrNull()
    }
}

object PrivilegedResultBus {

    private const val RESULT_FILENAME = "stateside_last_report.json"

    @Volatile
    private var activeDeferred: CompletableDeferred<EnvExecutionReport>? = null

    private fun reportFile(context: Context): File =
        File(context.createDeviceProtectedStorageContext().cacheDir, RESULT_FILENAME)

    fun prepareNewRun(context: Context): CompletableDeferred<EnvExecutionReport> {
        runCatching { reportFile(context).delete() }
        val deferred = CompletableDeferred<EnvExecutionReport>()
        activeDeferred = deferred
        return deferred
    }

    fun publish(context: Context, report: EnvExecutionReport) {
        runCatching { reportFile(context).writeText(report.toJson()) }
        activeDeferred?.complete(report)
    }

    fun readDiskFallback(context: Context): EnvExecutionReport? {
        val f = reportFile(context)
        if (!f.exists()) return null
        return EnvExecutionReport.fromJson(f.readText())
    }
}
