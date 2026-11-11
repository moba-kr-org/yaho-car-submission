package ai.yaho.hyundai.vehicle

internal class UnsupportedSignalAreaException : Exception()
internal class SignalAccessException(val access: String) : Exception()


internal fun snapshotErrorState(error: Exception): String {
    if (error is SecurityException) return "권한 부족"
    if (error is UnsupportedSignalAreaException) return "미지원 부위"
    if (error is SignalAccessException) return if (error.access == "NONE") "미지원 기능" else "읽기 권한 없음"
    val code = snapshotErrorCode(error)
    return when (code) {
        "ERROR_SECURITY_PERMISSION" -> "권한 부족"
        "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA" -> "미지원"
        "ERROR_NOT_SUPPORTED_VALUE_TYPE" -> "값 형식 미지원"
        "ERROR_DRIVING_RESTRICTED" -> "주행 중 제한"
        "ERROR_EMPTY_RESULT" -> "값 미제공"
        "ERROR_SERVICE_CONNECTION_FAILURE" -> "서비스 연결 실패"
        "ERROR_INTERNAL_SERVICE" -> "서비스 오류"
        else -> "조회 실패 · 원인 미확인"
    }
}


internal fun snapshotErrorCode(error: Exception): String? = runCatching {
    error.javaClass.methods.firstOrNull {
        it.parameterCount == 0 && it.returnType.name == "ai.pleos.playground.vehicle.constant.ErrorCode"
    }?.invoke(error)?.let { (it as? Enum<*>)?.name }
}.getOrNull()?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) }
