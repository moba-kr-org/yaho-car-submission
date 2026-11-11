package ai.yaho.hyundai.vehicle

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTurnTest {
    @Test
    fun preservesDirectionDistanceAndReceiptWithoutRouteText() {
        val turn = TBTInfo(TurnType.TURN_RIGHT, 300.0, "도로명").toNavigationTurn(42L)
        assertEquals(NavigationTurn(TurnDirection.RIGHT, 300.0, 42L), turn)
    }

    @Test
    fun unsupportedManeuversAndInvalidDistancesAreHidden() {
        assertNull(TBTInfo(TurnType.TURN_UNKNOWN, 300.0, "").toNavigationTurn(42L))
        assertNull(TBTInfo(TurnType.TURN_LEFT, -1.0, "").toNavigationTurn(42L))
        assertNull(TBTInfo(TurnType.TURN_LEFT, Double.NaN, "").toNavigationTurn(42L))
    }
}
