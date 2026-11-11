package ai.yaho.hyundai

import android.app.Instrumentation
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.vehicle.VehicleObservationService
import org.json.JSONObject


internal fun Instrumentation.runVisibilityAuditProbe():JSONObject {
    val audit=runCollectionAuditProbe()
    val result=JSONObject().put("test","visibility_live_sdk_audit_v1").put("signalValuesIncluded",false)
        .put("vehicleControlPerformed",false).put("choicesUnchanged",audit.optBoolean("choicesUnchanged"))
    if(audit.optString("status")!="ready")return result.put("status","blocked")
    val snapshot=VehicleObservationService.snapshots.value
    val rows=snapshot.readings.map{VisibilitySignal(it.key,it.state,it.value,it.observedAt,it.receivedElapsed)}
    val assessment=VisibilityMonitor().update(rows,snapshot.active,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),true)
    return result.put("status","received").put("assessmentStatus",assessment.status)
        .put("wiperPresent",rows.any{it.key==VISIBILITY_WIPER&&it.state=="수신"})
        .put("frontDefrosterPresent",rows.any{it.key==VISIBILITY_DEFROSTER&&it.state=="수신"})
        .put("recommendationEmitted",assessment.notice!=null).put("historyPersisted",false)
}
