package ai.yaho.hyundai

import android.app.Instrumentation
import android.os.SystemClock
import ai.yaho.hyundai.core.NavigationRouteObservation
import ai.yaho.hyundai.core.routeObservationLabel
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runNavigationRouteProbe(): JSONObject {
    check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
    val before=AppConsentStore(targetContext).read()
    val service=AtomicReference(NavigationSdkStatus())
    val route=AtomicReference(NavigationRouteObservation())
    var adapter:NavigationContextAdapter?=null
    var approved=false
    try {
        runOnMainSync { adapter=NavigationContextAdapter(targetContext,{service.set(it)},{false},{route.set(it)},{approved}).also{it.start()} }
        var deadline=SystemClock.elapsedRealtime()+13_000
        while(SystemClock.elapsedRealtime()<deadline && service.get().state !in setOf(AdapterState.CONNECTED,AdapterState.UNAVAILABLE)) SystemClock.sleep(100)
        runOnMainSync {adapter?.requestRouteStateOnce()}
        val blocked=route.get().state=="blocked"
        runOnMainSync {approved=true;adapter?.requestRouteStateOnce()}
        deadline=SystemClock.elapsedRealtime()+13_000
        while(SystemClock.elapsedRealtime()<deadline && route.get().state in setOf("pending","blocked","idle")) SystemClock.sleep(100)
        val received=route.get()
        val expired=routeObservationLabel(received,SystemClock.elapsedRealtime()+30_001).contains("만료")
        val unchanged=before==AppConsentStore(targetContext).read()
        return JSONObject().put("test","navigation_route_read_v1").put("state",received.state)
            .put("mode",received.mode ?: JSONObject.NULL).put("unapprovedBlocked",blocked).put("expiryVerified",expired)
            .put("choicesUnchanged",unchanged).put("routeWrites",0).put("coordinatesOrHistoryIncluded",false)
            .put("result",if(blocked && received.state=="received" && expired && unchanged) "passed" else "incomplete")
    } finally {runOnMainSync{approved=false;adapter?.release()}}
}
