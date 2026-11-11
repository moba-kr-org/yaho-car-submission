package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.SystemClock
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.*
import ai.yaho.hyundai.vehicle.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runDestinationExecutionProbe(): JSONObject {
    check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
    val before=AppConsentStore(targetContext).read()
    val attempts=NavigationAttemptStore(targetContext)
    val result=JSONObject().put("test","destination_execution_v1").put("emulator",true).put("privateCoordinatesIncluded",false)
    if(attempts.unresolved())return result.put("result","blocked").put("reason","previous_unresolved_attempt")
    if(targetContext.checkSelfPermission("pleos.car.permission.NAVI_ROUTE_SEARCH")!=PackageManager.PERMISSION_GRANTED)
        return result.put("result","blocked").put("reason","route_search_os_permission_required")
    if(!before.vehicleRead)return result.put("result","blocked").put("reason","existing_vehicle_consent_required")
    val target=NavigationTarget("42dot",37.48544722,127.03636666,"36khp37hh8gqcm5nqhe5","서울시 강남구 남부순환로 2621","0")
    val state=AtomicReference(DestinationState())
    val gear=AtomicReference<String?>(null);val speed=AtomicReference<Float?>(null)
    val mode=AtomicReference<String?>(null);val destination=AtomicReference<Pair<Double,Double>?>(null)
    var sdk:NavigationDestinationAdapter?=null;var vehicle:PleosVehicleContextAdapter?=null;var driving:DrivingUxMonitor?=null
    val helper=NaviHelper(targetContext);var helperReady=false;var registered=false;var dispatched=false;var cancelled=false
    val listener=object:NaviHelperEventListener {
        override fun onRouteStateInfo(info:RouteStateInfo){mode.set(info.naviMode.name)}
        override fun onDestinationInfo(info:DestinationInfo){destination.set(info.latitude to info.longitude)}
        override fun onRouteCancelled(){cancelled=true}
    }
    fun parked()=gear.get()=="GEAR_PARK" && speed.get()?.let{it.isFinite() && it in 0f..0.1f}==true
    fun waitUntil(ms:Long,condition:()->Boolean){val deadline=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<deadline && !condition())SystemClock.sleep(100)}
    try{
        runOnMainSync {
            vehicle=PleosVehicleContextAdapter(targetContext){gear.set(it.gear)}.also{it.start()}
            driving=DrivingUxMonitor(targetContext,{}, {speed.set(it)},readSpeed=true).also{it.start()}
            helper.initialize(onConnected={helperReady=true;helper.addListener(listener);registered=true},onFailed={})
            sdk=NavigationDestinationAdapter(targetContext,{parked()}){state.set(it)}
        }
        waitUntil(12_000){parked() && helperReady}
        if(!parked() || !helperReady)return result.put("result","blocked").put("reason","parked_or_service_unverified")
        runOnMainSync {helper.getRouteStateInfo()}
        waitUntil(12_000){mode.get()!=null}
        if(mode.get() !in setOf("SAFE_GUIDING","NOT_WORKING"))return result.put("result","blocked").put("reason","existing_or_unknown_guidance")
        result.put("initialMode",mode.get())
        runOnMainSync{sdk?.prepare(target)}
        waitUntil(13_000){state.get().phase in setOf("review","unavailable","blocked")}
        if(state.get().phase!="review")return result.put("result","incomplete").put("reason","preflight_not_received")
        runOnMainSync{sdk?.execute(target,false)}
        check(!attempts.unresolved() && state.get().phase=="review")
        runOnMainSync{sdk?.execute(target,true)}
        dispatched=attempts.unresolved()
        waitUntil(22_000){state.get().phase in setOf("matched","unknown","blocked")}
        result.put("unapprovedBlocked",true).put("guardPersisted",dispatched).put("executionState",state.get().phase)
        return result
    } finally {
        if(dispatched && helperReady){
            destination.set(null)
            runOnMainSync{helper.getDestinationInfo()}
            waitUntil(8_000){destination.get()!=null}
            val current=destination.get()
            if(current!=null && destinationMatches(target,current.first,current.second) && parked()){
                runOnMainSync{helper.cancelRoute()}
                waitUntil(8_000){cancelled}
                mode.set(null);runOnMainSync{helper.getRouteStateInfo()}
                waitUntil(8_000){mode.get()!=null}
                val restored=cancelled && mode.get() in setOf("SAFE_GUIDING","NOT_WORKING")
                result.put("testRouteCancelled",cancelled).put("inactiveModeRestored",restored)
                if(restored)attempts.acknowledgeManually()
            }else result.put("cleanupSkippedDifferentOrUnknownDestination",true)
        }
        val outcome=result.optString("executionState")
        result.put("choicesUnchanged",before==AppConsentStore(targetContext).read())
        if(result.optString("result").isEmpty())result.put("result",if(outcome=="matched" && result.optBoolean("inactiveModeRestored"))"passed" else "incomplete")
        runOnMainSync{
            sdk?.release();vehicle?.release();driving?.release()
            if(registered)helper.removeListener(listener);helper.release()
        }
    }
}


internal fun Instrumentation.runDestinationReadOnlyAudit():JSONObject {
    check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
    val result=JSONObject().put("test","destination_readonly_audit_v1").put("routeWrites",0)
    val mode=AtomicReference<String?>(null);val destination=AtomicReference<Boolean?>(null)
    val swapped=AtomicReference<Boolean?>(null)
    val metadata=AtomicReference<JSONObject?>(null)
    val ready=java.util.concurrent.atomic.AtomicBoolean(false)
    val error=AtomicReference<String?>(null)
    val target=NavigationTarget("42dot",37.48544722,127.03636666)
    val helper=NaviHelper(targetContext)
    val listener=object:NaviHelperEventListener {
        override fun onRouteStateInfo(info:RouteStateInfo){mode.set(info.naviMode.name)}
        override fun onDestinationInfo(info:DestinationInfo){
            destination.set(destinationMatches(target,info.latitude,info.longitude));swapped.set(destinationMatches(target,info.longitude,info.latitude))
            val north=(info.latitude-target.latitude)*111_320
            val east=(info.longitude-target.longitude)*111_320*kotlin.math.cos(Math.toRadians(target.latitude))
            val distance=kotlin.math.sqrt(north*north+east*east)
            metadata.set(JSONObject().put("nameMatchesSample",info.name=="42dot")
                .put("poiIdMatchesSample",info.poiId=="36khp37hh8gqcm5nqhe5")
                .put("displayCoordinatesMatch",info.displayLatitude?.let {a->info.displayLongitude?.let {b->destinationMatches(target,a,b)}} ?: false)
                .put("distanceBucket",when{!distance.isFinite()->"invalid";distance<5->"under5m";distance<50->"5to50m";distance<500->"50to500m";distance<5000->"500mto5km";else->"over5km"})
                .put("routingCoordinatesValid",info.latitude in -90.0..90.0 && info.longitude in -180.0..180.0)
                .put("placeholderZero",info.latitude==0.0 && info.longitude==0.0))
        }
        override fun onError(code:ai.pleos.playground.navi.constants.NaviErrorCode){error.set(code.name)}
    }
    try {
        runOnMainSync{helper.initialize(onConnected={helper.addListener(listener);ready.set(true)},onFailed={error.set("connection_failed")})}
        var deadline=SystemClock.elapsedRealtime()+12_000
        while(SystemClock.elapsedRealtime()<deadline && !ready.get() && error.get()==null)SystemClock.sleep(100)
        if(ready.get()) {
            runOnMainSync{helper.getRouteStateInfo()}
            deadline=SystemClock.elapsedRealtime()+8_000
            while(SystemClock.elapsedRealtime()<deadline && mode.get()==null)SystemClock.sleep(100)
            runOnMainSync{helper.getDestinationInfo()}
            deadline=SystemClock.elapsedRealtime()+8_000
            while(SystemClock.elapsedRealtime()<deadline && destination.get()==null && error.get()==null)SystemClock.sleep(100)
        }
        result.put("mode",mode.get() ?: JSONObject.NULL).put("destinationMatchesApprovedSample",destination.get() ?: JSONObject.NULL)
            .put("swappedCoordinatesMatch",swapped.get() ?: JSONObject.NULL).put("metadata",metadata.get() ?: JSONObject.NULL)
            .put("errorCode",error.get() ?: JSONObject.NULL).put("unresolvedGuard",NavigationAttemptStore(targetContext).unresolved())
        return result
    } finally {runOnMainSync{if(ready.get())helper.removeListener(listener);helper.release()}}
}
