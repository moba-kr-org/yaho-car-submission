package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


internal enum class DemoWarningMode { OFF, LIVE, CLEARED, MISSING }





internal data class DemoWarningState(
    val available: Boolean,
    val mode: DemoWarningMode,
    val live: ActiveVehicleWarning?,
    val clearedKeys: Set<String>,
    val nextMode: DemoWarningMode,
    val cycle: () -> Unit,
)

private val WarnInk = Color(0xFF2B211B)
private val WarnMuted = Color(0xFF6F6258)
private val WarnAccent = Color(0xFFB6342B)
private val WarnAmber = Color(0xFF8C5800)
private val WarnPaper = Color(0xFFFFFBF7)


@Composable
internal fun rememberHomeNotice(
    assessment: VehicleWarningAssessment, demo: DemoWarningState, rainBanner: String?, consentOn: Boolean,
): HomeNotice {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    val empty = WarningTrack(WarningPresence.None, null)
    var sdkMemory by remember { mutableStateOf(empty) }
    var demoMemory by remember { mutableStateOf(empty) }
    val sdk = trackWarning(assessment.active.firstOrNull(), assessment.clearedKeys, consentOn, sdkMemory, now)
    val demoTrack = if (demo.mode == DemoWarningMode.OFF) empty
        else trackWarning(demo.live, demo.clearedKeys, consentOn, demoMemory, now)
    SideEffect {
        sdkMemory = sdk
        demoMemory = demoTrack
    }
    return selectHomeNotice(sdk.presence, demoTrack.presence, rainBanner, assessment.checked)
}

private val WarnCalm = Color(0xFF3D7A5A)


private data class CardTone(val accent: Color, val tint: Color, val border: Dp, val titleSize: Int, val elevation: Dp)

private fun toneOf(notice: HomeNotice): CardTone = when (notice) {
    is HomeNotice.Warning -> CardTone(WarnAccent, Color(0xFFFCEDEA), 3.dp, 30, 14.dp)
    is HomeNotice.StaleWarning -> CardTone(WarnAmber, Color(0xFFFBF1E1), 2.dp, 24, 8.dp)
    is HomeNotice.Idle -> when (notice.kind) {
        IdleKind.LOST, IdleKind.UNRECEIVED -> CardTone(WarnAmber, Color(0xFFFBF1E1), 1.dp, 22, 4.dp)
        IdleKind.CLEARED, IdleKind.QUIET -> CardTone(WarnCalm, Color(0xFFEEF5F0), 1.dp, 22, 3.dp)
        IdleKind.CONSENT_OFF -> CardTone(WarnMuted, Color(0xFFF2EEEA), 1.dp, 22, 3.dp)
    }
    is HomeNotice.Rain -> CardTone(WarnMuted, Color(0xFFF2EEEA), 1.dp, 22, 3.dp)
}





@Composable
internal fun HomeVehicleCard(
    notice: HomeNotice, status: HomeVehicleStatus, parked: Boolean, canPrepare: Boolean, onPrepare: () -> Unit,
    canOpenDetail: Boolean, onOpenDetail: () -> Unit, modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
) {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    val tone = toneOf(notice)
    Surface(
        modifier = modifier.semantics { contentDescription = "내 차 상태와 차량 경고" },
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .97f),
        contentColor = WarnInk,
        border = BorderStroke(tone.border, if (tone.border > 1.dp) tone.accent else Color(0xFFEAD9CA)),
        shadowElevation = tone.elevation,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = canOpenDetail, role = Role.Button, onClick = onOpenDetail).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("내 차 상태", fontSize = 23.sp, fontWeight = FontWeight.Bold)

                    Text((status.energy.firstOrNull()?.let { "$it · Pleos SDK" } ?: "잔량 미확인") + if (canOpenDetail) "  ›" else "",
                        fontSize = 17.sp, color = WarnMuted)
                }
                headerAction?.invoke()
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tone.tint).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val source = when (notice) {
                    is HomeNotice.Warning -> notice.source
                    is HomeNotice.StaleWarning -> notice.source
                    is HomeNotice.Idle -> notice.source
                    is HomeNotice.Rain -> null
                }
                val title = when (notice) {
                    is HomeNotice.Warning -> notice.warning.guidance.title
                    is HomeNotice.StaleWarning -> lostTitle(notice.title)
                    is HomeNotice.Idle -> notice.title
                    is HomeNotice.Rain -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(tone.accent))
                    Text(title, Modifier.weight(1f), fontSize = tone.titleSize.sp, lineHeight = (tone.titleSize + 6).sp,
                        fontWeight = FontWeight.Bold, color = if (notice is HomeNotice.Warning || notice is HomeNotice.StaleWarning) tone.accent else WarnInk)
                }
                when (notice) {
                    is HomeNotice.Warning -> if (!parked) {
                        Text(DRIVING_WARNING_LINE, fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)

                        if (notice.source == WarningSource.DEMO) Text("가상 신호", fontSize = 15.sp, color = WarnMuted)
                    } else {
                        val age = ((now - notice.warning.observedAt) / 1000).coerceAtLeast(0)
                        Text(warningMeaningFor(notice.warning, notice.source), fontSize = 20.sp, lineHeight = 28.sp)
                        Text((if (age < 3) "방금 수신" else "${age}초 전 수신") + if (notice.source == WarningSource.SDK) " · Pleos SDK" else "",
                            fontSize = 17.sp, color = WarnMuted)
                        Button(
                            onClick = onPrepare, enabled = canPrepare,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarnAccent, contentColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("점검 준비", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    is HomeNotice.StaleWarning ->
                        Text("마지막 수신 ${formatKoreanClock(notice.lastObservedAt)} · 계기판을 확인하세요",
                            fontSize = 19.sp, lineHeight = 26.sp, color = WarnMuted)
                    is HomeNotice.Idle -> Text(notice.signalState, fontSize = 18.sp, lineHeight = 25.sp, color = WarnMuted)
                    is HomeNotice.Rain -> Unit
                }
            }
        }
    }
}






@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WarningInspectionContent(
    warning: ActiveVehicleWarning, source: WarningSource, signalCurrent: Boolean,
    selected: List<String>, onToggle: (String) -> Unit, onOpenOfficial: () -> Boolean,
) {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    var openFailed by remember { mutableStateOf(false) }
    val summary = buildInspectionSummary(warning, source, selected, signalCurrent, now)
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("1. 지금 느껴지는 증상을 눌러 주세요 (여러 개 가능)", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            symptomOptions(warning.guidance.id).forEach { option ->
                val on = option.id in selected
                Surface(
                    onClick = { onToggle(option.id) },
                    modifier = Modifier.heightIn(min = 64.dp).semantics { this.selected = on; role = Role.Checkbox },
                    shape = RoundedCornerShape(18.dp),
                    color = if (on) Color(0xFFFFE2C9) else Color.White,
                    border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Color(0xFFB84A08) else Color(0xFFE0D3C7)),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "✓ " else "", fontSize = 21.sp, color = Color(0xFFB84A08))
                        Text(option.label, fontSize = 21.sp)
                    }
                }
            }
        }
        Text("2. 점검 요약 · 상담할 때 이 화면을 참고하세요", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryBlock("차량 신호", if (source == WarningSource.DEMO) "가상 신호" else "Pleos SDK",
                summary.vehicleSignal, if (signalCurrent) WarnAccent else WarnAmber, Modifier.weight(1.2f).fillMaxHeight())
            SummaryBlock("운전자 입력", "직접 선택한 증상", summary.driverInput, Color(0xFFB84A08), Modifier.weight(1f).fillMaxHeight())
            SummaryBlock("미확인", "이 앱이 확인하지 못한 것", summary.unknown, WarnMuted, Modifier.weight(1f).fillMaxHeight())
        }
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF2F5F9), border = BorderStroke(1.dp, Color(0xFFD5DEE8))) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                val qr = remember { officialQr(HYUNDAI_SERVICE_NETWORK_URL) }
                androidx.compose.foundation.Image(qr, contentDescription = "휴대폰으로 현대 공식 정비 안내 열기 QR 코드",
                    modifier = Modifier.size(176.dp), filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. 휴대폰으로 현대 공식 정비 안내 열기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF002C5F))
                    Text("경고 이해 → 증상 정리 → 현대 공식 서비스까지 한 번에", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF002C5F))
                    Text("휴대폰 카메라로 QR을 찍으면 현대 서비스 네트워크 페이지가 열립니다. 예약은 공식 채널에서 확정됩니다. 이 요약은 자동으로 전송되지 않습니다.",
                        fontSize = 18.sp, lineHeight = 25.sp)
                    OutlinedButton(onClick = { openFailed = !onOpenOfficial() }, modifier = Modifier.heightIn(min = 60.dp),
                        shape = RoundedCornerShape(16.dp)) { Text("차량 화면에서 열기", fontSize = 20.sp) }
                    if (openFailed) Text("이 차량 화면에서는 웹페이지를 열 수 없어요. QR을 이용해 주세요 · 고객센터 080-600-6000",
                        fontSize = 17.sp, color = WarnAmber)
                }
            }
        }
    }
}


private fun officialQr(url: String): androidx.compose.ui.graphics.ImageBitmap {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 2))
    val bitmap = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height)
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bitmap.asImageBitmap()
}

@Composable
private fun SummaryBlock(title: String, caption: String, lines: List<String>, accent: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFEAD9CA))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(caption, fontSize = 15.sp, color = WarnMuted)
            HorizontalDivider(color = Color(0xFFEBDFD4))
            lines.forEach { Text(it, fontSize = 18.sp, lineHeight = 24.sp) }
        }
    }
}
