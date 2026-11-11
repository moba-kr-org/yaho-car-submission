package ai.yaho.hyundai.vehicle

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo

internal enum class TurnDirection(val arrow: String, val label: String) {
    LEFT("↰", "좌회전"),
    RIGHT("↱", "우회전"),
    STRAIGHT("↑", "직진"),
    KEEP_LEFT("↖", "왼쪽으로"),
    KEEP_RIGHT("↗", "오른쪽으로"),
    U_TURN("↶", "유턴"),
    ARRIVE("●", "목적지 도착"),
}

internal data class NavigationTurn(
    val direction: TurnDirection,
    val sdkDistance: Double,
    val receivedAtElapsed: Long,
)

internal fun TBTInfo.toNavigationTurn(receivedAtElapsed: Long): NavigationTurn? {
    if (!distance.isFinite() || distance < 0) return null
    val direction = when (type) {
        TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnDirection.LEFT
        TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnDirection.RIGHT
        TurnType.TURN_STRAIGHT -> TurnDirection.STRAIGHT
        TurnType.TURN_KEEP_L -> TurnDirection.KEEP_LEFT
        TurnType.TURN_KEEP_R -> TurnDirection.KEEP_RIGHT
        TurnType.TURN_UTURN -> TurnDirection.U_TURN
        TurnType.TURN_ARRIVE -> TurnDirection.ARRIVE
        else -> return null
    }
    return NavigationTurn(direction, distance, receivedAtElapsed)
}
