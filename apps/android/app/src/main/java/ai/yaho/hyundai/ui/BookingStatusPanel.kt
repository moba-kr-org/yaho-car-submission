package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.bookingStatusDisplay
import ai.yaho.hyundai.data.*
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
internal fun BookingStatusPanel(backend: BackendApi, variant: ExperimentVariant, revision: Int, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    val controller = remember(backend,variant) { BookingHistoryController(object : BookingHistorySource {
        override suspend fun list() = backend.fetchBookings(variant)
        override fun hasPending() = backend.hasPendingBooking(variant)
        override suspend fun recover() = backend.recoverPendingBooking(variant)
        override suspend fun cancel(id: String) = backend.cancelBooking(variant,id,approved = true)
    },SystemClock::elapsedRealtime) }
    val history by controller.state.collectAsState()
    var tick by remember { mutableLongStateOf(0) }
    var selected by remember(controller) { mutableStateOf<BookingStatus?>(null) }
    val fresh = remember(history.checkedAt,tick) { controller.isFresh() }
    LaunchedEffect(controller,revision,enabled) {
        selected = null
        controller.invalidate()
        if (enabled) controller.refresh()
    }
    DisposableEffect(controller) { onDispose { controller.invalidate() } }
    LaunchedEffect(fresh) { if (!fresh) selected = null }
    LaunchedEffect(Unit) { while (true) { tick++; delay(1000) } }
    SettingsPanel(title = "최근 점검 요청") {
        Text("최근 20건 · 접수와 예약 확정은 달라요",style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { if (enabled) scope.launch { controller.refresh() } },enabled = enabled && !history.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (history.busy) "상태 확인 중…" else "서버에서 상태 다시 확인")
        }
        history.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        if (history.pending) Text("이전 요청 결과 확인이 필요합니다. 새 요청을 전송하지 말고 기존 접수 결과를 확인해 주세요.")
        if (history.checkedAt == null) Text("현재 상태 미확인 · 연결 실패를 요청 없음으로 해석하지 않습니다.")
        else if (!fresh) Text("이전 조회 결과입니다. 상태를 다시 확인한 뒤 조작해 주세요.")
        else if (history.items.isEmpty()) Text("현재 설치에서 조회된 요청이 없습니다.")
        history.items.forEach { item ->
            val display = bookingStatusDisplay(item.status,item.cancellationRequested)
            HorizontalDivider()
            Text(item.serviceType + (item.workshopName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text((if (item.workshopName != null && item.status == "needs_configuration") "전화 연동 대기 · 발신 전" else display.title) +
                " · 희망 ${bookingTime(item.requestedAt)}", style = MaterialTheme.typography.bodyMedium)
            SettingsFinePrint {
                Text(display.detail,style = MaterialTheme.typography.bodySmall)
                Text("서버 갱신 ${bookingTime(item.updatedAt)} · 요청 ${item.bookingId.take(8)}",style = MaterialTheme.typography.bodySmall)
            }
            if (display.canCancel) OutlinedButton(onClick = { if (enabled && controller.isFresh()) selected = item },enabled = enabled && !history.busy && fresh) { Text("이 요청 취소 검토") }
        }
    }
    selected?.takeIf { enabled && fresh }?.let { item ->
        AlertDialog(onDismissRequest = { if (!history.busy) selected = null },
            title = { Text("점검 요청을 취소할까요?") },
            text = { ReviewDialogBody("${item.serviceType}\n희망 일시 ${bookingTime(item.requestedAt)}\n\n대기 중 요청은 취소할 수 있습니다. 이미 처리 중이거나 결과가 불명확하면 취소 의사만 접수되며 완료는 따로 확인해야 합니다.") },
            confirmButton = { TextButton(modifier = Modifier.heightIn(min = 64.dp), enabled = enabled && !history.busy && fresh,onClick = {
                selected = null
                if (enabled && controller.isFresh()) scope.launch { controller.cancel(item.bookingId) }
            }) { Text("취소 요청 보내기") } },
            dismissButton = { TextButton(modifier = Modifier.heightIn(min = 64.dp), enabled = !history.busy,onClick = { selected = null }) { Text("돌아가기") } })
    }
}

private fun bookingTime(value: String): String = runCatching {
    Instant.parse(value)
    ai.yaho.hyundai.core.formatKoreanDateTime(value)
}.getOrElse { "시간 확인 필요" }
