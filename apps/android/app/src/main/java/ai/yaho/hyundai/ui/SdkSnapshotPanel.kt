package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import ai.yaho.hyundai.core.ObservationFreshness
import ai.yaho.hyundai.core.observationFreshness
import ai.yaho.hyundai.core.signalContract
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun SdkSnapshotPanel(snapshot: VehicleSnapshot) {
    val context = LocalContext.current
    val inventory = remember {
        runCatching { JSONObject(context.assets.open("vehicle-sdk-inventory.json").bufferedReader().use { it.readText() }) }.getOrNull()
    }
    var expanded by remember { mutableStateOf(false) }
    val clock by produceState(System.currentTimeMillis() to android.os.SystemClock.elapsedRealtime(), snapshot.active) {
        do {
            value = System.currentTimeMillis() to android.os.SystemClock.elapsedRealtime()
            if (!snapshot.active) break
            delay(1_000)
        } while (true)
    }
    val freshness = snapshot.readings.associate { reading ->
        reading.key to observationFreshness(reading.state, reading.value != null, snapshot.active,
            reading.observedAt, reading.receivedElapsed, clock.first, clock.second)
    }
    val fresh = freshness.values.count { it == ObservationFreshness.FRESH }
    val stale = freshness.values.count { it == ObservationFreshness.STALE }
    val received = snapshot.readings.count { it.state == "수신" }
    val unsupported = snapshot.readings.count { it.state.startsWith("미지원") || it.state == "값 형식 미지원" }
    val denied = snapshot.readings.count { it.state == "권한 부족" || it.state == "읽기 권한 없음" }
    val unknown = snapshot.readings.count { it.state == "조회 실패 · 원인 미확인" }
    val timedOut = snapshot.readings.count { it.state == "시간 초과" }
    SettingsPanel(title = "자동으로 읽는 내 차의 상태") {
        SettingsStatusLine("${snapshot.readings.size}개 중 ${received}개 수신 · ${if (snapshot.active) "갱신 중" else "관측 중지"}", snapshot.active)
        Text("최근 30초 ${fresh}개 · 오래된 값 ${stale}개 · 미지원 ${unsupported}개 · 권한 부족 ${denied}개 · 시간 초과 ${timedOut}개 · 원인 미확인 ${unknown}개", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "항목별 값 접기  ⌃" else "항목별 값 보기  ⌄") }
        if (expanded) {
        Text("SDK ${inventory?.getString("sdkVersion") ?: "확인 중"} · ${inventory?.getJSONArray("domains")?.length() ?: 0}개 영역 · ${inventory?.getJSONArray("methods")?.length() ?: 0}개 함수 서명", style = MaterialTheme.typography.labelMedium)
        Text("부위별 읽기 조회와 앞 와이퍼 5초 조회를 수행합니다. 전체 조회 후 15초 대기하며 미지원·권한 부족·시간 초과를 구분합니다. 수집기는 차량 제어를 하지 않습니다. 네트워크 동의가 있어도 새 좌석·실내 신호는 서버로 보내지 않습니다.", style = MaterialTheme.typography.bodySmall)
        Text("항목 수에는 부위별 조회와 동일 응답의 파생값이 포함됩니다. 함수·항목 수는 센서 수가 아닙니다. 부위별 조회·권한·차종 지원을 따로 확인하며, 현재는 에뮬레이터 검증 단계입니다.", style = MaterialTheme.typography.bodySmall)
            snapshot.readings.groupBy { it.key.substringBefore('.') }.forEach { (domain, readings) ->
                HorizontalDivider()
                Text(domain, fontWeight = FontWeight.Bold)
                readings.forEach { reading ->
                    val contract = signalContract(reading.key)
                    val status = freshness[reading.key] ?: ObservationFreshness.UNAVAILABLE
                    val shownValue = if (status == ObservationFreshness.FRESH) reading.value else null
                    val unit = contract.confirmedUnit?.takeIf { it == reading.unit }
                    Text("${contract.title}${contract.area?.let { " · $it" } ?: ""} · ${shownValue ?: status.label}${if (shownValue != null && unit != null) " · $unit" else ""}", style = MaterialTheme.typography.bodySmall)
                    Text("${contract.meaning.label} · ${status.label} · ${contract.cadence}", style = MaterialTheme.typography.labelSmall)
                    Text("${reading.key} · ${reading.state}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("최근 30초는 앱의 표시 유효기간이며 차량 센서의 보장 주기가 아닙니다. 오래된 값과 관측 중지 값은 현재 수치로 표시하지 않습니다. 수치는 SDK 원시 값입니다. 단위·차종별 해석과 위험 판단은 별도 검증합니다. 식별번호·등록번호·제어 함수는 이 자동 조회에 포함하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
