package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class VisibilityPolicyTest {
    private fun frame(t:Long,wiper:String="ON",defrost:String="true")=listOf(
        VisibilitySignal(VISIBILITY_WIPER,"수신",wiper,1_000_000+t,100_000+t),
        VisibilitySignal(VISIBILITY_DEFROSTER,"수신",defrost,1_000_000+t,100_000+t))
    private fun step(m:VisibilityMonitor,t:Long,w:String="ON",d:String="true")=m.update(frame(t,w,d),true,1_000_000+t,100_000+t,true)
    private fun qualify(m:VisibilityMonitor):VisibilityAssessment {step(m,0);step(m,15_000);return step(m,30_000)}
    @Test fun simultaneousDistinctThirtySecondSeriesQualifiesAsCheckNotWeatherDiagnosis() {
        val result=qualify(VisibilityMonitor())
        assertEquals("check_recommended",result.status)
        assertEquals(3,result.wiperSamples);assertEquals(3,result.defrosterSamples)
        assertEquals(30_000,result.overlapMs)
        assertTrue(result.notice!!.text.startsWith("시험 안내"))
        assertFalse(result.notice!!.text.contains("비가"));assertFalse(result.notice!!.text.contains("블랙아이스"))
    }
    @Test fun repeatedSnapshotAndShortWashingCannotFabricatePersistence() {
        val m=VisibilityMonitor()
        repeat(8){m.update(frame(0),true,1_000_000+it*1000L,100_000+it*1000L,true)}
        assertEquals(1,m.update(frame(0),true,1_030_000,130_000,true).wiperSamples)
        assertNull(step(m,30_001,"OFF").notice)
    }
    @Test fun unknownModesDuplicatesAndStaleOrFutureTimeRemoveCandidate() {
        for(mode in listOf("OTHER","SERVICE","UNKNOWN")) {
            val m=VisibilityMonitor();qualify(m);assertNull(step(m,31_000,mode).notice)
        }
        val m=VisibilityMonitor();qualify(m)
        assertEquals("unknown",m.update(frame(30_000)+frame(30_000).first(),true,1_030_000,130_000,true).status)
        assertNull(m.update(frame(30_000),true,1_060_001,160_001,true).notice)
        assertEquals("unknown",m.update(frame(70_000),true,1_069_999,169_999,true).status)
    }
    @Test fun gapOffOrRearOnlyNeverKeepsPriorRecommendation() {
        val m=VisibilityMonitor();step(m,0);step(m,10_000)
        assertNull(step(m,40_000).notice)
        qualify(VisibilityMonitor()).also{assertNotNull(it.notice)}
        val active=VisibilityMonitor();qualify(active);assertNull(step(active,31_000,d="false").notice)
        assertEquals("unknown",VisibilityMonitor().update(frame(0).map{if(it.key==VISIBILITY_DEFROSTER)it.copy(key="HVAC.getHvacDefrosterState.REAR_WINDSHIELD")else it},true,1_000_000,100_000,true).status)
    }
    @Test fun consentWithdrawalClearsShortTermHistoryAndClockRollbackCannotQualify() {
        val m=VisibilityMonitor();qualify(m)
        assertEquals("stopped",m.update(frame(30_000),false,1_030_000,130_000,true).status)
        assertEquals(1,step(m,31_000).wiperSamples)
        assertNull(step(m,1_000).notice)
    }
    @Test fun lightsOrTemperatureAloneNeverBecomeVisibilityOrFogMeasurement() {
        val rows=listOf(VisibilitySignal("Light.getCurrentLowBeamLightState","수신","ON",1_000_000,100_000),
            VisibilitySignal("HVAC.getHvacTemperatureCurrent.ROW_1_LEFT","수신","19",1_000_000,100_000))
        val result=VisibilityMonitor().update(rows,true,1_000_000,100_000,true)
        assertNull(result.notice);assertTrue(result.lightContext.contains("날씨 판정 아님"))
        assertTrue(result.climateContext.contains("김서림 계산에 미사용"))
    }
    @Test fun sameReceiptConflictCannotReappearAsNewEvidence() {
        val m=VisibilityMonitor();step(m,0)
        assertEquals("unknown",step(m,0,"OFF").status)
        assertEquals("unknown",step(m,0).status)
        assertNull(step(m,1000).notice)
    }
    @Test fun visibilityRanksBelowRoadAndSafetyButAboveMaintenanceAndRain() {
        val visible=qualify(VisibilityMonitor()).notice!!
        val now=1_030_000L
        val washer=assessVehicleWarnings(listOf(WarningObservation("Safety.hasLowWasherFluidWarning","수신","true",now,130_000)),true,now,130_000).active
        val road=RoadNoticeEvidence("road_hazard","grid","unverified",now+1000,0,1)
        assertEquals("visibility:check",selectDrivingNotice(washer,emptyList(),"rain",now,130_000,true,true,null,null,false,visible)?.key)
        assertEquals("road:road_hazard",selectDrivingNotice(washer,listOf(road),"rain",now,130_000,true,true,"grid",130_000,true,visible)?.key)
    }
}
