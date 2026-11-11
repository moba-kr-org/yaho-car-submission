package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WiperStateProjectionTest {
    @Test fun onlyOnAndOffProduceDefiniteActiveBooleans() {
        assertEquals(WiperStateProjection("ON", true), projectWiperState("ON"))
        assertEquals(WiperStateProjection("OFF", false), projectWiperState("OFF"))
    }

    @Test fun otherAndServiceRemainVisibleButDoNotBecomeActiveOrInactive() {
        for (name in listOf("OTHER", "SERVICE")) {
            val result = projectWiperState(name)
            assertEquals(name, result.enumName)
            assertNull(result.active)
        }
    }

    @Test fun unknownAndMissingNamesNeverDefaultToOff() {
        val unknown = projectWiperState("FUTURE_STATE")
        assertEquals("FUTURE_STATE", unknown.enumName)
        assertNull(unknown.active)

        val missing = projectWiperState(null)
        assertNull(missing.enumName)
        assertNull(missing.active)
    }
}
