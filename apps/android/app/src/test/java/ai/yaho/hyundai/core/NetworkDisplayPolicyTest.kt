package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class NetworkDisplayPolicyTest {
    @Test fun withdrawalAndLostPrerequisitesHideEvenFreshPositiveAssessment() {
        val cases = listOf(
            networkAccessState(true, true, false, true) to NetworkAccessState.SHARING_OFF,
            networkAccessState(false, true, true, true) to NetworkAccessState.SERVER_NOT_CONFIGURED,
            networkAccessState(true, false, true, true) to NetworkAccessState.INSTALL_REQUIRED,
            networkAccessState(true, true, true, false) to NetworkAccessState.LOCATION_REQUIRED,
        )
        for ((actual, expected) in cases) {
            assertEquals(expected, actual)
            val display = networkDisplayState(NetworkLoadState.RECEIVED, true, 10, actual)
            assertFalse(display.showAssessment)
            assertEquals("미확인", display.participantLabel)
        }
        assertEquals(NetworkAccessState.SHARING_OFF, networkAccessState(false, false, false, false))
        assertEquals(NetworkAccessState.READY, networkAccessState(true, true, true, true))
    }

    @Test fun liveAssessmentUsesCountOnlyWhileFresh() {
        val value=networkDisplayState(NetworkLoadState.RECEIVED,true,10)
        assertTrue(value.showAssessment)
        assertEquals("10곳",value.participantLabel)
        val expired=networkDisplayState(NetworkLoadState.RECEIVED,false,10)
        assertFalse(expired.showAssessment)
        assertEquals("미확인",expired.participantLabel)
    }
    @Test fun failedOrMissingResponseNeverRevivesPreviousPositiveOrClaimsZeroVehicles() {
        for(load in listOf(NetworkLoadState.WAITING,NetworkLoadState.EMPTY,NetworkLoadState.OFFLINE,NetworkLoadState.TIMEOUT)) {
            val value=networkDisplayState(load,true,10)
            assertFalse(value.showAssessment)
            assertEquals("미확인",value.participantLabel)
        }
    }
}
