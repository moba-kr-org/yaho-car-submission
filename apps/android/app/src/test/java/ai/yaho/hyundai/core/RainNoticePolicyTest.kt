package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class RainNoticePolicyTest {
    @Test fun expiredOfflineWithdrawnAndNonCandidatesNeverProducePositiveNotice() {
        assertNull(rainNoticeText(false,"possible_precipitation","supports",false))
        for (state in listOf("", "insufficient_evidence", "no_collective_precipitation_signal"))
            assertNull(rainNoticeText(true,state,"supports",false))
    }
    @Test fun simulatorAndConflictAreSpokenAsUnconfirmed() {
        val text = rainNoticeText(true,"possible_precipitation","conflicts",true)!!
        assertTrue(text.startsWith("시험 안내입니다."))
        assertTrue(text.contains("미확인"))
        assertFalse(text.contains("비가 옵니다"))
        assertTrue(rainNoticeText(true,"possible_precipitation","unavailable",false)!!.contains("미확인"))
    }
    @Test fun firstNoticeAndFiveMinuteBoundaryWorkWithoutResettingOnClearOrCellChange() {
        assertTrue(mayPresentRainNotice(true,null,1_000))
        assertFalse(mayPresentRainNotice(false,null,1_000))
        assertFalse(mayPresentRainNotice(true,1_000,300_999))
        assertTrue(mayPresentRainNotice(true,1_000,301_000))
    }
    @Test fun corruptOrRolledBackClockNeverRearmsImmediately() {
        assertFalse(mayPresentRainNotice(true,2_000,1_000))
        assertFalse(mayPresentRainNotice(true,0,1_000))
        assertFalse(mayPresentRainNotice(true,-1,1_000))
        assertFalse(mayPresentRainNotice(true,null,0))
    }
}
