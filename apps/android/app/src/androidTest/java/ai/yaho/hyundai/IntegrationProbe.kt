package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.NavigationContextAdapter
import ai.yaho.hyundai.vehicle.NavigationSdkStatus
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

private data class GleoCallbackBroadcast(val packageName: String?, val response: String?)

internal fun Instrumentation.runNavigationProbe(): JSONObject {
    val store = AppConsentStore(targetContext)
    val before = store.read()
    val state = AtomicReference(NavigationSdkStatus())
    var adapter: NavigationContextAdapter? = null
    try {
        runOnMainSync { adapter = NavigationContextAdapter(targetContext, { state.set(it) }, { store.read().location }).also { it.start() } }
        val deadline = SystemClock.elapsedRealtime() + 16_000
        while (SystemClock.elapsedRealtime() < deadline && !state.get().message.contains("콜백 수신") && state.get().state.name != "UNAVAILABLE") SystemClock.sleep(100)
        return JSONObject().put("test", "navigation_sdk_v1").put("state", state.get().state.name)
            .put("existingLocationConsent", before.location).put("locationCallbackReceived", state.get().message.contains("콜백 수신"))
            .put("coordinatesStoredOrUploaded", false).put("message", state.get().message).put("consentUnchanged", before == store.read())
    } finally { runOnMainSync { adapter?.release() } }
}


internal fun Instrumentation.runGleoProbe(): JSONObject {
    val response = AtomicReference<GleoCallbackBroadcast?>(null)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("packageName") == targetContext.packageName) {
                response.set(GleoCallbackBroadcast(
                    packageName = intent.getStringExtra("packageName"),
                    response = intent.getStringExtra("response"),
                ))
            }
        }
    }
    targetContext.registerReceiver(receiver, IntentFilter("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"), Context.RECEIVER_EXPORTED)
    try {
        val start = SystemClock.elapsedRealtime()
        fun putExtraValue(intent: Intent, key: String, value: Any?) {
            when (value) {
                null -> intent.putExtra(key, null as String?)
                is String -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                else -> error("unsupported_probe_extra:$key")
            }
        }

        fun invoke(
            action: String,
            parameters: Any?,
            clearTask: Boolean = false,
            includeActionParam: Boolean = true,
            directParameters: Map<String, Any?> = emptyMap(),
        ): Pair<JSONObject, Long>? {
            response.set(null)
            val requestStart = SystemClock.elapsedRealtime()
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
            val intent = Intent(targetContext, MainActivity::class.java).setAction(action).addFlags(flags)
            if (includeActionParam) putExtraValue(intent, "ACTION_PARAM", parameters)
            directParameters.forEach { (key, value) -> putExtraValue(intent, key, value) }
            targetContext.startActivity(intent)
            while (response.get() == null && SystemClock.elapsedRealtime() - requestStart < 6500) SystemClock.sleep(50)
            val envelope = response.get() ?: return null
            check(envelope.packageName == targetContext.packageName)
            val item = JSONObject(envelope.response ?: error("callback_response_missing:$action"))
            check(item.getString("functionName") == action)
            return item to (SystemClock.elapsedRealtime() - requestStart)
        }

        val (parsed, contextLatencyMs) = invoke("GET_DRIVING_CONTEXT", "{\"scope\":\"all\"}", clearTask = true)
            ?: return JSONObject().put("test", "gleo_action_wiring_v1").put("result", "callback_timeout")
        val data = org.json.JSONArray(parsed.getString("data")).getJSONObject(0)
        check(parsed.getString("functionName") == "GET_DRIVING_CONTEXT" && parsed.getString("status") == "success")
        val checks = org.json.JSONArray().put(JSONObject().put("case", "context_without_sample_or_coordinates").put("latencyMs", contextLatencyMs))
        for ((action, parameters, expected) in listOf(
            Triple("GET_DRIVING_CONTEXT", "{\"scope\":\"unsupported\"}", "fail"),
            Triple("GET_DRIVING_CONTEXT", "{\"scope\":true}", "fail"),
            Triple("REPORT_ROAD_EVENT", "{}", "asking"),
            Triple("REQUEST_SERVICE_BOOKING", "{\"serviceType\":\"tire\"}", "asking"),
            Triple("REPORT_ROAD_EVENT", "{\"kind\":\"rain_report\"}", "asking"),
        )) {
            val (item, latencyMs) = invoke(action, parameters) ?: error("callback_timeout:$action")
            check(item.getString("status") == expected)
            checks.put(JSONObject().put("function", action).put("status", expected).put("latencyMs", latencyMs))
        }

        val (validDirectScope, validDirectScopeLatencyMs) = invoke(
            "GET_DRIVING_CONTEXT",
            null,
            includeActionParam = false,
            directParameters = mapOf("scope" to "vehicle"),
        ) ?: error("callback_timeout:valid_direct_scope")
        check(validDirectScope.getString("status") == "success")
        check(org.json.JSONArray(validDirectScope.getString("data")).getJSONObject(0).getString("scope") == "vehicle")
        checks.put(JSONObject().put("case", "valid_direct_scope_without_action_param")
            .put("status", validDirectScope.getString("status")).put("latencyMs", validDirectScopeLatencyMs))

        val (booleanDirectScope, booleanDirectScopeLatencyMs) = invoke(
            "GET_DRIVING_CONTEXT",
            null,
            includeActionParam = false,
            directParameters = mapOf("scope" to true),
        ) ?: error("callback_timeout:boolean_direct_scope")
        check(booleanDirectScope.getString("status") == "fail")
        checks.put(JSONObject().put("case", "boolean_direct_scope_rejected")
            .put("status", booleanDirectScope.getString("status")).put("latencyMs", booleanDirectScopeLatencyMs))

        val (booleanActionParam, booleanActionParamLatencyMs) = invoke("GET_DRIVING_CONTEXT", true)
            ?: error("callback_timeout:boolean_action_param")
        check(booleanActionParam.getString("status") == "fail")
        checks.put(JSONObject().put("case", "non_string_action_param_rejected")
            .put("status", booleanActionParam.getString("status")).put("latencyMs", booleanActionParamLatencyMs))

        val (duplicateReport, duplicateLatencyMs) = invoke("REPORT_ROAD_EVENT", "{\"kind\":\"accident_report\"}")
            ?: error("callback_timeout:duplicate_report")
        val preservedReport = org.json.JSONArray(duplicateReport.getString("data")).getJSONObject(0)
        check(duplicateReport.getString("status") == "asking")
        check(preservedReport.getString("pendingFunction") == "REPORT_ROAD_EVENT")
        check(preservedReport.getString("pendingKind") == "rain_report")
        check(!preservedReport.getBoolean("newRequestStarted"))
        checks.put(JSONObject().put("case", "duplicate_review_keeps_first_request")
            .put("function", "REPORT_ROAD_EVENT").put("status", duplicateReport.getString("status")).put("latencyMs", duplicateLatencyMs))

        val (blockedBooking, blockedBookingLatencyMs) = invoke(
            "REQUEST_SERVICE_BOOKING",
            "{\"serviceType\":\"tire\",\"requestedDate\":\"2026-11-06\"}",
        ) ?: error("callback_timeout:blocked_booking")
        val preservedAfterCrossFunction = org.json.JSONArray(blockedBooking.getString("data")).getJSONObject(0)
        check(blockedBooking.getString("status") == "asking")
        check(preservedAfterCrossFunction.getString("pendingFunction") == "REPORT_ROAD_EVENT")
        check(preservedAfterCrossFunction.getString("pendingKind") == "rain_report")
        check(!preservedAfterCrossFunction.getBoolean("newRequestStarted"))
        checks.put(JSONObject().put("case", "cross_function_review_keeps_first_request")
            .put("function", "REQUEST_SERVICE_BOOKING").put("status", blockedBooking.getString("status")).put("latencyMs", blockedBookingLatencyMs))

        return JSONObject().put("test", "gleo_action_wiring_v1").put("checks", checks).put("result", "passed")
            .put("totalLatencyMs", SystemClock.elapsedRealtime() - start).put("simulatedValuesIncluded", data.getBoolean("simulatedValuesIncluded"))
            .put("coordinatesIncluded", data.getJSONObject("navigation").getBoolean("coordinatesIncluded"))
            .put("liveGleoVoiceValidated", false)
    } finally { targetContext.unregisterReceiver(receiver) }
}
