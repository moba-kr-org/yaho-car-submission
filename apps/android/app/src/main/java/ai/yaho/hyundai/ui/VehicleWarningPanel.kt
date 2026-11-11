package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun rememberWarningAssessment(snapshot: VehicleSnapshot, consent: Boolean): VehicleWarningAssessment {
    var tick by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) { while (true) { tick++; delay(1000) } }
    return remember(snapshot, consent, tick) {
        assessVehicleWarnings(snapshot.readings.map { WarningObservation(it.key,it.state,it.value,it.observedAt,it.receivedElapsed) },
            consent && snapshot.active, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }
}

@Composable
internal fun VehicleWarningPanel(assessment: VehicleWarningAssessment, manualProfile: VehicleManualProfile?, enabled: Boolean, onPrepareService: (ActiveVehicleWarning) -> Unit) {
    val context = LocalContext.current
    SettingsPanel(title = "차량 경고") {
        if (assessment.active.isEmpty()) SettingsStatusLine(
            if (assessment.checked == 0) "경고 신호 미수신 · 정상으로 보지 않아요" else "최근 수신한 경고 없음",
            if (assessment.checked == 0) null else true)
        assessment.active.forEach { item ->
            Text(item.guidance.title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFFB6342B))
            Text("수신 ${formatKoreanClock(item.observedAt)} · Pleos SDK", style = MaterialTheme.typography.labelMedium)
            Text(if (matchesAvailableManual(manualProfile)) item.guidance.nextStep else "계기판 안내와 해당 차종의 공식 설명서를 먼저 확인해 주세요.",
                style = MaterialTheme.typography.bodyMedium)
            item.guidance.serviceType?.let {
                Button(onClick = { onPrepareService(item) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text("점검 준비")
                }
            }
            SettingsFinePrint("근거") {
                Text(item.guidance.meaning, style = MaterialTheme.typography.bodySmall)
                Text("근거: ${item.guidance.locator}\n$WARNING_REFERENCE_SCOPE", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WARNING_REFERENCE_URL))) } }, enabled = enabled) {
                    Text("공식 설명서 근거 보기")
                }
            }
        }
        SettingsFinePrint("확인 범위") {
            Text("확인 가능한 신호만 표시합니다. 차량 계기판 안내를 우선하세요.", style = MaterialTheme.typography.bodySmall)
            Text(manualApplicabilityLabel(manualProfile), style = MaterialTheme.typography.bodySmall)
            Text("최근 확인 ${assessment.checked}/${SUPPORTED_VEHICLE_WARNING_KEYS.size} · 미검출 ${assessment.clearedKeys.size} · 미확인 ${assessment.unavailable}",
                style = MaterialTheme.typography.bodySmall)
            Text("실제 고장코드 자동 수집은 연동 전입니다. 일부 신호의 미검출만으로 차량 상태나 부품 교체 필요성을 확정할 수 없습니다.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
