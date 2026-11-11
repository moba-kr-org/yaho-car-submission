package ai.yaho.hyundai.core

data class DrivingUxState(val known: Boolean = false, val requiresOptimization: Boolean = true, val flags: Int = -1) {
    val restricted: Boolean get() = !known || requiresOptimization || flags != 0
}

fun canUseParkedExperience(mode: DrivingMode, ux: DrivingUxState): Boolean = mode == DrivingMode.PARKED && !ux.restricted

fun exceedsSettingsSpeedLimit(speedKph: Float?): Boolean =
    speedKph?.let { it.isFinite() && kotlin.math.abs(it) > 40f } == true


fun settingsAfterSpeedChange(settingsOpen: Boolean, speedKph: Float?): Boolean =
    settingsOpen && !exceedsSettingsSpeedLimit(speedKph)
