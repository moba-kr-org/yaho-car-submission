package ai.yaho.hyundai.core


fun modeFromGear(gear: String?): DrivingMode = when (gear) {
    "GEAR_PARK" -> DrivingMode.PARKED
    "GEAR_DRIVE", "GEAR_REVERSE", "GEAR_NEUTRAL",
    "GEAR_1", "GEAR_2", "GEAR_3", "GEAR_4", "GEAR_5",
    "GEAR_6", "GEAR_7", "GEAR_8", "GEAR_9" -> DrivingMode.MOVING
    else -> DrivingMode.UNKNOWN
}
