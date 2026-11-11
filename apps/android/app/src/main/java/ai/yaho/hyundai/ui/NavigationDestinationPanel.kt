package ai.yaho.hyundai.ui

import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.NavigationAttemptStore
import ai.yaho.hyundai.vehicle.NavigationDestinationAdapter
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun NavigationDestinationPanel(enabled:Boolean) {
    val context=LocalContext.current
    val attempts=remember{NavigationAttemptStore(context)}
    var unresolved by remember{mutableStateOf(attempts.unresolved())}
    var name by remember{mutableStateOf("")};var lat by remember{mutableStateOf("")};var lng by remember{mutableStateOf("")}
    var verified by remember{mutableStateOf(false)}
    var state by remember{mutableStateOf(DestinationState())}
    var error by remember{mutableStateOf<String?>(null)}
    var approval by remember{mutableStateOf(false)};var acknowledge by remember{mutableStateOf(false)}
    var epoch by remember{mutableIntStateOf(0)};var tick by remember{mutableLongStateOf(0)}
    var adapter by remember{mutableStateOf<NavigationDestinationAdapter?>(null)}
    val allowed=rememberUpdatedState(enabled)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        approval=false;epoch++;error="권한 선택이 반영됐습니다. 목적지와 기존 안내 상태를 다시 확인해 주세요."
    }
    DisposableEffect(enabled,epoch){
        if(enabled){adapter=NavigationDestinationAdapter(context,{allowed.value}){state=it;unresolved=attempts.unresolved()}}
        onDispose{adapter?.release();adapter=null}
    }
    LaunchedEffect(enabled){if(!enabled){approval=false;acknowledge=false;name="";lat="";lng="";verified=false}}
    LaunchedEffect(Unit){while(true){tick++;delay(1000)}}
    val target=remember(name,lat,lng,verified){navigationTarget(name,lat,lng,verified)}
    val reviewFresh=remember(state,target,tick){target!=null && destinationReviewValid(state,target,android.os.SystemClock.elapsedRealtime())}
    val busy=state.phase in setOf("connecting","checking","pending")
    if (unresolved) {
        SettingsPanel(title = "이전 목적지 요청 확인 필요") {
            Text("내비 앱에서 현재 목적지와 경로를 확인해 주세요. 확인 전에는 새 요청을 보낼 수 없습니다.")
            OutlinedButton(onClick = { acknowledge = true }, enabled = enabled && state.phase != "pending") {
                Text("내비에서 확인한 결과 정리")
            }
        }
    } else {
        SettingsDetails("목적지 직접 입력", "이름과 좌표를 알고 있을 때") {
            SettingsPanel {
                Text("이름과 좌표를 차량 내비에 전달합니다. 기존 목적지와 경유지가 바뀔 수 있습니다. 야호 서버 전송이나 기기 저장은 하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name,{name=it.take(80);verified=false;approval=false},label={Text("목적지 이름")},enabled=enabled && !busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(lat,{lat=it.take(24);verified=false;approval=false},label={Text("위도 · 예: 37.x")},enabled=enabled && !busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(lng,{lng=it.take(24);verified=false;approval=false},label={Text("경도 · 예: 127.x")},enabled=enabled && !busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                Row {Checkbox(verified,{verified=it;approval=false},enabled=enabled && !busy);Text("지도 등으로 목적지와 좌표가 맞는지 확인했습니다.",modifier=Modifier.padding(top=12.dp))}
                OutlinedButton(onClick={
                    approval=false
                    if(target==null)error="이름·좌표와 위치 확인 항목을 확인해 주세요."
                    else {error=null;adapter?.prepare(target)}
                },enabled=enabled && !busy && target!=null && state.phase!="unavailable") {Text("기존 안내 확인 후 변경 준비")}
                if(state.phase=="unavailable")TextButton(onClick={epoch++;state=DestinationState()},enabled=enabled){Text("내비 연결 다시 준비")}
                if(state.phase=="review") {
                    Text("현재 내비 모드: ${state.mode ?: "미확인"}\n기존 목적지·경유지의 구체적인 내용은 읽지 않습니다. 변경 전 내비 화면에서 확인해 주세요.")
                    Button(onClick={approval=true},enabled=enabled && reviewFresh) {Text("목적지 변경 내용 검토")}
                    if(!reviewFresh)Text("입력 또는 확인 시간이 바뀌었습니다. 기존 안내를 다시 확인해 주세요.")
                }
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if(approval && enabled && reviewFresh && target!=null && !unresolved)AlertDialog(onDismissRequest={approval=false},
        title={Text("이 목적지로 안내를 변경할까요?")},
        text={Text("${target.name}\n위도 ${target.latitude} / 경도 ${target.longitude}\n현재 모드 ${state.mode}\n\n기존 목적지·경유지가 바뀔 수 있습니다. 선택한 이름과 좌표를 내비 서비스로 전달하고 한 번만 요청합니다.")},
        confirmButton={TextButton(onClick={
            approval=false
            if(context.checkSelfPermission("pleos.car.permission.NAVI_ROUTE_SEARCH")!=PackageManager.PERMISSION_GRANTED)
                permission.launch("pleos.car.permission.NAVI_ROUTE_SEARCH")
            else if(enabled && destinationReviewValid(state,target,android.os.SystemClock.elapsedRealtime()))adapter?.execute(target,true)
        }){Text("확인하고 1회 요청")}},dismissButton={TextButton(onClick={approval=false}){Text("돌아가기")}})
    if(acknowledge && enabled && state.phase!="pending")AlertDialog(onDismissRequest={acknowledge=false},
        title={Text("내비 화면에서 확인했나요?")},
        text={Text("현재 목적지·경로를 내비에서 직접 확인했고 필요한 조치를 마쳤을 때만 선택해 주세요. 확인 표시를 정리해도 경로 변경 성공이나 취소를 뜻하지 않으며 내비 명령을 보내지 않습니다.")},
        confirmButton={TextButton(onClick={
            acknowledge=false
            if(attempts.acknowledgeManually()){unresolved=false;epoch++;state=DestinationState();name="";lat="";lng="";verified=false}
            else error="확인 표시를 저장하지 못했습니다."
        }){Text("직접 확인했고 정리합니다")}},dismissButton={TextButton(onClick={acknowledge=false}){Text("아직 확인 전")}})
}
