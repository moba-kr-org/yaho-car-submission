package ai.yaho.hyundai.core

data class NavigationRouteObservation(val state: String = "idle",val mode: String? = null,val receivedElapsed: Long? = null)
fun routeObservationLabel(value: NavigationRouteObservation, elapsed: Long): String {
    if(value.state != "received") return when(value.state) {
        "pending" -> "내비 상태 응답 대기"
        "timeout" -> "응답 시간 초과 · 현재 상태 미확인"
        "error" -> "내비 상태 조회 실패"
        "blocked" -> "현재 조건에서 조회할 수 없습니다."
        else -> "내비 상태를 아직 조회하지 않았습니다."
    }
    if(value.receivedElapsed == null || elapsed-value.receivedElapsed !in 0..30_000) return "이전 내비 응답 만료 · 다시 확인해 주세요."
    return when(value.mode) {
        "NOT_WORKING" -> "내비 안내 비활성 모드"
        "SAFE_GUIDING" -> "내비 안전 안내 모드"
        "ROUTE_GUIDING" -> "내비 경로 안내 모드"
        "SIMULATION_GUIDING" -> "내비 모의 안내 모드"
        else -> "확인되지 않은 내비 모드"
    }
}
