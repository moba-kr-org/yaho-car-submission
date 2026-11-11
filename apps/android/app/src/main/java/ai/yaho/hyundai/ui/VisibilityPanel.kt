package ai.yaho.hyundai.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ai.yaho.hyundai.data.AppConsentStore
import androidx.compose.ui.unit.dp
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import kotlinx.coroutines.delay

@Composable
internal fun rememberVisibilityAssessment(snapshot:VehicleSnapshot,consent:Boolean):VisibilityAssessment {
    val context=LocalContext.current.applicationContext
    val store=remember(context){AppConsentStore(context)}
    val monitor=remember{VisibilityMonitor()}
    val latest by rememberUpdatedState(snapshot)
    DisposableEffect(monitor,store){
        val listener=store.listen{if(!store.read().let{it.reviewed&&it.vehicleRead})monitor.clear()}
        onDispose{store.unlisten(listener);monitor.clear()}
    }
    val value by produceState(VisibilityAssessment("unknown"),consent,snapshot.active) {
        do {
            val current=latest
            val allowed=consent&&store.read().let{it.reviewed&&it.vehicleRead}
            value=monitor.update(current.readings.map{VisibilitySignal(it.key,it.state,it.value,it.observedAt,it.receivedElapsed)},
                allowed&&current.active,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),
                android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
            if(!allowed||!current.active)break
            delay(250)
        }while(true)
    }
    return value
}
@Composable
internal fun VisibilityPanel(state:VisibilityAssessment) {
    SettingsPanel(title = "시야 확인 조합") {
        SettingsStatusLine(when(state.status){"stopped"->"차량 조회 중지";"unknown"->"최근 신호 미확인";"observing"->"동시 작동 확인 중";"check_recommended"->"시야 확인 권장";else->"동시 작동 조합 없음"}, when(state.status){"check_recommended","unknown"->null;"stopped"->false;else->true})
        Text("와이퍼 ${state.wiperSamples}회 · 앞유리 성에 제거 ${state.defrosterSamples}회 · 겹친 관측 ${(state.overlapMs/1000)}초",style=MaterialTheme.typography.labelMedium)
        SettingsFinePrint {
            Text(state.lightContext,style=MaterialTheme.typography.bodySmall)
            Text(state.climateContext,style=MaterialTheme.typography.bodySmall)
            Text("각 3회 이상·30초 이상 겹친 ON 관측을 사용하는 초기 안내 규칙입니다. 실제 시정·김서림·비·결빙을 측정한 결과가 아닙니다. 차량 설정은 자동 변경하지 않습니다.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
