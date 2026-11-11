package ai.yaho.hyundai.core

enum class ObservationStartup { DISABLED, START_SERVICE, USER_OPEN_REQUIRED }


fun observationStartup(reviewed: Boolean, vehicleRead: Boolean, resumeAfterBoot: Boolean, sdk: Int): ObservationStartup =
    when {
        !reviewed || !vehicleRead || !resumeAfterBoot -> ObservationStartup.DISABLED
        sdk >= 35 -> ObservationStartup.USER_OPEN_REQUIRED
        else -> ObservationStartup.START_SERVICE
    }
