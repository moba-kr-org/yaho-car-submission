package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.ActiveVehicleWarning
import ai.yaho.hyundai.core.POWER_DOWN_WARNING_KEY
import ai.yaho.hyundai.core.warningGuidance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay






@Composable
internal fun rememberDemoWarning(): DemoWarningState {
    val sequence = remember { listOf(DemoWarningMode.OFF, DemoWarningMode.LIVE, DemoWarningMode.MISSING, DemoWarningMode.LIVE, DemoWarningMode.CLEARED) }
    var step by remember { mutableIntStateOf(0) }
    val mode = sequence[step]
    var lastObservedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode) {
        while (mode == DemoWarningMode.LIVE) {
            lastObservedAt = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val guidance = remember { warningGuidance(POWER_DOWN_WARNING_KEY)!! }
    return DemoWarningState(
        available = true,
        mode = mode,
        live = if (mode == DemoWarningMode.LIVE && lastObservedAt > 0) ActiveVehicleWarning(POWER_DOWN_WARNING_KEY, lastObservedAt, guidance) else null,
        clearedKeys = if (mode == DemoWarningMode.CLEARED) setOf(POWER_DOWN_WARNING_KEY) else emptySet(),
        nextMode = sequence[(step + 1) % sequence.size],
        cycle = {
            step = (step + 1) % sequence.size
            if (sequence[step] == DemoWarningMode.LIVE) lastObservedAt = System.currentTimeMillis()
        },
    )
}


private fun demoActionLabel(mode: DemoWarningMode, next: DemoWarningMode) = when {
    mode == DemoWarningMode.OFF -> "가상 경고 켜기"
    next == DemoWarningMode.MISSING -> "가상 · 수신 끊기"
    mode == DemoWarningMode.MISSING -> "가상 · 다시 수신"
    next == DemoWarningMode.CLEARED -> "가상 · 해제 신호"
    else -> "가상 경고 끄기"
}


@Composable
internal fun DemoWarningButton(state: DemoWarningState, modifier: Modifier = Modifier) {
    val label = demoActionLabel(state.mode, state.nextMode)
    Surface(
        onClick = state.cycle,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(24.dp),

        color = Color.White,
        contentColor = Color(0xFF5E5650),
        border = BorderStroke(1.5.dp, Color(0xFFCDBFB3)),
    ) {
        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
