package ai.yaho.hyundai.core

data class VehicleManualProfile(val model: String, val year: Int, val market: String)

fun parseManualProfile(model: String, year: String, market: String, currentYear: Int): VehicleManualProfile? {
    val clean=model.trim()
    if (clean.length !in 1..40 || clean.any { it.isISOControl() } || market !in setOf("KR","US","OTHER")) return null
    val parsed=year.toIntOrNull() ?: return null
    if (year.length != 4 || parsed !in 1990..currentYear+1) return null
    val canonical=when(clean.uppercase().replace(" ","")) { "아이오닉5","IONIQ5" -> "IONIQ5"; "아이오닉6","IONIQ6" -> "IONIQ6"; else -> clean.uppercase() }
    return VehicleManualProfile(canonical,parsed,market)
}


fun matchesAvailableManual(profile: VehicleManualProfile?) =
    profile?.model == "IONIQ5" && profile.year == 2025 && profile.market == "US"

fun manualApplicabilityLabel(profile: VehicleManualProfile?): String = when {
    profile == null -> "차량 적용 기준 미설정 · 일반 참고"
    matchesAvailableManual(profile) -> "사용자 설정과 참고 문서 일치 · 실차 검증 전"
    else -> "현재 참고 문서와 차종·연식·판매 지역 불일치 · 일반 참고"
}
