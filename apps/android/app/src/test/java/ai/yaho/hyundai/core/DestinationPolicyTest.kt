package ai.yaho.hyundai.core
import org.junit.Assert.*
import org.junit.Test

class DestinationPolicyTest {
    private val target=NavigationTarget("목적지",37.5,127.0)
    @Test fun targetRequiresExplicitCoordinateVerificationAndNoPlaceholder() {
        assertNull(navigationTarget("목적지","37.5","127",false))
        for(pair in listOf("NaN" to "127","37" to "Infinity","91" to "127","0" to "0","127" to "37"))assertNull(navigationTarget("목적지",pair.first,pair.second,true))
        assertNull(navigationTarget("bad\nname","37.5","127",true))
        assertEquals(target,navigationTarget("목적지","37.5","127.0",true))
    }
    @Test fun changedTargetExpiredReviewOrClockRollbackRequireNewReview() {
        val state=DestinationState("review",target,checkedElapsed=100)
        assertTrue(destinationReviewValid(state,target,100))
        assertFalse(destinationReviewValid(state,target,15_101))
        assertFalse(destinationReviewValid(state,target,99))
        assertFalse(destinationReviewValid(state,target.copy(latitude=37.6),100))
        assertFalse(destinationReviewValid(state.copy(phase="pending"),target,100))
    }
    @Test fun completionEventIsNotSuccessWithoutMatchingFinalDestination() {
        val evidence=DestinationEvidence(target)
        evidence.routingCompleted();assertFalse(evidence.reconciled())
        evidence.destination(38.0,127.0);assertFalse(evidence.reconciled())
        evidence.destination(37.5,127.0);assertTrue(evidence.reconciled())
    }
    @Test fun matchingDestinationBeforeCompletionAndNonFiniteValuesDoNotConfirm() {
        val evidence=DestinationEvidence(target)
        evidence.destination(37.5,127.0);assertFalse(evidence.reconciled())
        evidence.destination(Double.NaN,127.0);evidence.routingCompleted();assertFalse(evidence.reconciled())
        assertFalse(destinationMatches(target,127.0,37.5))
    }
    @Test fun completionRequiresAReadbackAfterCompletionNotAnOldMatchingSnapshot() {
        val evidence=DestinationEvidence(target)
        evidence.destination(37.5,127.0)
        evidence.routingCompleted()
        assertFalse(evidence.reconciled())
        evidence.destination(37.5,127.0)
        assertTrue(evidence.reconciled())
    }
    @Test fun matchingPlaceIdentityCannotOverrideConflictingCoordinates() {
        val sample=target.copy(poiId="public-example-id")
        val message=destinationConflictReason(sample,38.0,127.0,sample.name,sample.poiId)
        assertTrue(message!!.contains("이름·ID는 같지만"))
        assertNull(destinationConflictReason(sample,target.latitude,target.longitude,sample.name,sample.poiId))
        assertTrue(destinationConflictReason(sample,38.0,127.0,"다른 곳","other")!!.contains("위치와 다릅니다"))
    }
}
