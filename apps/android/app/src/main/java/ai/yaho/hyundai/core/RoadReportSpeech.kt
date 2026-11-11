package ai.yaho.hyundai.core

private val reportPhrases = mapOf(
    "accident_report" to listOf("사고", "충돌", "추돌"),
    "slippery_report" to listOf("미끄", "블랙아이스", "살얼음", "결빙"),
    "stalled_vehicle" to listOf("정차차량", "차가서있", "차가멈춰", "멈춘차", "고장차"),
    "road_hazard" to listOf("위험물", "낙하물", "포트홀", "도로파임"),
    "traffic" to listOf("정체", "막혀", "막히"),
    "rain_report" to listOf("강우", "비가와", "비가내", "비와", "빗길", "소나기"),
)


fun roadReportKindFromSpeech(speech: String): String? {
    val normalized = speech.filter(Char::isLetterOrDigit)
    return reportPhrases.entries.singleOrNull { (_, phrases) -> phrases.any(normalized::contains) }?.key
}
