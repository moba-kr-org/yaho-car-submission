package ai.yaho.hyundai.core


data class WarningObservation(
    val key: String, val state: String, val value: String?, val observedAt: Long?, val receivedElapsed: Long?,
)
data class WarningGuidance(
    val id: String, val title: String, val meaning: String, val nextStep: String,
    val locator: String, val serviceType: String? = null,
)
data class ActiveVehicleWarning(val key: String, val observedAt: Long, val guidance: WarningGuidance)
data class VehicleWarningAssessment(val active: List<ActiveVehicleWarning>, val checked: Int, val unavailable: Int, val clearedKeys: Set<String> = emptySet())

const val WARNING_REFERENCE_URL = "https://ownersmanual.hyundai.com/full_webhelp/NE1a/2025/en_US/warning_indicator_lights.html"
const val WARNING_REFERENCE_SCOPE = "현대 공식 설명서 · 2025 IONIQ 5 미국 사양 참고 · 현재 차종·연식 일치 미확인"

private const val LOW_WASHER_WARNING_KEY = "Safety.hasLowWasherFluidWarning"
const val POWER_DOWN_WARNING_KEY = "Safety.hasPowerDownWarningIndicator"
private const val SEAT_BELT_WARNING_PREFIX = "Safety.hasSeatBeltNotBuckledWarning."

private val seatBeltWarningKeys = listOf(
    "ROW_1_LEFT", "ROW_1_CENTER", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT",
    "ROW_3_LEFT", "ROW_3_CENTER", "ROW_3_RIGHT",
).map { "$SEAT_BELT_WARNING_PREFIX$it" }

val SUPPORTED_VEHICLE_WARNING_KEYS: Set<String> = setOf(
    LOW_WASHER_WARNING_KEY,
    POWER_DOWN_WARNING_KEY,
) + seatBeltWarningKeys

private val washer = WarningGuidance("washer_low", "워셔액 부족 신호", "SDK가 워셔액 부족 경고를 전달했습니다.",
    "안전하게 정차한 뒤 해당 차량 설명서에 맞는 워셔액 보충 방법을 확인하세요.", "Low washer fluid warning light", "워셔액 경고 점검")
private val power = WarningGuidance("power_limited", "출력 제한 신호", "SDK가 출력 제한 표시를 전달했습니다. 이 신호만으로 고장이나 원인을 확정할 수 없습니다.",
    "급가속을 피하고 계기판 안내를 확인하세요. 주행이 어려우면 안전한 장소에서 지원을 요청하세요.", "Power Down Indicator Light", "출력 제한 상태 점검")
private val belt = WarningGuidance("seat_belt", "안전벨트 경고", "SDK가 해당 좌석의 안전벨트 경고를 전달했습니다. 탑승 여부나 센서 고장은 이 신호만으로 판단하지 않습니다.",
    "탑승자의 안전벨트 착용 상태와 계기판 안내를 확인하세요.", "Seat Belt Warning Light")

private val guidanceByKey = mapOf(
    LOW_WASHER_WARNING_KEY to washer,
    POWER_DOWN_WARNING_KEY to power,
) + seatBeltWarningKeys.associateWith { belt }

fun isSupportedWarningKey(key: String) = key in SUPPORTED_VEHICLE_WARNING_KEYS

fun warningGuidance(key: String): WarningGuidance? = guidanceByKey[key]

fun assessVehicleWarnings(observations: List<WarningObservation>, collecting: Boolean, now: Long, elapsed: Long): VehicleWarningAssessment {
    val groups = observations.filter { it.key in SUPPORTED_VEHICLE_WARNING_KEYS }.groupBy { it.key }
    val active = mutableListOf<ActiveVehicleWarning>()
    var checked = 0
    var unavailable = 0
    val cleared = mutableSetOf<String>()
    for (key in SUPPORTED_VEHICLE_WARNING_KEYS) {
        val values = groups[key].orEmpty()

        val row = values.singleOrNull()
        if (row == null) { unavailable++; continue }
        val at = row.observedAt
        val received = row.receivedElapsed
        if (at == null || received == null) { unavailable++; continue }
        if (!collecting || row.state != "수신" || at <= 0 || now-at !in -5000..30_000 ||
            received < 0 || elapsed-received !in 0..30_000 || row.value !in setOf("true", "false")) {
            unavailable++
            continue
        }
        checked++
        if (row.value == "true") active += ActiveVehicleWarning(key, at, warningGuidance(key)!!) else cleared += key
    }
    return VehicleWarningAssessment(active.sortedWith(compareBy({ when(it.guidance.id){"power_limited"->0;"seat_belt"->1;else->2} },{it.key})),
        checked, unavailable, cleared)
}


data class WarningServiceOrigin(val key: String, val observedAt: Long)


data class ServiceRequestReview(
    val variant: ExperimentVariant,
    val installationId: String?,
    val serviceType: String,
    val requestedDateInput: String,
    val normalizedRequestedDate: String?,
    val notes: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationIsSample: Boolean,
    val warningOrigin: WarningServiceOrigin?,
    val workshopName: String? = null,
)


fun isCurrentServiceRequestReview(approved: ServiceRequestReview, current: ServiceRequestReview): Boolean =
    approved == current

fun warningServiceStatus(origin: WarningServiceOrigin, assessment: VehicleWarningAssessment, collecting: Boolean): String = when {
    !collecting -> "차량 조회 중지 · 현재 경고 상태 미확인"
    assessment.active.any { it.key == origin.key } -> "최근 SDK에서 개별 경고 신호 확인 · 원인 미확인"
    origin.key in assessment.clearedKeys -> "최근 개별 경고 신호 미검출 · 계기판 경고 해제 여부 미확인 · 수리 완료를 뜻하지 않음"
    else -> "개별 경고 신호 만료·미지원·수신 중단 · 현재 상태 미확인"
}

fun warningServiceNotes(notes: String, origin: WarningServiceOrigin?, assessment: VehicleWarningAssessment, collecting: Boolean): String {
    if (origin == null) return notes
    val warningNote = "[경고 기반 점검 초안] 최초 관측 " + java.time.Instant.ofEpochMilli(origin.observedAt) +
        "\n현재 확인: " + warningServiceStatus(origin, assessment, collecting)
    val userNote = notes.trimEnd()

    val kept = userNote.lines().filterNot { it.startsWith("[경고 기반 점검 초안]") || it.startsWith("현재 확인: ") }.joinToString("\n").trimEnd()
    return if (kept.isEmpty()) warningNote else "$kept\n$warningNote"
}
