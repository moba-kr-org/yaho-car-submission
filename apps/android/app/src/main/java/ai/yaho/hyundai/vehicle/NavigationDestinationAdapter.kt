package ai.yaho.hyundai.vehicle

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.NavigationAttemptStore
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock


class NavigationDestinationAdapter(
    private val context: Context,
    private val allowed: ()->Boolean,
    private val changed: (DestinationState)->Unit,
) {
    private val helper=NaviHelper(context.applicationContext)
    private val handler=Handler(Looper.getMainLooper())
    private val attempts=NavigationAttemptStore(context)
    private var state=DestinationState()
    private var connected=false
    private var released=false
    private var registered=false
    private var started=false
    private var used=false
    private var evidence:DestinationEvidence?=null
    private var destinationPending=false
    private val timeout=Runnable {
        if(state.phase in setOf("connecting","checking")) publish(state.copy(phase="unavailable",message="내비 응답을 받지 못했습니다. 다시 연결해 확인해 주세요."))
        else if(state.phase=="pending") unknown("내비 변경 결과를 확인하지 못했습니다. 자동으로 다시 요청하지 않습니다.")
    }
    private fun publish(value:DestinationState){state=value;changed(value)}
    private val listener=object:NaviHelperEventListener {
        override fun onRouteStateInfo(info:RouteStateInfo) { val mode=info.naviMode.name; handler.post {
            if(released || state.phase!="checking") return@post
            handler.removeCallbacks(timeout)
            if(!allowed()) {publish(DestinationState(message="정차 후 다시 확인해 주세요."));return@post}
            publish(state.copy(phase="review",mode=mode,checkedElapsed=SystemClock.elapsedRealtime(),message="기존 안내 모드 확인 · 목적지 변경 내용을 검토해 주세요."))
        } }
        override fun onRoutingCompleted(){handler.post{
            if(released || state.phase!="pending")return@post
            evidence?.routingCompleted();readDestination()
        }}
        override fun onDestinationChanged(){handler.post{if(!released && state.phase=="pending")readDestination()}}
        override fun onDestinationInfo(info:DestinationInfo){val lat=info.latitude;val lng=info.longitude;val name=info.name;val poiId=info.poiId;handler.post{
            if(released || state.phase!="pending" || !destinationPending)return@post
            destinationPending=false
            evidence?.destination(lat,lng)
            if(evidence?.completed==true) {
                val target=state.target
                val conflict=target?.let {destinationConflictReason(it,lat,lng,name,poiId)}
                if(conflict!=null){unknown(conflict);return@post}
            }
            if(evidence?.reconciled()==true){
                handler.removeCallbacks(timeout)

                publish(state.copy(phase="matched",message="내비의 현재 목적지가 입력 좌표와 일치하고 탐색 완료 이벤트를 받았습니다. 앱 요청의 독점적인 성공 증명은 아니므로 내비 화면에서 경로를 확인해 주세요."))
            }
        }}
        override fun onError(errorCode:NaviErrorCode){handler.post{
            if(released)return@post
            if(state.phase=="pending")unknown("내비 오류 ${errorCode.name} · 변경 여부 미확인. 내비 화면에서 확인해 주세요.")
            else if(state.phase in setOf("checking","connecting")){handler.removeCallbacks(timeout);publish(state.copy(phase="unavailable",message="내비 상태 확인 실패"))}
        }}
    }
    private fun readDestination(){
        if(destinationPending || released)return
        destinationPending=true
        try{helper.getDestinationInfo()}catch(_:Exception){unknown("최종 목적지 조회 실패 · 변경 여부 미확인")}
    }
    fun prepare(target:NavigationTarget){
        if(released || !allowed() || used || attempts.unresolved()){publish(DestinationState(phase="blocked",message="이전 요청을 내비 화면에서 확인하거나 정차 조건을 확인해 주세요."));return}
        if(state.phase in setOf("connecting","checking","pending"))return

        if(state.phase=="unavailable")return
        publish(DestinationState(phase=if(connected)"checking" else "connecting",target=target))
        handler.postDelayed(timeout,12_000)
        if(connected){queryState();return}
        if(started)return
        started=true
        try{helper.initialize(onConnected={handler.post{
            if(released || state.phase!="connecting")return@post
            try {
                helper.addListener(listener);registered=true;connected=true
                publish(state.copy(phase="checking"));queryState()
            } catch(_:Exception) {handler.removeCallbacks(timeout);publish(state.copy(phase="unavailable",message="내비 리스너 연결 실패"))}
        }},onFailed={handler.post{if(!released){handler.removeCallbacks(timeout);publish(state.copy(phase="unavailable",message="내비 연결 실패"))}}})}
        catch(_:Exception){handler.removeCallbacks(timeout);publish(state.copy(phase="unavailable",message="내비 연결 실패"))}
    }
    private fun queryState(){try{helper.getRouteStateInfo()}catch(_:Exception){handler.removeCallbacks(timeout);publish(state.copy(phase="unavailable",message="기존 안내 조회 실패"))}}
    fun execute(target:NavigationTarget,approved:Boolean){
        if(released || !approved || !allowed() || used || !connected || !destinationReviewValid(state,target,SystemClock.elapsedRealtime()))return
        if(context.checkSelfPermission("pleos.car.permission.NAVI_ROUTE_SEARCH")!=PackageManager.PERMISSION_GRANTED){publish(state.copy(phase="blocked",message="내비 경로 탐색 권한이 필요합니다. OS 권한을 확인해 주세요."));return}
        if(!attempts.begin()){publish(state.copy(phase="blocked",message="이전 요청 결과를 먼저 확인해 주세요."));return}
        used=true;evidence=DestinationEvidence(target);destinationPending=false
        publish(state.copy(phase="pending",message="내비에 한 번 요청했습니다. 탐색 결과와 목적지를 대조하고 있습니다."))
        handler.postDelayed(timeout,20_000)
        try{helper.requestRoute(RouteInfo(latitude=target.latitude,longitude=target.longitude,poiName=target.name,poiId=target.poiId,address=target.address,poiSubId=target.poiSubId))}
        catch(_:Exception){unknown("내비 요청 결과 미확인 · 자동 재요청하지 않습니다.")}
    }
    private fun unknown(message:String){handler.removeCallbacks(timeout);destinationPending=false;publish(state.copy(phase="unknown",message=message))}
    fun release(){
        if(released)return
        if(state.phase=="pending")unknown("앱이 전환되어 내비 요청 결과를 확인하지 못했습니다. 내비 화면에서 확인해 주세요.")
        released=true;handler.removeCallbacksAndMessages(null)
        if(registered)runCatching{helper.removeListener(listener)}
        runCatching{helper.release()};connected=false

        publish(DestinationState(phase=if(attempts.unresolved())"unknown" else "idle",message=if(attempts.unresolved())"이전 내비 요청 확인이 필요합니다." else "목적지 입력 후 기존 안내 상태를 확인해 주세요."))
    }
}
