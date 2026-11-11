package ai.yaho.hyundai.core


data class WiperStateProjection(
    val enumName: String?,
    val active: Boolean?,
)

fun projectWiperState(enumName: String?): WiperStateProjection = WiperStateProjection(
    enumName = enumName,
    active = when (enumName) {
        "ON" -> true
        "OFF" -> false
        else -> null
    },
)
