package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class ManualAndNavigationPolicyTest {
    @Test fun manualMatchRequiresModelYearAndSalesMarketTogether() {
        val matching=parseManualProfile("아이오닉 5","2025","US",2026)!!
        assertTrue(matchesAvailableManual(matching))
        for(profile in listOf(null,matching.copy(market="KR"),matching.copy(year=2024),matching.copy(model="IONIQ6"))) assertFalse(matchesAvailableManual(profile))
        assertTrue(manualApplicabilityLabel(matching).contains("실차 검증 전"))
    }
    @Test fun invalidInputsNeverBecomeADefaultCar() {
        for(profile in listOf(parseManualProfile("","2025","KR",2026),parseManualProfile("IONIQ5","2028","KR",2026),parseManualProfile("IONIQ5","20x5","US",2026),parseManualProfile("bad\nvalue","2025","US",2026),parseManualProfile("IONIQ5","2025","UNKNOWN",2026))) assertNull(profile)
    }
    @Test fun routeModeIsExplicitAndFreshnessUsesReceiptOnly() {
        assertEquals("내비 모의 안내 모드",routeObservationLabel(NavigationRouteObservation("received","SIMULATION_GUIDING",100),100))
        assertTrue(routeObservationLabel(NavigationRouteObservation("received","ROUTE_GUIDING",100),30_101).contains("만료"))
        assertTrue(routeObservationLabel(NavigationRouteObservation("received","ROUTE_GUIDING",100),99).contains("만료"))
        assertEquals("확인되지 않은 내비 모드",routeObservationLabel(NavigationRouteObservation("received","unknown",100),100))
    }
    @Test fun missingResponseDoesNotClaimRouteInactive() {
        for(state in listOf("idle","pending","timeout","error","blocked")) assertFalse(routeObservationLabel(NavigationRouteObservation(state),100).contains("비활성 모드"))
    }
}
