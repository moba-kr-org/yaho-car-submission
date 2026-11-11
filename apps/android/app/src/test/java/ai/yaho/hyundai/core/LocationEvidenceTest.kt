package ai.yaho.hyundai.core
import ai.yaho.hyundai.data.AppConsents
import org.junit.Assert.*
import org.junit.Test

class LocationEvidenceTest {
    private val fix=LocationEvidence(37.5,127.0,100_000,1000,.5f,90f,true)
    @Test fun freshAndTimeAlignedFixRequired() {
        assertTrue(locationMatchesObservation(fix,110_000,110_000,2000))
        assertFalse(locationMatchesObservation(fix,116_000,110_000,2000))
        assertFalse(validLocationEvidence(fix,131_000,2000))
        assertFalse(validLocationEvidence(fix,100_000,31_001))
        assertFalse(validLocationEvidence(fix.copy(latitude=Double.NaN),100_000,2000))
        assertFalse(validLocationEvidence(fix.copy(latitude=0.0,longitude=0.0),100_000,2000))
        assertFalse(validLocationEvidence(fix,94_000,2000))
    }
    @Test fun existingChoicesNeverGrantLocationSharingAndEveryDependencyCanRevokeIt() {
        val old=AppConsents(true,true,true,true,true,true)
        assertFalse(old.liveLocationNetwork)
        val allowed=old.copy(liveLocationNetwork=true).normalized()
        assertTrue(allowed.liveLocationNetwork)
        assertFalse(allowed.copy(location=false).normalized().liveLocationNetwork)
        assertFalse(allowed.copy(network=false).normalized().liveLocationNetwork)
        assertFalse(allowed.copy(vehicleRead=false).normalized().liveLocationNetwork)
    }
}
