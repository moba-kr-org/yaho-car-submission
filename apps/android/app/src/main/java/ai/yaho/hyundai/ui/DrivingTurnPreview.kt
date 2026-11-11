package ai.yaho.hyundai.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.yaho.hyundai.vehicle.NavigationTurn
import kotlinx.coroutines.delay

internal enum class TurnPreviewMode { OFF, SDK, MAP }

@Composable
internal fun TurnPreviewSettings(mode: TurnPreviewMode, onChange: (TurnPreviewMode) -> Unit) {
    SettingsPanel(title = "주행 화면", category = "에뮬레이터") {
        Text("D단에서 실제 지도와 서울시청–종로3가 경로를 표시합니다. SDK 확인은 이미 안내 중인 Pleos 경로의 회전 정보를 읽습니다.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                TurnPreviewMode.OFF to "끄기",
                TurnPreviewMode.SDK to "SDK 응답 확인",
                TurnPreviewMode.MAP to "실제 지도",
            ).forEach { (option, label) ->
                FilterChip(
                    selected = mode == option,
                    onClick = { onChange(option) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
internal fun DrivingTurnPreview(turn: NavigationTurn?, modifier: Modifier = Modifier) {
    var expired by remember(turn) { mutableStateOf(false) }
    LaunchedEffect(turn) {
        if (turn != null) {
            delay((turn.receivedAtElapsed + 5_000 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            expired = true
        }
    }
    val direction = turn?.direction?.takeUnless { expired }
    val distance = turn?.let { "${it.sdkDistance}" }
    Surface(modifier, color = Color(0xFF24221F), contentColor = Color.White, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("내비 안내 · SDK 확인", color = Color(0xFFFFC490), fontSize = 23.sp)
            if (direction == null || distance == null) {
                Text("받은 회전 안내가 없어요", fontSize = 28.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(direction.arrow, color = Color(0xFFFFAD65), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                    Text("$distance · ${direction.label}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Text("SDK 거리값 · 단위 검증 중", color = Color(0xFFFFC490), fontSize = 20.sp)
            }
        }
    }
}
