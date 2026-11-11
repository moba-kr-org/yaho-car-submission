package ai.yaho.hyundai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.yaho.hyundai.vehicle.VehicleSdkStatus
import kotlinx.coroutines.delay
import org.json.JSONObject


@Composable
internal fun CompanionPanel(status: VehicleSdkStatus) {
    val context = LocalContext.current
    val data = remember { JSONObject(context.assets.open("companion-face.json").bufferedReader().use { it.readText() }) }
    val prefs = remember { context.getSharedPreferences("yaho_companion_face", 0) }
    val shapes = listOf("hex" to "기본", "round" to "둥근", "square" to "네모", "cloud" to "구름", "drop" to "물방울", "diamond" to "다이아")
    val colors = listOf("orange" to "오렌지", "navy" to "네이비", "blue" to "하늘", "green" to "초록", "violet" to "보라", "amber" to "호박", "coral" to "코랄", "slate" to "회청", "charcoal" to "차콜")
    val eyes = listOf("open" to "기본", "dot" to "동그란", "line" to "웃는", "slit" to "가느다란", "sharp" to "또렷한")
    var shape by remember { mutableStateOf(prefs.getString("shape", "hex").takeIf { key -> shapes.any { it.first == key } } ?: "hex") }
    var color by remember { mutableStateOf(prefs.getString("color", "orange").takeIf { key -> colors.any { it.first == key } } ?: "orange") }
    var eye by remember { mutableStateOf(prefs.getString("eyes", "open").takeIf { key -> eyes.any { it.first == key } } ?: "open") }
    var customizing by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("안녕하세요. 야호예요. 얼굴을 눌러 저를 불러보세요.") }
    var question by remember { mutableStateOf("") }
    var tapped by remember { mutableIntStateOf(0) }
    var targetTilt by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableStateOf(false) }
    val tilt by animateFloatAsState(targetTilt, spring(dampingRatio = .5f), label = "companion-greeting")
    LaunchedEffect(tapped) {
        if (tapped > 0) { targetTilt = if (tapped % 2 == 0) -7f else 7f; delay(650); targetTilt = 0f }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(4300); blink = true; delay(140); blink = false }
    }
    fun save() { prefs.edit().putString("shape", shape).putString("color", color).putString("eyes", eye).apply() }
    fun answer(text: String) {
        reply = when {
            text.contains("와이퍼") -> "현재 와이퍼 SDK 상태는 ${status.wiper}예요. 이것만으로 비가 온다고 확정하지는 않아요."
            text.contains("브레이크") -> "브레이크 SDK 상태는 ${status.brake}예요. 미끄러짐 감지와는 다른 신호예요."
            text.contains("차량") || text.contains("상태") -> "연결된 기어 신호는 ${status.gear ?: "확인 전"}예요. 차량 신호는 자동으로 확인하고 있어요."
            else -> "차량 상태와 도로 정보를 함께 확인해 드릴게요. 지금은 차량 데이터 기반 안내 단계이고, 자유 음성 대화는 연결 준비 중이에요."
        }
        tapped += 1
        question = ""
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("내 차의 컴패니언, 야호", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val facePath = remember(shape) { androidx.core.graphics.PathParser.createPathFromPathData(data.getJSONObject("paths").getString(shape))!!.asComposePath() }
            val faceColor = Color(android.graphics.Color.parseColor(data.getJSONObject("colors").getString(color)))
            Canvas(Modifier.size(420.dp).semantics { contentDescription = "야호 컴패니언 얼굴 · 눌러 인사" }.clickable { tapped += 1; reply = "네, 여기 있어요. 오늘 이동도 함께할게요." }) {
                withTransform({ rotate(tilt); scale(size.width / 32f, size.height / 32f, Offset.Zero) }) {
                    drawPath(facePath, faceColor)
                    val earPaths = data.getJSONObject("ears").getJSONArray(shape)
                    repeat(2) { index -> drawPath(androidx.core.graphics.PathParser.createPathFromPathData(earPaths.getString(index))!!.asComposePath(), Color(0xFFF2A986)) }
                    val ink = Color(0xFF142338)
                    for (x in listOf(12.5f, 19.5f)) {
                        if (blink || eye == "line" || eye == "slit") {
                            drawLine(ink, Offset(x-1.25f, 16.75f), Offset(x+1.25f, 16.75f), .7f)
                        } else if (eye == "dot") drawCircle(ink, .9f, Offset(x, 16.75f))
                        else {
                            drawOval(Color.White, Offset(x-1.7f, 14.85f), Size(3.4f, 4.1f))
                            drawOval(ink, Offset(x-.85f, 15.3f), Size(1.7f, 3.25f))
                            if (eye == "sharp") drawLine(ink, Offset(x-1.8f, 14.7f), Offset(x+1.8f, 15.4f), .55f)
                        }
                    }
                    drawPath(Path().apply { moveTo(15.2f,19.4f);lineTo(16.8f,19.4f);lineTo(16f,20.3f);close() }, ink)
                    drawLine(ink, Offset(14.6f,21.5f), Offset(17.4f,21.5f), .55f)
                }
            }
            Text(reply, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { answer("차량 상태") }) { Text("내 차 상태") }
                OutlinedButton(onClick = { answer("와이퍼") }) { Text("와이퍼 상태") }
                OutlinedButton(onClick = { customizing = !customizing }) { Text(if (customizing) "꾸미기 접기" else "얼굴 꾸미기") }
            }
            OutlinedTextField(value = question, onValueChange = { question = it.take(200) }, label = { Text("야호에게 물어보기") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { answer(question) }, enabled = question.isNotBlank()) { Text("상태 물어보기") }
            Text("현재는 차량 데이터 기반 안내입니다. 마이크 수신·자유 음성 대화는 아직 연결되지 않았습니다.", style = MaterialTheme.typography.bodySmall)
            if (customizing) {
                Text("얼굴 모양", fontWeight = FontWeight.Bold)
                shapes.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (id,label) -> FilterChip(selected=shape==id, onClick={shape=id;save()}, label={Text(label)}) } } }
                Text("얼굴 색", fontWeight = FontWeight.Bold)
                colors.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (id,label) -> FilterChip(selected=color==id, onClick={color=id;save()}, label={Text(label)}) } } }
                Text("눈 모양", fontWeight = FontWeight.Bold)
                eyes.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (id,label) -> FilterChip(selected=eye==id, onClick={eye=id;save()}, label={Text(label)}) } } }
            }
        }
    }
}

@Composable
internal fun DrivingInfoPanel(known: Boolean) {
    Column(Modifier.fillMaxWidth().padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text("주행 안내", fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text(if (known) "차량 상태를 확인하고 있습니다." else "차량 안내 연결을 확인하고 있습니다.", fontSize = 32.sp)
        Text("확인된 도로 정보가 들어오면 이곳에서 짧게 안내합니다.", fontSize = 28.sp)
    }
}
