package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class NetworkFreshnessTest {
    @Test fun onlyUnexpiredEvidenceAndRecentReceiptAreDisplayed() {
        assertTrue(isNetworkAssessmentFresh(120_000, 100_000, 1000, 2000))
        assertFalse(isNetworkAssessmentFresh(null, 100_000, 1000, 2000))
        assertFalse(isNetworkAssessmentFresh(100_000, 100_000, 1000, 2000))
        assertFalse(isNetworkAssessmentFresh(120_000, 100_000, null, 2000))
        assertFalse(isNetworkAssessmentFresh(150_000, 100_000, 1000, 31_000))
    }
    @Test fun clockRollbackCannotExtendTheMonotonicReceiptBudget() {
        assertFalse(isNetworkAssessmentFresh(150_000, 50_000, 1000, 31_000))
        assertFalse(isNetworkAssessmentFresh(150_000, 50_000, 2000, 1000))
    }
}
