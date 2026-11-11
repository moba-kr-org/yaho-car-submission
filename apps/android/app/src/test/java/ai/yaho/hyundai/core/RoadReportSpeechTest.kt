package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadReportSpeechTest {
    @Test
    fun selectsOneReviewableCategory() {
        assertEquals("accident_report", roadReportKindFromSpeech("앞에 사고가 났어요."))
        assertEquals("slippery_report", roadReportKindFromSpeech("여기 블랙 아이스 같아요"))
        assertEquals("stalled_vehicle", roadReportKindFromSpeech("갓길에 차가 서 있어요"))
        assertEquals("rain_report", roadReportKindFromSpeech("비가 내려요"))
    }

    @Test
    fun leavesUnclearAndCompoundReportsForManualSelection() {
        assertNull(roadReportKindFromSpeech("앞에 사고가 나서 정체예요"))
        assertNull(roadReportKindFromSpeech("준비가 됐어요"))
        assertNull(roadReportKindFromSpeech("차가 서행하고 있어요"))
    }
}
