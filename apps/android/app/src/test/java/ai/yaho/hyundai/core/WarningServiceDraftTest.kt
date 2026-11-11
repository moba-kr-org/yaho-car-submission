package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class WarningServiceDraftTest {
    private val now=1_000_000L
    private fun assessment(value:String="true")=assessVehicleWarnings(listOf(WarningObservation("Safety.hasPowerDownWarningIndicator","수신",value,now,100_000)),true,now,100_000)
    @Test fun matchedWarningCreatesExplicitlyUnconfirmedDraftWithSource() {
        val current=assessment();val draft=prepareWarningServiceDraft(current.active.single(),current,true,true,true)!!
        assertEquals("출력 제한 상태 점검",draft.serviceType)
        assertTrue(draft.notes.contains("시험 관측"));assertTrue(draft.notes.contains("원인 미확인"))
        assertTrue(draft.notes.contains("Safety.hasPowerDownWarningIndicator"))
        assertEquals(now,draft.origin.observedAt)
        assertTrue(warningServiceNotes(draft.notes,draft.origin,current,true).length<=500)
    }
    @Test fun changedObservationOffMissingConsentOrDrivingCannotPrepare() {
        val current=assessment();val row=current.active.single()
        assertNull(prepareWarningServiceDraft(row,assessment("false"),true,true,true))
        assertNull(prepareWarningServiceDraft(row.copy(observedAt=now-1),current,true,true,true))
        assertNull(prepareWarningServiceDraft(row,current,false,true,true))
        assertNull(prepareWarningServiceDraft(row,current,true,false,true))
    }
    @Test fun approvalMustBeRepeatedWhenWarningMeaningOrMemoChanges() {
        val current=assessment();val draft=prepareWarningServiceDraft(current.active.single(),current,true,true,true)!!
        val review=ServiceRequestReview(ExperimentVariant.A,"fixture",draft.serviceType,"2026-11-06",ServiceDateParser.normalize("2026-11-06"),warningServiceNotes(draft.notes,draft.origin,current,true),37.5,127.0,true,draft.origin)
        assertFalse(isCurrentServiceRequestReview(review,review.copy(notes=warningServiceNotes(draft.notes,draft.origin,assessment("false"),true))))
        assertFalse(isCurrentServiceRequestReview(review,review.copy(installationId="other")))
        assertFalse(isCurrentServiceRequestReview(review,review.copy(notes="changed")))
    }
}
