package ai.yaho.hyundai.core

import ai.yaho.hyundai.vehicle.snapshotErrorState
import ai.yaho.hyundai.vehicle.UnsupportedSignalAreaException
import ai.yaho.hyundai.vehicle.SignalAccessException
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotErrorStateTest {
    @Test fun permissionFailureIsNotUnsupportedOrAZeroReading() {
        assertEquals("권한 부족", snapshotErrorState(SecurityException("denied")))
    }
    @Test fun unspecifiedFailureDoesNotClaimUnsupportedOrPermissionDenial() {
        assertEquals("조회 실패 · 원인 미확인", snapshotErrorState(IllegalStateException("service fault")))
    }
    @Test fun capabilityRefusalDistinguishesAbsentAreasFromReadAccess() {
        assertEquals("미지원 부위", snapshotErrorState(UnsupportedSignalAreaException()))
        assertEquals("미지원 기능", snapshotErrorState(SignalAccessException("NONE")))
        assertEquals("읽기 권한 없음", snapshotErrorState(SignalAccessException("WRITE")))
    }
}
