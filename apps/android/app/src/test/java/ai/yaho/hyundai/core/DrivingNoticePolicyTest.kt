package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class DrivingNoticePolicyTest {
    private val now=100_000L
    private val grid="47327:90339"
    private fun warning(key:String)=assessVehicleWarnings(listOf(WarningObservation(key,"수신","true",now,5000)),true,now,5000).active
    private val road=RoadNoticeEvidence("slippery_report",grid,"unverified",now+5000,0,1)
    private fun select(warnings:List<ActiveVehicleWarning> = emptyList(), roads:List<RoadNoticeEvidence> = listOf(road),
        receipt:Long?=5000,scope:String?=grid,allow:Boolean=true,simulation:Boolean=true,elapsed:Long=5000)=
        selectDrivingNotice(warnings,roads,"시험 강수 후보",now,elapsed,true,simulation,scope,receipt,allow)
    @Test fun priorityUsesVehicleSafetyBeforeRoadBeforeMaintenanceBeforeRain() {
        val washer=warning("Safety.hasLowWasherFluidWarning")
        val power=warning("Safety.hasPowerDownWarningIndicator")
        val belt=warning("Safety.hasSeatBeltNotBuckledWarning.ROW_1_LEFT")
        assertEquals("vehicle:power_limited",select(washer+power+belt)?.key)
        assertEquals("vehicle:seat_belt",select(washer+belt)?.key)
        assertEquals("road:slippery_report",select(washer)?.key)
        assertEquals("vehicle:washer_low",select(washer,emptyList())?.key)
        assertEquals("rain",select(emptyList(),emptyList())?.key)
    }
    @Test fun wrongGridExpiredStaleUnknownOrMixedSourceCannotBecomeNearbyAlert() {
        for (invalid in listOf(road.copy(grid="elsewhere"),road.copy(expiresAt=now),road.copy(status="cleared"),road.copy(users=1)))
            assertEquals("rain",select(roads=listOf(invalid))?.key)
        assertEquals("rain",select(scope=null)?.key)
        assertEquals("rain",select(receipt=null)?.key)
        assertEquals("rain",select(receipt=-25_001)?.key)
        assertEquals("rain",select(receipt=100,elapsed=40_000)?.key)
        assertEquals("rain",select(allow=false)?.key)
        assertEquals("rain",select(simulation=false)?.key)
        assertTrue(select(roads=listOf(road.copy(status="conflicted")))!!.text.contains("상충"))
        assertTrue(select()!!.text.startsWith("시험 안내"))
        assertTrue(select()!!.text.contains("진행 방향은 미확인"))
    }
    @Test fun missingOrStaleWarningCannotOutrankNewEvidence() {
        assertEquals("rain",select(warning("Safety.hasPowerDownWarningIndicator").map{it.copy(observedAt=1)},emptyList())?.key)
    }
    @Test fun gridMatchesServerPublicFixtureAndRejectsMissingCoordinates() {
        assertEquals(grid,noticeGrid(37.5446,127.0557))
        assertEquals("47335:90290",noticeGrid(37.5665,126.978))
        assertNull(noticeGrid(null,127.0));assertNull(noticeGrid(Double.NaN,127.0));assertNull(noticeGrid(0.0,0.0))
    }
    @Test fun higherNoticeInterruptsLowerDespiteCooldownButClearedLowerNeverReplays() {
        var stamp:GuidanceStamp?=null;var stops=0
        val controller=PriorityGuidanceController({stamp},{stamp=it;true},{stops++})
        val rain=DrivingNotice("rain",20,"강수 후보")
        val power=DrivingNotice("vehicle:power_limited",100,"계기판 확인")
        assertEquals("강수 후보",controller.update(rain,true,1000,0))
        assertEquals("강수 후보",controller.consumeSpeech(true))
        assertEquals("계기판 확인",controller.update(power,true,2000,1000))
        assertEquals("계기판 확인",controller.consumeSpeech(true))
        assertNull(controller.consumeSpeech(true))
        assertNull(controller.update(rain,true,3000,2000))
        assertTrue(stops>=3)
        val restarted=PriorityGuidanceController({stamp},{stamp=it;true},{})
        assertNull(restarted.update(power,true,4000,0))
    }
}
