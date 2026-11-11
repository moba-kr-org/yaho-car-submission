package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class VehicleWarningPolicyTest {
    private val now = 100_000L
    private val elapsed = 50_000L
    private fun reading(key: String = "Safety.hasLowWasherFluidWarning", value: String? = "true", at: Long? = now, received: Long? = elapsed, state: String = "수신") =
        WarningObservation(key,state,value,at,received)
    private fun assess(vararg rows: WarningObservation, collecting: Boolean = true) = assessVehicleWarnings(rows.toList(),collecting,now,elapsed)

    @Test fun onlyFreshExplicitWarningFlagsBecomeAlerts() {
        val result = assess(reading(),reading("Safety.hasPowerDownWarningIndicator"),reading("Safety.hasSeatBeltNotBuckledWarning.ROW_1_LEFT"))
        assertEquals(3,result.active.size)
        assertEquals("power_limited",result.active.first().guidance.id)
        assertNull(result.active.single { it.guidance.id == "seat_belt" }.guidance.serviceType)
        val off = assess(reading(value="false"))
        assertEquals(1,off.checked); assertTrue(off.active.isEmpty())
    }
    @Test fun missingSupportedKeysRemainUnknownAndAreCounted() {
        assertEquals(11, SUPPORTED_VEHICLE_WARNING_KEYS.size)
        val empty = assess()
        assertEquals(0, empty.checked)
        assertEquals(11, empty.unavailable)

        val oneOff = assess(reading(value = "false"), reading("P0300", "stored code"))
        assertEquals(1, oneOff.checked)
        assertEquals(10, oneOff.unavailable)
        assertEquals(setOf("Safety.hasLowWasherFluidWarning"), oneOff.clearedKeys)
    }
    @Test fun NumericThresholdDtcAndUnrecognizedSeatNeverBecomeWarningFlags() {
        for (row in listOf(reading("Tire.getLowTirePressure.FRONT_LEFT","230.0"),reading("P0300"),reading("Safety.hasSeatBeltNotBuckledWarning.UNKNOWN"),reading(value="1"))) {
            assertTrue(assess(row).active.isEmpty())
        }
    }
    @Test fun missingUnsupportedStaleFutureAndClockRollbackStayUnknown() {
        for (row in listOf(reading(at=null),reading(received=null),reading(state="未対応"),reading(at=now-30_001),reading(at=now+5001),reading(received=elapsed-30_001),reading(received=elapsed+1),reading(value=null))) {
            val result=assess(row); assertTrue(result.active.isEmpty()); assertEquals(0,result.checked)
        }
        assertTrue(assess(reading(),collecting=false).active.isEmpty())
        assertEquals(0,assess().checked)
    }
    @Test fun duplicateOrConflictingEntriesCannotManufactureOrClearAnAlert() {
        val result=assess(reading(),reading(value="false"))
        assertTrue(result.active.isEmpty()); assertEquals(0,result.checked); assertEquals(11,result.unavailable)
    }
    @Test fun expiryAndConsentWithdrawalRemoveExistingAlert() {
        val rows=listOf(reading())
        assertEquals(1,assessVehicleWarnings(rows,true,now,elapsed).active.size)
        assertTrue(assessVehicleWarnings(rows,true,now+30_001,elapsed+30_001).active.isEmpty())
        assertTrue(assessVehicleWarnings(rows,false,now,elapsed).active.isEmpty())
        assertTrue(assessVehicleWarnings(listOf(reading(value="false")),true,now,elapsed).active.isEmpty())
    }
    @Test fun serviceDraftTracksOnOffUnknownAndWithdrawalWithoutClaimingRepair() {
        val origin=WarningServiceOrigin("Safety.hasLowWasherFluidWarning",now)
        val on=assess(reading())
        assertTrue(warningServiceStatus(origin,on,true).contains("개별 경고 신호 확인"))
        val off=assess(reading(value="false"))
        assertTrue(origin.key in off.clearedKeys)
        assertTrue(warningServiceStatus(origin,off,true).contains("개별 경고 신호 미검출"))
        assertTrue(warningServiceStatus(origin,off,true).contains("계기판 경고 해제 여부 미확인"))
        assertTrue(warningServiceStatus(origin,off,true).contains("완료를 뜻하지 않음"))
        val stale=assessVehicleWarnings(listOf(reading()),true,now+30_001,elapsed+30_001)
        assertFalse(origin.key in stale.clearedKeys)
        assertTrue(warningServiceStatus(origin,stale,true).contains("미확인"))
        assertTrue(warningServiceStatus(origin,on,false).contains("조회 중지"))
    }
    @Test fun anotherWarningAndConflictingFlagCannotClearTheOriginal() {
        val origin=WarningServiceOrigin("Safety.hasLowWasherFluidWarning",now)
        for (state in listOf(assess(reading("Safety.hasPowerDownWarningIndicator", "false")),assess(reading(),reading(value="false")))) {
            assertTrue(warningServiceStatus(origin,state,true).contains("미확인"))
            assertFalse(origin.key in state.clearedKeys)
        }
    }
    @Test fun laterFreshObservationUpdatesDraftButPreservesOriginalTimeAndUserNotes() {
        val origin=WarningServiceOrigin("Safety.hasLowWasherFluidWarning",now-1000)
        val notes=warningServiceNotes("사용자 추가 요청",origin,assess(reading()),true)
        assertTrue(notes.startsWith("사용자 추가 요청"))
        assertTrue(notes.contains(java.time.Instant.ofEpochMilli(now-1000).toString()))
        assertTrue(notes.contains("최근 SDK"))
        val withoutUserMemo = warningServiceNotes("   ", origin, assess(reading()), true)
        assertTrue(withoutUserMemo.startsWith("[경고 기반 점검 초안]"))
        assertEquals("일반 점검",warningServiceNotes("일반 점검",null,assess(),false))
    }
    @Test fun nonBooleanAndUnsupportedNeverBecomeAnOffAcknowledgement() {
        for(row in listOf(reading(value="0"),reading(value="false",state="미지원"),reading(value="false",received=null))) {
            assertTrue(assess(row).clearedKeys.isEmpty())
        }
    }

    @Test fun serviceRequestReviewPolicyDetectsChangedApprovedFields() {
        val origin = WarningServiceOrigin("Safety.hasLowWasherFluidWarning", now)
        val on = assess(reading())
        val off = assess(reading(value = "false"))
        val approved = ServiceRequestReview(
            variant = ExperimentVariant.A,
            installationId = "install-a",
            serviceType = "워셔액 경고 점검",
            requestedDateInput = "2026-11-06",
            normalizedRequestedDate = "2026-11-06T09:00+09:00",
            notes = warningServiceNotes("사용자 메모", origin, on, true),
            latitude = 37.5,
            longitude = 127.0,
            locationIsSample = true,
            warningOrigin = origin,
        )

        assertTrue(isCurrentServiceRequestReview(approved, approved.copy()))
        val offNotes = warningServiceNotes("사용자 메모", origin, off, true)
        assertNotEquals(approved.notes, offNotes)
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(notes = offNotes)))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(notes = "사용자 메모 수정")))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(variant = ExperimentVariant.B)))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(installationId = "install-b")))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(serviceType = "일반 점검")))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(requestedDateInput = "2026-11-07")))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(latitude = 37.6)))
        assertFalse(isCurrentServiceRequestReview(approved, approved.copy(locationIsSample = false)))
    }
}
