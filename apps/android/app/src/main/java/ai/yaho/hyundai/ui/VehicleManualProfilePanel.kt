package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Year

@Composable
internal fun VehicleManualProfilePanel(profile: VehicleManualProfile?, enabled: Boolean, onSave: (VehicleManualProfile?) -> Boolean) {
    var editing by remember { mutableStateOf(false) }
    var model by remember(profile) { mutableStateOf(profile?.model.orEmpty()) }
    var year by remember(profile) { mutableStateOf(profile?.year?.toString().orEmpty()) }
    var market by remember(profile) { mutableStateOf(profile?.market ?: "KR") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) { if (!enabled) editing=false }
    SettingsPanel(title = "설명서 적용 기준") {
        SettingsStatusLine(profile?.let { "${it.model} · ${it.year}년형 · ${it.market}" } ?: "차종 미설정", profile != null)
        Text(manualApplicabilityLabel(profile), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={editing=!editing},enabled=enabled) {Text(if(editing) "입력 닫기" else "적용 기준 설정")}
        if(editing) {
            OutlinedTextField(model,{model=it.take(40)},label={Text("차종 · 예: 아이오닉 5")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(year,{year=it.filter(Char::isDigit).take(4)},label={Text("모델 연식 · 예: 2025")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("KR" to "한국","US" to "미국","OTHER" to "기타").forEach { (code,title) ->
                    OutlinedButton(onClick={market=code},enabled=enabled) { Text(if(market==code) "✓ $title" else title) }
                }
            }
            SettingsFinePrint {
                Text("차종·연식·판매 지역을 직접 선택하는 참고 설정입니다. 차량 자동 식별이나 소유 인증이 아니며 이 기기에만 저장합니다.",style=MaterialTheme.typography.bodySmall)
                Text("현재 내장 참고는 2025 IONIQ 5 미국 사양입니다. 한국 사양이나 다른 연식의 전용 설명서가 연결된 것으로 표시하지 않습니다.",style=MaterialTheme.typography.bodySmall)
            }
            Button(onClick={
                val parsed=parseManualProfile(model,year,market,Year.now().value)
                if(parsed==null) error="차종과 올바른 모델 연식을 확인해 주세요."
                else if(onSave(parsed)) {error=null;editing=false} else error="설정을 저장하지 못했습니다."
            },enabled=enabled) {Text("기기에 저장")}
            TextButton(onClick={if(onSave(null)){error=null;editing=false}else error="설정을 지우지 못했습니다."},enabled=enabled) {Text("설정 지우기")}
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    }
}
