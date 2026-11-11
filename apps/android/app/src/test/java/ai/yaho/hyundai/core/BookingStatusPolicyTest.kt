package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class BookingStatusPolicyTest {
    @Test fun acceptedIsNeverDescribedAsConfirmed() {
        for (status in listOf("needs_configuration","queued","leased","unknown")) {
            val display=bookingStatusDisplay(status,false)
            assertTrue(display.canCancel)
            assertFalse(display.title.contains("예약 확정"))
        }
        assertTrue(bookingStatusDisplay("needs_configuration",false).detail.contains("전달하거나 예약을 확정한 상태가 아닙니다"))
    }
    @Test fun cancellationIntentIsNotCancellationCompletion() {
        for(status in listOf("leased","unknown","cancel_requested")) {
            val display=bookingStatusDisplay(status,true)
            assertEquals("취소 요청 접수 · 완료 미확인",display.title)
            assertFalse(display.canCancel)
        }
        assertEquals("요청 취소 완료",bookingStatusDisplay("cancelled",true).title)
    }
    @Test fun unexpectedOrFailedStatusesNeverOfferBlindRetryOrCancellation() {
        for(status in listOf("failed","success","confirmed","unrecognized","")) assertFalse(bookingStatusDisplay(status,false).canCancel)
        assertEquals("알 수 없는 처리 상태",bookingStatusDisplay("confirmed",false).title)
    }
}
