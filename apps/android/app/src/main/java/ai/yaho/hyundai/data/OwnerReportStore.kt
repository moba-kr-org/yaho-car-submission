package ai.yaho.hyundai.data

import android.content.Context
import ai.yaho.hyundai.core.ExperimentVariant

data class OwnerReportState(val reportId: String, val status: String)

class OwnerReportStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(variant: ExperimentVariant): OwnerReportState? {
        val id = preferences.getString("report_id_${variant.wireKey}", null) ?: return null
        val status = preferences.getString("report_status_${variant.wireKey}", "submitted") ?: "submitted"
        return OwnerReportState(id, status)
    }

    fun save(variant: ExperimentVariant, reportId: String, status: String = "submitted") {
        preferences.edit()
            .putString("report_id_${variant.wireKey}", reportId)
            .putString("report_status_${variant.wireKey}", status)
            .apply()
    }

    fun clear(variant: ExperimentVariant) {
        preferences.edit()
            .remove("report_id_${variant.wireKey}")
            .remove("report_status_${variant.wireKey}")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "yaho_hyundai_owner_reports"
    }
}
