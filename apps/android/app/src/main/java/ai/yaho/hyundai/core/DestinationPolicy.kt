package ai.yaho.hyundai.core

import kotlin.math.abs

data class NavigationTarget(val name: String,val latitude: Double,val longitude: Double,val poiId: String="",val address: String="",val poiSubId: String="")
fun navigationTarget(name: String,lat: String,lng: String,verified: Boolean): NavigationTarget? {
    val latitude=lat.toDoubleOrNull() ?: return null
    val longitude=lng.toDoubleOrNull() ?: return null
    if(!verified || name.trim().length !in 1..80 || name.any {it.isISOControl()} || !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || (latitude==0.0 && longitude==0.0)) return null
    return NavigationTarget(name.trim(),latitude,longitude)
}
fun destinationMatches(target: NavigationTarget,latitude: Double,longitude: Double) = latitude.isFinite() && longitude.isFinite() &&
    abs(target.latitude-latitude)<=0.00001 && abs(target.longitude-longitude)<=0.00001


fun destinationConflictReason(target: NavigationTarget,latitude: Double,longitude: Double,name: String,poiId: String): String? {
    if(destinationMatches(target,latitude,longitude))return null
    return if(target.poiId.isNotBlank() && target.poiId==poiId && target.name==name)
        "장소 이름·ID는 같지만 위치가 다릅니다. 내비 지도에서 목적지를 확인해 주세요."
    else "내비가 반환한 목적지가 선택한 위치와 다릅니다. 안내 완료로 처리하지 않습니다."
}

data class DestinationState(
    val phase: String="idle",val target: NavigationTarget?=null,val mode: String?=null,
    val checkedElapsed: Long?=null,val message: String="목적지 입력 후 기존 안내 상태를 확인해 주세요.",
)
fun destinationReviewValid(state: DestinationState,target: NavigationTarget,elapsed: Long) =
    state.phase=="review" && state.target==target && state.checkedElapsed?.let {elapsed-it in 0..15_000}==true


class DestinationEvidence(private val target: NavigationTarget) {
    var completed=false;private set
    var matched=false;private set
    fun routingCompleted(){completed=true;matched=false}
    fun destination(latitude:Double,longitude:Double){matched=destinationMatches(target,latitude,longitude)}
    fun reconciled()=completed && matched
}
