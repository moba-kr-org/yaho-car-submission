package ai.yaho.hyundai.data

import android.content.Context
import ai.yaho.hyundai.core.DrivingMode

class SimulatorStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): DrivingMode = runCatching {
        DrivingMode.valueOf(preferences.getString(KEY_MODE, DrivingMode.PARKED.name)!!)
    }.getOrDefault(DrivingMode.PARKED)

    fun write(mode: DrivingMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "yaho_hyundai_simulator"
        private const val KEY_MODE = "driving_mode"
    }
}
