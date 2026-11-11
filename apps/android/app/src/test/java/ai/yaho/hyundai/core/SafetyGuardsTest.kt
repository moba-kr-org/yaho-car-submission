package ai.yaho.hyundai.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardsTest {
    @Test
    fun longActionsRequireParkedState() {
        assertTrue(SafetyGuards.canPerformLongAction(DrivingMode.PARKED))
        assertFalse(SafetyGuards.canPerformLongAction(DrivingMode.MOVING))
        assertFalse(SafetyGuards.canPerformLongAction(DrivingMode.UNKNOWN))
    }

    @Test
    fun reportsRequireParkedStateAndConsent() {
        assertFalse(SafetyGuards.canSubmitReport(DrivingMode.PARKED, ConsentChoice.NOT_DECIDED))
        assertFalse(SafetyGuards.canSubmitReport(DrivingMode.MOVING, ConsentChoice.GRANTED))
        assertTrue(SafetyGuards.canSubmitReport(DrivingMode.PARKED, ConsentChoice.GRANTED))
    }

    @Test
    fun serviceRequestsRequireParkedStateAndExplicitConfirmation() {
        assertFalse(SafetyGuards.canRequestService(DrivingMode.PARKED, userConfirmed = false))
        assertFalse(SafetyGuards.canRequestService(DrivingMode.MOVING, userConfirmed = true))
        assertTrue(SafetyGuards.canRequestService(DrivingMode.PARKED, userConfirmed = true))
    }
}
