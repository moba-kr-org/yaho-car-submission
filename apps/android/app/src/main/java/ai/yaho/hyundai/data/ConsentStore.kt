package ai.yaho.hyundai.data

import android.content.Context
import ai.yaho.hyundai.core.ExperimentVariant

class ConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGranted(variant: ExperimentVariant): Boolean = preferences.getBoolean(key(variant), false)

    fun setGranted(variant: ExperimentVariant, granted: Boolean) {
        preferences.edit().putBoolean(key(variant), granted).apply()
    }

    private fun key(variant: ExperimentVariant) = "consent_granted_${variant.wireKey}"

    companion object {
        private const val PREFS_NAME = "yaho_hyundai_consent"
    }
}
