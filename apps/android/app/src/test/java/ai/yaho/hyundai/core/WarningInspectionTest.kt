package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class WarningInspectionTest {
    private val now = 1_800_000_000_000L
    private val power = ActiveVehicleWarning(POWER_DOWN_WARNING_KEY, now - 2_000, warningGuidance(POWER_DOWN_WARNING_KEY)!!)

    private val none = WarningPresence.None
    private val live = WarningPresence.Live(power)

    @Test fun warningAlwaysOutranksRain() {
        assertTrue(selectHomeNotice(live, none, "비 소식이 있습니다", 3) is HomeNotice.Warning)
        assertEquals(WarningSource.DEMO, (selectHomeNotice(none, live, "비", 3) as HomeNotice.Warning).source)
    }

    @Test fun sdkWarningOutranksDemo() {
        assertEquals(WarningSource.SDK, (selectHomeNotice(live, live, null, 3) as HomeNotice.Warning).source)
    }

    @Test fun recentMissingWarningOutranksRainAndIsNotIdle() {
        val notice = selectHomeNotice(WarningPresence.Missing(power, prominent = true), none, "비 소식", 3)
        assertTrue(notice is HomeNotice.StaleWarning)
    }

    @Test fun oldMissingWarningYieldsHomeButIsNeverNormal() {
        val old = WarningPresence.Missing(power, prominent = false)
        assertEquals(HomeNotice.Rain("비"), selectHomeNotice(old, none, "비", 3))
        val idle = selectHomeNotice(old, none, null, 3) as HomeNotice.Idle
        assertEquals(IdleKind.LOST, idle.kind)
        assertEquals("출력 제한 신호 · 수신 끊김", idle.title)
        assertTrue(idle.signalState.contains("현재 상태 미확인"))
        assertFalse(idle.signalState.contains("경고 없음"))
    }

    @Test fun rainShowsOnlyWithoutWarning() {
        assertEquals(HomeNotice.Rain("비"), selectHomeNotice(none, none, "비", 3))
    }

    @Test fun unreceivedSignalsAreNotShownAsNormal() {
        val unreceived = selectHomeNotice(none, none, null, 0) as HomeNotice.Idle
        assertEquals(IdleKind.UNRECEIVED, unreceived.kind)
        assertFalse(unreceived.title.contains("없음") && !unreceived.title.contains("미수신"))
        val off = selectHomeNotice(WarningPresence.ConsentOff, none, null, 5) as HomeNotice.Idle
        assertEquals("차량 조회 꺼짐", off.title)
        val ok = selectHomeNotice(none, none, null, 5) as HomeNotice.Idle
        assertEquals(IdleKind.QUIET, ok.kind)
        assertTrue(ok.signalState.startsWith("최근 수신한 경고 없음"))
    }

    private fun seen(w: ActiveVehicleWarning) = WarningTrack(WarningPresence.Live(w), w)
    private val empty = WarningTrack(WarningPresence.None, null)

    @Test fun explicitClearForgetsWarningAndStaysClearedWhileFalseArrives() {
        val cleared = setOf(POWER_DOWN_WARNING_KEY)
        val track = trackWarning(null, cleared, true, seen(power), now)
        assertEquals(WarningPresence.Cleared, track.presence)
        assertNull(track.remembered)
        val idle = selectHomeNotice(track.presence, none, null, 3) as HomeNotice.Idle
        assertEquals(IdleKind.CLEARED, idle.kind)
        assertEquals(WarningSource.SDK, idle.source)

        assertEquals(WarningPresence.Cleared, trackWarning(null, cleared, true, track, now + 1_000).presence)

        assertEquals(WarningPresence.None, trackWarning(null, emptySet(), true, track, now + 2_000).presence)
    }

    @Test fun temporaryNonReceptionKeepsWarningAsUnknown() {
        val recent = trackWarning(null, emptySet(), true, seen(power), now)
        assertEquals(WarningPresence.Missing(power, true), recent.presence)
        assertEquals(power, recent.remembered)
        val old = trackWarning(null, emptySet(), true, recent, power.observedAt + STALE_WARNING_PROMINENT_MS + 1)
        assertEquals(WarningPresence.Missing(power, false), old.presence)
    }

    @Test fun consentWithdrawalForgetsEvenALiveWarning() {
        val track = trackWarning(power, emptySet(), false, seen(power), now)
        assertEquals(WarningPresence.ConsentOff, track.presence)
        assertNull(track.remembered)
    }

    @Test fun liveWarningIsRemembered() {
        assertEquals(WarningTrack(live, power), trackWarning(power, emptySet(), true, empty, now))
        assertEquals(WarningPresence.None, trackWarning(null, emptySet(), true, empty, now).presence)
    }

    @Test fun drivingLineHasNoActionWording() {
        assertEquals("계기판 안내를 먼저 따르세요", DRIVING_WARNING_LINE)
    }

    @Test fun exclusiveSymptomsReplaceConcreteOnes() {
        val picked = toggleSymptom(toggleSymptom(emptyList(), "weak_accel"), "smell")
        assertEquals(listOf("weak_accel", "smell"), picked)
        assertEquals(listOf("unsure"), toggleSymptom(picked, "unsure"))
        assertEquals(listOf("weak_accel"), toggleSymptom(listOf("unsure"), "weak_accel"))
        assertEquals(emptyList<String>(), toggleSymptom(listOf("weak_accel"), "weak_accel"))
    }

    @Test fun summarySeparatesSourcesAndLabelsDemo() {
        val summary = buildInspectionSummary(power, WarningSource.DEMO, listOf("weak_accel"), true, now)
        assertTrue(summary.vehicleSignal.first().contains("가상 신호"))
        assertFalse(summary.vehicleSignal.any { it.contains(POWER_DOWN_WARNING_KEY) })
        assertTrue(summary.vehicleSignal.any { it.startsWith("수신 ") && it.contains("2초 전") })
        assertEquals(listOf("가속이 평소보다 약함"), summary.driverInput)
        assertTrue(summary.unknown.any { it.contains("DTC") })
    }

    @Test fun demoNeverClaimsSdkDelivery() {
        assertFalse(warningMeaningFor(power, WarningSource.DEMO).startsWith("SDK가"))
        val summary = buildInspectionSummary(power, WarningSource.DEMO, emptyList(), true, now)
        assertTrue(summary.vehicleSignal.any { it.contains("실제 차량 신호 아님") })
        assertTrue(warningMeaningFor(power, WarningSource.SDK).contains("원인"))
    }

    @Test fun staleSummaryDoesNotClaimLiveSignal() {
        val summary = buildInspectionSummary(power, WarningSource.SDK, emptyList(), false, now)
        assertTrue(summary.vehicleSignal.any { it.contains("현재 상태 미확인") })
        assertFalse(summary.vehicleSignal.any { it.contains("수신 중") })
        assertEquals(listOf("아직 선택하지 않음"), summary.driverInput)
    }

    @Test fun summaryTextHasNoRepeatedLines() {
        val text = buildInspectionSummary(power, WarningSource.SDK, listOf("none"), true, now).asText()
        val lines = text.lines()
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test fun dedupeSentencesKeepsFirstOccurrence() {
        assertEquals("원인 미확인. 점검 필요.", dedupeSentences("원인 미확인. 점검 필요.\n원인 미확인."))
    }

    @Test fun koreanDateFormatting() {
        assertEquals("11월 11일 오전 9시", formatKoreanDateTime("2026-11-11T09:00+09:00"))
        assertEquals("11월 11일 오후 2시 30분", formatKoreanDateTime("2026-11-11T05:30:00Z"))
        assertEquals("11월 11일 오전 9시", formatKoreanDateTime("2026-11-11"))
        assertEquals("내일 아침", formatKoreanDateTime("내일 아침"))
        assertEquals("희망 일시 미입력", formatKoreanDateTime(" "))
    }

    @Test fun warningNotesDoNotStackOnRepeatedReview() {
        val origin = WarningServiceOrigin(POWER_DOWN_WARNING_KEY, now - 2_000)
        val assessment = VehicleWarningAssessment(listOf(power), 1, 0)
        val once = warningServiceNotes("메모", origin, assessment, true)
        val twice = warningServiceNotes(once, origin, assessment, true)
        assertEquals(once, twice)
    }
}
