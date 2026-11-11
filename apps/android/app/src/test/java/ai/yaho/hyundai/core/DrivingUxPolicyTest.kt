package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class DrivingUxPolicyTest {
    @Test fun settingsCloseOnlyAboveForty() {
        assertTrue(settingsAfterSpeedChange(true, 0f))
        assertTrue(settingsAfterSpeedChange(true, 10.1f))
        assertTrue(settingsAfterSpeedChange(true, 40f))
        assertFalse(settingsAfterSpeedChange(true, 40.1f))
        assertFalse(settingsAfterSpeedChange(true, -41f))
    }
    @Test fun slowingDownDoesNotReopenSettingsAndUnavailableSpeedIsNotFabricated() {
        var open = settingsAfterSpeedChange(true, 41f)
        open = settingsAfterSpeedChange(open, 20f)
        assertFalse(open)
        assertFalse(settingsAfterSpeedChange(false, null))
        assertTrue(settingsAfterSpeedChange(true, null))
        assertFalse(exceedsSettingsSpeedLimit(Float.NaN))
        assertFalse(exceedsSettingsSpeedLimit(Float.POSITIVE_INFINITY))
        assertFalse(canUseParkedExperience(DrivingMode.MOVING, DrivingUxState(true, true, 80)))
    }
    @Test fun parkedExperienceRequiresBothObservedParkAndPlatformPermission() {
        assertTrue(canUseParkedExperience(DrivingMode.PARKED, DrivingUxState(true, false, 0)))
        assertFalse(canUseParkedExperience(DrivingMode.PARKED, DrivingUxState()))
        assertFalse(canUseParkedExperience(DrivingMode.PARKED, DrivingUxState(true, true, 8)))
        assertFalse(canUseParkedExperience(DrivingMode.PARKED, DrivingUxState(true, false, 64)))
        assertFalse(canUseParkedExperience(DrivingMode.MOVING, DrivingUxState(true, false, 0)))
        assertFalse(canUseParkedExperience(DrivingMode.UNKNOWN, DrivingUxState(true, false, 0)))
    }
}
