package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.SystemClock
import ai.pleos.playground.location.fused.FusedLocation
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.FusedLocationAdapter
import ai.yaho.hyundai.vehicle.LiveLocationStatus
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runFusedLocationProbe(upload: Boolean = false): JSONObject {
    val choice=AppConsentStore(targetContext).read()
    val supported=runCatching { FusedLocation(targetContext).isSupported() }.getOrDefault(false)
    val permission=targetContext.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==PackageManager.PERMISSION_GRANTED
    val result=JSONObject().put("test","fused_location_v1").put("sdkSupported",supported)
        .put("osPermissionGranted",permission).put("explicitPurposeEnabled",choice.liveLocationNetwork)
        .put("coordinatesIncluded",false).put("uploadAttempted",false)
    if(!supported || !permission || !choice.liveLocationNetwork) return result.put("result","blocked").put("reason",
        if(!supported) "sdk_unsupported" else if(!permission) "os_permission_required" else "explicit_purpose_required")
    var adapter:FusedLocationAdapter?=null
    val state=AtomicReference(LiveLocationStatus())
    try {
        runOnMainSync { adapter=FusedLocationAdapter(targetContext,{AppConsentStore(targetContext).read().liveLocationNetwork},{state.set(it)}).also {it.start()} }
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<deadline && state.get().fix==null) SystemClock.sleep(100)
        val value=state.get()
        if(upload && value.fix != null) {
            val fix=value.fix
            val location=ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter(ai.yaho.hyundai.core.DrivingMode.PARKED).readContext()
                .copy(latitude=fix.latitude,longitude=fix.longitude,headingDegrees=fix.heading?.toInt(),
                    sourceLabel="FusedLocation SDK",isSample=fix.emulator,locationEvidence=fix)
            val network=kotlinx.coroutines.runBlocking { runNativeNetworkProbe(location) }
            result.put("uploadAttempted",true).put("network",network)
        }
        result.put("result",if(value.fix!=null) "callback_received" else "no_fresh_fix")
            .put("state",value.state).put("hasProviderTimestamp",value.fix?.observedAt?.let { it>0 } ?: false)
            .put("emulatorSource",value.fix?.emulator ?: JSONObject.NULL).put("metreAccuracyProvided",false)
    } finally {
        runOnMainSync {adapter?.release()}
        result.put("memoryFixClearedOnRelease",state.get().fix==null)
    }
    return result
}
