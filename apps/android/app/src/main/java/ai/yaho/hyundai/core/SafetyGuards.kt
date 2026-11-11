package ai.yaho.hyundai.core

enum class DrivingMode {
    PARKED,
    MOVING,
    UNKNOWN,
}

enum class ConsentChoice {
    NOT_DECIDED,
    GRANTED,
    WITHDRAWN,
}

object SafetyGuards {
    fun canPerformLongAction(mode: DrivingMode): Boolean = mode == DrivingMode.PARKED

    fun canSubmitReport(mode: DrivingMode, consent: ConsentChoice): Boolean =
        canPerformLongAction(mode) && consent == ConsentChoice.GRANTED

    fun canRequestService(mode: DrivingMode, userConfirmed: Boolean): Boolean =
        canPerformLongAction(mode) && userConfirmed
}
