package ai.yaho.hyundai.core

enum class NetworkLoadState { WAITING, RECEIVED, EMPTY, OFFLINE, TIMEOUT }

enum class NetworkAccessState { READY, SHARING_OFF, SERVER_NOT_CONFIGURED, INSTALL_REQUIRED, LOCATION_REQUIRED }

fun networkAccessState(configured: Boolean, installed: Boolean, sharing: Boolean, locationReady: Boolean): NetworkAccessState = when {
    !sharing -> NetworkAccessState.SHARING_OFF
    !configured -> NetworkAccessState.SERVER_NOT_CONFIGURED
    !installed -> NetworkAccessState.INSTALL_REQUIRED
    !locationReady -> NetworkAccessState.LOCATION_REQUIRED
    else -> NetworkAccessState.READY
}

data class NetworkDisplayState(val showAssessment: Boolean, val participantLabel: String, val status: String)


fun networkDisplayState(load: NetworkLoadState, fresh: Boolean, participants: Int, access: NetworkAccessState = NetworkAccessState.READY): NetworkDisplayState {
    if (access != NetworkAccessState.READY) return NetworkDisplayState(false, "미확인", when (access) {
        NetworkAccessState.SHARING_OFF -> "네트워크 공유 꺼짐 · 새 관측과 위치를 보내지 않습니다."
        NetworkAccessState.SERVER_NOT_CONFIGURED -> "중앙 서버 연동 전 · 집단 판단을 확인할 수 없습니다."
        NetworkAccessState.INSTALL_REQUIRED -> "설치 연결 필요 · 아직 전송하지 않았습니다."
        NetworkAccessState.LOCATION_REQUIRED -> "신선한 위치·권한 확인 필요 · 샘플 위치로 대체하지 않습니다."
        NetworkAccessState.READY -> error("unreachable")
    })
    if (load == NetworkLoadState.RECEIVED && fresh) return NetworkDisplayState(true, "${participants.coerceAtLeast(0)}곳", "최신 집계")
    return NetworkDisplayState(false, "미확인", when (load) {
        NetworkLoadState.WAITING -> "새 집계를 기다립니다."
        NetworkLoadState.EMPTY -> "유효한 주변 관측이 없습니다. 주변 차량이 없다는 뜻은 아닙니다."
        NetworkLoadState.OFFLINE -> "서버 연결 실패 · 최신 상태 미확인"
        NetworkLoadState.TIMEOUT -> "서버 응답 지연 · 최신 상태 미확인"
        NetworkLoadState.RECEIVED -> "이전 집계 만료 · 새 관측을 기다립니다."
    })
}
