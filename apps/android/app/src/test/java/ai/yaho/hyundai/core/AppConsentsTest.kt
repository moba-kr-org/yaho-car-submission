package ai.yaho.hyundai.core

import ai.yaho.hyundai.data.AppConsents
import org.junit.Assert.*
import org.junit.Test

class AppConsentsTest {
    @Test fun newInstallHasNoImplicitOptionalPermission() {
        val value = AppConsents()
        assertFalse(value.reviewed)
        assertFalse(value.vehicleRead || value.location || value.manualControl || value.network)
    }
    @Test fun withdrawingVehicleReadAlsoDisablesDependentSharingAndControls() {
        val approved = AppConsents(true, true, true, true, true)
        val revoked = approved.copy(vehicleRead = false).normalized()
        assertFalse(revoked.manualControl)
        assertFalse(revoked.network)
        assertTrue(revoked.location)
    }
    @Test fun localObservationDoesNotAuthorizeTransmissionOrActuation() {
        val localOnly = AppConsents(reviewed = true, vehicleRead = true).normalized()
        assertTrue(localOnly.vehicleRead)
        assertFalse(localOnly.network)
        assertFalse(localOnly.manualControl)
        assertFalse(localOnly.location)
    }
    @Test fun locationAndNetworkCanBeWithdrawnIndependently() {
        val approved = AppConsents(true, true, true, true, true)
        assertTrue(approved.copy(location = false).normalized().network)
        assertTrue(approved.copy(network = false).normalized().location)
        assertTrue(approved.copy(manualControl = false).normalized().vehicleRead)
    }
}
