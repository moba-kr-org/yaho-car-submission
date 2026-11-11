package ai.yaho.hyundai

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ai.yaho.hyundai.ui.RainNoticeCard


class SpeechProbeActivity : ComponentActivity() {
    var rainNotice by mutableStateOf<String?>(null)
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        setContent {
            ai.yaho.hyundai.ui.YahoHyundaiTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val text = rainNotice
                        if (intent.getBooleanExtra("review_dialog_fixture", false)) {
                            AlertDialog(
                                onDismissRequest = { finish() },
                                title = { Text("정비 요청 검토 · 합성 화면") },
                                text = { ai.yaho.hyundai.ui.ReviewDialogBody(
                                    "차량 점검\n희망 일시 2026.11.07 09:00 (한국시간)\n" +
                                    "메모: " + "합성 경고 관측입니다. 원인은 확정되지 않았으며 차종별 점검이 필요합니다. ".repeat(10) +
                                    "\n업체 연동 전입니다. 예약은 확정되지 않았습니다.") },
                                confirmButton = { Button(onClick = {}, modifier = Modifier.heightIn(min = 64.dp)) { Text("시험 화면 · 전송 없음") } },
                                dismissButton = { TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 64.dp)) { Text("닫기") } },
                            )
                        } else if (text == null) Text("YAHO · 로컬 합성 관측 / 음성 연결 시험")
                        else RainNoticeCard(text, false, {}, { rainNotice = null }, Modifier.widthIn(max = 640.dp).padding(24.dp))
                    }
                }
            }
        }
    }
}
