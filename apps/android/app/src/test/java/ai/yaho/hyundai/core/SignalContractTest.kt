package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class SignalContractTest {
    private fun freshness(at: Long? = 100_000, receipt: Long? = 50_000, now: Long = 100_000,
        elapsed: Long = 50_000, active: Boolean = true, state: String = "수신", hasValue: Boolean = true) =
        observationFreshness(state, hasValue, active, at, receipt, now, elapsed)

    @Test fun eitherClockExpiringInvalidatesAValueAndBoundaryIsExplicit() {
        assertEquals(ObservationFreshness.FRESH, freshness(now = 130_000, elapsed = 80_000))
        assertEquals(ObservationFreshness.STALE, freshness(now = 130_001))
        assertEquals(ObservationFreshness.STALE, freshness(elapsed = 80_001))
    }
    @Test fun clockRollbackFutureAndMissingReceiptsAreNotFresh() {
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(now = 99_999))
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(elapsed = 49_999))
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(at = null))
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(receipt = null))
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(at = 0))
        assertEquals(ObservationFreshness.TIMESTAMP_INVALID, freshness(receipt = -1))
    }
    @Test fun stoppedOrFailedObservationNeverPresentsCachedValueAsCurrent() {
        assertEquals(ObservationFreshness.STOPPED, freshness(active = false))
        assertEquals(ObservationFreshness.UNAVAILABLE, freshness(state = "時間超過"))
        assertEquals(ObservationFreshness.UNAVAILABLE, freshness(hasValue = false))
    }
    @Test fun settingsAndReportedStateHaveDifferentMeaning() {
        assertEquals(SignalMeaning.SETTING, signalContract("Light.getHighBeamLightSwitch").meaning)
        assertEquals(SignalMeaning.REPORTED_STATE, signalContract("Light.getCurrentHighBeamLightState").meaning)
        assertEquals(SignalMeaning.REPORTED_POSITION, signalContract("Seat.getCurrentSeatDepthPosition.ROW_1_LEFT").meaning)
    }
    @Test fun explicitCelsiusDoesNotLeakIntoMeasuredTemperatureOrPosition() {
        assertEquals("CELSIUS", signalContract("HVAC.getHvacTemperature.ROW_1_LEFT").confirmedUnit)
        assertNull(signalContract("HVAC.getHvacTemperatureCurrent.ROW_1_LEFT").confirmedUnit)
        assertNull(signalContract("Seat.getCurrentSeatDepthPosition.ROW_1_LEFT").confirmedUnit)
        assertEquals("앞좌석 왼쪽", signalContract("Seat.getCurrentSeatDepthPosition.ROW_1_LEFT").area)
    }
    @Test fun unknownMethodStaysUnknownAndWiperDerivedIsNotAnotherSensor() {
        assertEquals(SignalMeaning.UNKNOWN, signalContract("Unknown.getSomething.ROW_1_LEFT").meaning)
        assertEquals(SignalMeaning.DERIVED, signalContract("wiper.front.active").meaning)
        assertEquals(SignalMeaning.REPORTED_STATE, signalContract("wiper.front.state").meaning)
    }
}
