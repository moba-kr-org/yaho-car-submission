package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun NavigationRoutePanel(status: NavigationRouteObservation, enabled: Boolean, onRead: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var tick by remember {mutableLongStateOf(0)}
    LaunchedEffect(Unit){while(true){tick++;delay(1000)}}
    LaunchedEffect(enabled){if(!enabled)confirm=false}
    val label=remember(status,tick){routeObservationLabel(status,android.os.SystemClock.elapsedRealtime())}
    SettingsPanel(title = "내비 연결 확인") {
        SettingsStatusLine(label, null)
        OutlinedButton(onClick={confirm=true},enabled=enabled && status.state!="pending") {Text("내비 상태 1회 확인")}
        SettingsFinePrint {
            Text("현재 안내 모드만 1회 확인합니다. 목적지·주소·최근 방문 목록은 읽거나 저장하지 않으며 기존 경로를 바꾸지 않습니다.",style=MaterialTheme.typography.bodySmall)
            Text("응답 수신 기준 참고 상태입니다. SDK의 측정 시각은 제공되지 않아 실시간 경로 검증으로 사용하지 않습니다. 목적지·경유지 실행 연결은 다음 단계입니다.",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirm && enabled) AlertDialog(onDismissRequest={confirm=false},title={Text("내비 상태를 확인할까요?")},
        text={Text("내비 서비스의 현재 안내 모드를 1회 읽어 이 화면에 표시합니다. 서버 전송·목적지 변경·경로 취소는 하지 않습니다.")},
        confirmButton={TextButton(onClick={confirm=false;if(enabled)onRead()}){Text("1회 확인")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("돌아가기")}})
}
