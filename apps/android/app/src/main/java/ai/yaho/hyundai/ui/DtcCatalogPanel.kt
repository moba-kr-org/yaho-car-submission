package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal data class DtcReference(val code: String, val system: String, val summary: String)

@Composable
internal fun DtcCatalogPanel(isParked: Boolean) {
    val context = LocalContext.current
    val catalog = remember {
        runCatching {
            val root = JSONObject(context.assets.open("dtc-catalog.json").bufferedReader().use { it.readText() })
            val entries = root.getJSONArray("entries")
            List(entries.length()) { index -> entries.getJSONObject(index).let {
                DtcReference(it.getString("code"), it.getString("system"), it.getString("summary"))
            } }
        }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DtcReference?>(null) }
    val entries = catalog.getOrDefault(emptyList())
    val matches = remember(query, entries) {
        if (query.isBlank()) emptyList() else entries.filter { it.code.contains(query.trim(), true) || it.summary.contains(query.trim(), true) }
    }
    SettingsPanel(title = "참고용 고장코드 사전") {
        Text("코드나 증상으로 찾아보는 참고 사전 · ${entries.size}개", style = MaterialTheme.typography.labelMedium)
        if (catalog.isFailure) Text("사전을 불러오지 못했습니다. 근거 없이 설명하지 않습니다.", color = MaterialTheme.colorScheme.error)
        if (isParked) {
            OutlinedTextField(value = query, onValueChange = { query = it.take(80); selected = null },
                label = { Text("예: P0300, 냉각, 타이어") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("검색 ${matches.size}개 · 최대 5개씩 미리보기", style = MaterialTheme.typography.labelSmall)
            matches.take(5).forEach { item ->
                TextButton(onClick = { selected = if (selected == item) null else item }, modifier = Modifier.fillMaxWidth()) {
                    Text("${item.code}  ${item.summary.substringBefore('—')}", modifier = Modifier.fillMaxWidth())
                }
            }
            selected?.let {
                HorizontalDivider()
                Text(it.code, fontWeight = FontWeight.Bold)
                Text(it.summary, style = MaterialTheme.typography.bodyMedium)
                Text("야호카 자체 작성 요약 · 차종·연식별 대응 및 정비 검토 전. 이 설명만으로 운행 가능 여부나 교체 부품을 확정하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            }
        } else Text("자세한 코드 검색은 정차 상태에서 이용하세요.")
        SettingsFinePrint {
            Text("위의 자동 경고와 별개인 수동 참고 사전입니다.", style = MaterialTheme.typography.bodySmall)
            Text("지원 범위 · 코드 설명 조회 가능 / 실제 DTC 수집·차종별 확정 진단 연결 전", style = MaterialTheme.typography.bodySmall)
        }
    }
}
