package ai.yaho.hyundai.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val HYUNDAI_SERVICE_NETWORK_URL = "https://www.hyundai.com/kr/ko/customer-support/service-network"
private val koreaZone: ZoneId = ZoneId.of("Asia/Seoul")


enum class WarningSource(val label: String) { SDK("Pleos SDK"), DEMO("가상 신호") }


const val STALE_WARNING_PROMINENT_MS = 120_000L


sealed interface HomeNotice {
    data class Warning(val warning: ActiveVehicleWarning, val source: WarningSource) : HomeNotice

    data class StaleWarning(val title: String, val lastObservedAt: Long, val source: WarningSource) : HomeNotice
    data class Rain(val text: String) : HomeNotice

    data class Idle(val kind: IdleKind, val title: String, val signalState: String, val source: WarningSource? = null) : HomeNotice
}

enum class IdleKind { QUIET, UNRECEIVED, CONSENT_OFF, CLEARED, LOST }


fun lostTitle(title: String) = "$title · 수신 끊김"


sealed interface WarningPresence {
    data class Live(val warning: ActiveVehicleWarning) : WarningPresence

    data class Missing(val last: ActiveVehicleWarning, val prominent: Boolean) : WarningPresence

    data object Cleared : WarningPresence

    data object ConsentOff : WarningPresence
    data object None : WarningPresence
}


data class WarningTrack(val presence: WarningPresence, val remembered: ActiveVehicleWarning?, val cleared: ActiveVehicleWarning? = null)





fun trackWarning(
    active: ActiveVehicleWarning?, clearedKeys: Set<String>, consentOn: Boolean,
    previous: WarningTrack, now: Long,
): WarningTrack {
    val remembered = previous.remembered
    val cleared = previous.cleared
    return when {
        !consentOn -> WarningTrack(WarningPresence.ConsentOff, null)
        active != null -> WarningTrack(WarningPresence.Live(active), active)
        remembered != null && remembered.key in clearedKeys -> WarningTrack(WarningPresence.Cleared, null, remembered)
        remembered != null -> WarningTrack(WarningPresence.Missing(remembered, now - remembered.observedAt <= STALE_WARNING_PROMINENT_MS), remembered)
        cleared != null && cleared.key in clearedKeys -> WarningTrack(WarningPresence.Cleared, null, cleared)
        else -> WarningTrack(WarningPresence.None, null)
    }
}

fun selectHomeNotice(
    sdk: WarningPresence, demo: WarningPresence, rainBanner: String?, checked: Int,
): HomeNotice {
    (sdk as? WarningPresence.Live)?.let { return HomeNotice.Warning(it.warning, WarningSource.SDK) }
    (demo as? WarningPresence.Live)?.let { return HomeNotice.Warning(it.warning, WarningSource.DEMO) }
    for ((presence, source) in listOf(sdk to WarningSource.SDK, demo to WarningSource.DEMO)) {
        val missing = presence as? WarningPresence.Missing ?: continue
        if (missing.prominent) return HomeNotice.StaleWarning(missing.last.guidance.title, missing.last.observedAt, source)
    }
    if (rainBanner != null) return HomeNotice.Rain(rainBanner)
    val pairs = listOf(sdk to WarningSource.SDK, demo to WarningSource.DEMO)
    val lost = pairs.firstOrNull { it.first is WarningPresence.Missing }
    val cleared = pairs.firstOrNull { it.first == WarningPresence.Cleared }
    return when {
        sdk == WarningPresence.ConsentOff -> HomeNotice.Idle(IdleKind.CONSENT_OFF, "차량 조회 꺼짐",
            "권한에서 차량 조회를 켜 주세요")
        lost != null -> (lost.first as WarningPresence.Missing).last.let {
            HomeNotice.Idle(IdleKind.LOST, lostTitle(it.guidance.title),
                "마지막 수신 ${formatKoreanClock(it.observedAt)} · 현재 상태 미확인", lost.second)
        }
        cleared != null -> HomeNotice.Idle(IdleKind.CLEARED, "경고 해제됨",
            "해제 신호 수신 · 계기판도 확인하세요", cleared.second)
        checked == 0 -> HomeNotice.Idle(IdleKind.UNRECEIVED, "경고 신호 미수신",
            "정상으로 보지 않아요 · 계기판을 확인하세요")
        else -> HomeNotice.Idle(IdleKind.QUIET, "경고 없음",
            "최근 수신한 경고 없음 · 경고가 오면 여기서 알려드려요")
    }
}


fun warningMeaningFor(warning: ActiveVehicleWarning, source: WarningSource): String =
    if (source == WarningSource.DEMO) "가상 신호예요. 실제 차에선 같은 SDK 신호로 이렇게 안내해요."
    else "원인은 아직 확인 전이에요. 증상을 골라 상담을 준비하세요."


const val DRIVING_WARNING_LINE = "계기판 안내를 먼저 따르세요"

data class SymptomOption(val id: String, val label: String)

private val commonSymptoms = listOf(
    SymptomOption("other_light", "계기판에 다른 경고도 켜짐"),
    SymptomOption("noise", "평소와 다른 소음·진동"),
    SymptomOption("none", "느껴지는 증상 없음"),
    SymptomOption("unsure", "잘 모르겠음"),
)

fun symptomOptions(guidanceId: String): List<SymptomOption> = when (guidanceId) {
    "power_limited" -> listOf(
        SymptomOption("weak_accel", "가속이 평소보다 약함"),
        SymptomOption("speed_limited", "속도가 잘 오르지 않음"),
        SymptomOption("smell", "타는 냄새·이상한 냄새"),
    )
    "washer_low" -> listOf(
        SymptomOption("no_spray", "워셔액이 안 나오거나 약함"),
        SymptomOption("refilled", "최근 워셔액을 보충함"),
    )
    "seat_belt" -> listOf(
        SymptomOption("buckled_still", "착용했는데도 경고가 켜짐"),
        SymptomOption("empty_seat", "빈 좌석에 짐이 있음"),
    )
    else -> emptyList()
} + commonSymptoms


fun toggleSymptom(selected: List<String>, id: String): List<String> = when {
    id in selected -> selected - id
    id == "none" || id == "unsure" -> listOf(id)
    else -> selected.filterNot { it == "none" || it == "unsure" } + id
}

data class InspectionSummary(
    val vehicleSignal: List<String>,
    val driverInput: List<String>,
    val unknown: List<String>,
    val signalCurrent: Boolean,
) {

    fun asText(): String = dedupeLines(
        listOf("[차량 신호]") + vehicleSignal + listOf("[운전자 입력]") + driverInput + listOf("[미확인]") + unknown,
    ).joinToString("\n")
}

fun buildInspectionSummary(
    warning: ActiveVehicleWarning, source: WarningSource, selectedSymptoms: List<String>,
    signalCurrent: Boolean, now: Long,
): InspectionSummary {
    val labels = symptomOptions(warning.guidance.id).associate { it.id to it.label }
    val ageSeconds = ((now - warning.observedAt) / 1000).coerceAtLeast(0)
    val vehicle = listOf(
        "${warning.guidance.title} · ${source.label}",
        if (source == WarningSource.DEMO) "실제 차량 신호 아님" else "Pleos SDK에서 수신",
        "수신 ${formatKoreanClock(warning.observedAt)} (${ageSeconds}초 전)",
        if (signalCurrent) "현재 경고 신호 수신 중 · 원인 미확인"
        else "최근 30초 안에 새 신호 없음 · 현재 상태 미확인",
    )
    val driver = selectedSymptoms.mapNotNull { labels[it] }.ifEmpty { listOf("아직 선택하지 않음") }
    val unknown = listOf(
        "경고 원인과 고장 여부",
        "고장코드(DTC) · 자동 수신 미연동",
        "타이어 공기압 · 계기판에서 확인",
        "차종·연식별 설명서 일치 여부",
    )
    return InspectionSummary(vehicle, driver, unknown, signalCurrent)
}


fun dedupeLines(lines: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return lines.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it) }
}


fun dedupeSentences(text: String): String {
    val sentences = text.split("\n").flatMap { line ->
        Regex("[^.!?]+[.!?]?").findAll(line).map { it.value.trim() }.toList()
    }
    return dedupeLines(sentences).joinToString(" ")
}

fun formatKoreanClock(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(koreaZone)
    return "${amPm(time)} ${hour12(time)}:${"%02d".format(time.minute)}:${"%02d".format(time.second)}"
}


fun formatKoreanDateTime(input: String?): String {
    val raw = input?.trim().orEmpty()
    if (raw.isEmpty()) return "희망 일시 미입력"
    val time = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(koreaZone) }.getOrNull()
        ?: runCatching { Instant.parse(raw).atZone(koreaZone) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(raw).atTime(9, 0).atZone(koreaZone) }.getOrNull()
        ?: return raw
    val minute = if (time.minute == 0) "" else " ${time.minute}분"
    return "${time.monthValue}월 ${time.dayOfMonth}일 ${amPm(time)} ${hour12(time)}시$minute"
}

private fun amPm(time: ZonedDateTime) = if (time.hour < 12) "오전" else "오후"
private fun hour12(time: ZonedDateTime) = (time.hour % 12).let { if (it == 0) 12 else it }
