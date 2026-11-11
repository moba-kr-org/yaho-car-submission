package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class ObservationStartupPolicyTest {
    @Test fun bootRequiresIndependentOptInAndObservationConsent() {
        assertEquals(ObservationStartup.DISABLED, observationStartup(true, true, false, 34))
        assertEquals(ObservationStartup.DISABLED, observationStartup(true, false, true, 34))
        assertEquals(ObservationStartup.DISABLED, observationStartup(false, true, true, 34))
        assertEquals(ObservationStartup.START_SERVICE, observationStartup(true, true, true, 34))
    }
    @Test fun android15NeverStartsDataSyncFromBoot() {
        for (sdk in 35..37) assertEquals(ObservationStartup.USER_OPEN_REQUIRED, observationStartup(true, true, true, sdk))
    }
    @Test fun observationWithdrawalAlsoRevokesBootResume() {
        val value = ai.yaho.hyundai.data.AppConsents(reviewed = true, vehicleRead = true, resumeAfterBoot = true)
        assertFalse(value.copy(vehicleRead = false).normalized().resumeAfterBoot)
        assertFalse(ai.yaho.hyundai.data.AppConsents(true, true, true, true, true).resumeAfterBoot)
    }
}
