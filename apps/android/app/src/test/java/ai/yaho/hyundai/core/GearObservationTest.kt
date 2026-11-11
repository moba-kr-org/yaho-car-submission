package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GearObservationTest {
    @Test fun onlyObservedParkAllowsLongActions() {
        assertEquals(DrivingMode.PARKED, modeFromGear("GEAR_PARK"))
        for (gear in listOf("GEAR_NEUTRAL", "GEAR_DRIVE", "GEAR_REVERSE", "GEAR_1")) {
            assertEquals(DrivingMode.MOVING, modeFromGear(gear))
        }
        for (gear in listOf(null, "GEAR_UNKNOWN", "P", "invented")) {
            assertEquals(DrivingMode.UNKNOWN, modeFromGear(gear))
        }
    }
}
