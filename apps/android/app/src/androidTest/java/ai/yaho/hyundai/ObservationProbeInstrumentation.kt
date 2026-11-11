package ai.yaho.hyundai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.VehicleObservationService
import org.json.JSONObject


class ObservationProbeInstrumentation : Instrumentation() {
    private var mode: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode"); start() }
    override fun onStart() {
        if (mode == "review_dialog_fixture") {
            check(ai.yaho.hyundai.BuildConfig.DEBUG && android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
            val activity = startActivitySync(Intent().setClassName(targetContext, "ai.yaho.hyundai.SpeechProbeActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("review_dialog_fixture", true))
            waitForIdleSync()
            SystemClock.sleep(15000)
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "synthetic_review_render_only_no_touch_no_transmission") })
            return
        }
        if (mode == "telltales_capability") {
            val result = try { runTelltalesCapabilityProbe() } catch (error: Exception) {
                JSONObject().put("test", "telltales_capability_metadata_v1")
                    .put("source", "pleos_emulator_capability_metadata").put("status", "SDKerror")
                    .put("permissionRequirementSource", "Vehicle2.3.2 AAR annotation")
                    .put("errorClass", error.javaClass.simpleName).put("getterCalled", false)
                    .put("signalValuesIncluded", false).put("actualVehicleWarningValuesValidated", false)
                    .put("capabilityMethodCalled", false).put("vehicleInitializeCalled", false)
            }
            finish(if (result.optString("status") == "received") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "telltales_read_once") {
            val result = try { runTelltalesReadOnceProbe() } catch (error: Exception) {
                JSONObject().put("test", "telltales_read_once_v1")
                    .put("source", "pleos_emulator_telltale_once").put("status", "SDKerror")
                    .put("errorClass", error.javaClass.simpleName).put("dataScope", "emulator_fixture_once")
                    .put("getterCalls", 0).put("SDKreceived", false).put("selectedPowerDownState", "unknown")
                    .put("privateCapabilityCalled", false).put("listenerRegistered", false)
                    .put("productionCollection", false).put("choicesUnchanged", false)
            }
            finish(if (result.optString("status") == "received") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "visibility_audit") {
            val result = runVisibilityAuditProbe()
            finish(if(result.optString("status")=="received") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply{putString("result",result.toString())})
            return
        }
        if (mode == "driving_priority" || mode == "visibility_priority") {
            val result=runDrivingPriorityProbe(visibilityBase = mode == "visibility_priority")
            finish(if(result.optString("status")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply{putString("result",result.toString())})
            return
        }
        if (mode == "speech_focus") {
            val result=runSpeechFocusProbe()
            finish(if(result.optString("status")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply{putString("result",result.toString())})
            return
        }
        if (mode == "rain_guidance_flow") {
            val result = runRainFlowProbe()
            finish(if(result.optString("status")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result",result.toString()) })
            return
        }
        if (mode == "pleos_speech_playback" || mode == "pleos_speech_stop") {
            val result = runPleosPlaybackProbe(stopAfterStart = mode == "pleos_speech_stop")
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "speech_readiness") {
            val result = runSpeechReadinessProbe()
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "trip_volume") {
            val result = runTripVolumeProbe()
            finish(if (result.optString("status") == "completed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "unresolved_reads") {
            val result = runUnresolvedReadsProbe()
            finish(if (result.optString("status") == "completed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "collection_audit") {
            val result = try { runCollectionAuditProbe() } catch (error: Exception) {
                JSONObject().put("test", "collection_audit_v1")
                    .put("source", "existing_pleos_collector_audit").put("status", "incomplete")
                    .put("snapshotActive", JSONObject.NULL).put("ready", false).put("readerCount", JSONObject.NULL)
                    .put("counts", JSONObject.NULL).put("stateCounts", JSONObject.NULL)
                    .put("rows", org.json.JSONArray()).put("rawValuesIncluded", false)
                    .put("timestampsIncluded", false).put("choicesUnchanged", JSONObject.NULL)
                    .put("errorClass", error.javaClass.simpleName)
            }
            finish(if (result.optString("status") == "ready") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if(mode=="destination_audit") {
            val result=try{runDestinationReadOnlyAudit()}catch(error:Exception){JSONObject().put("result","failed").put("type",error.javaClass.simpleName)}
            finish(Activity.RESULT_OK,Bundle().apply{putString("result",result.toString())});return
        }
        if(mode=="destination_execution") {
            val result=try{runDestinationExecutionProbe()}catch(error:Exception){JSONObject().put("result","failed").put("type",error.javaClass.simpleName)}
            finish(if(result.optString("result")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply{putString("result",result.toString())});return
        }
        if (mode == "navigation_route") {
            val result=try { runNavigationRouteProbe() } catch(error: Exception) {JSONObject().put("result","failed").put("type",error.javaClass.simpleName)}
            finish(if(result.optString("result")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply {putString("result",result.toString())});return
        }
        if (mode == "warning_booking_flow") {
            val result=runWarningBookingFlowProbe()
            finish(if(result.optString("status")=="passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply{putString("result",result.toString())})
            return
        }
        if (mode == "booking_status") {
            val result = try { runBookingStatusProbe() } catch (error: Exception) {
                JSONObject().put("result", "failed").put("type", error.javaClass.simpleName)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "warnings") {
            val result = try { runWarningProbe() } catch (error: Exception) {
                JSONObject().put("result", "failed").put("type", error.javaClass.simpleName)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "warning_inventory") {
            val result = try { runWarningInventoryProbe() } catch (error: Exception) {
                JSONObject().put("test", "warning_inventory_v1").put("result", "failed").put("type", error.javaClass.simpleName)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "watch_warning") {
            val result = try { runWarningConsoleProbe() } catch (error: Exception) {
                warningConsoleProbeFailure(error)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "fused_location" || mode == "fused_network") {
            val result=try { runFusedLocationProbe(upload = mode == "fused_network") }
                catch(error: Exception) { JSONObject().put("result","failed").put("reason",error.message?.take(100)) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "enable_local_network") {
            val store = AppConsentStore(targetContext)
            val before = store.read()

            store.write(before.copy(reviewed = true, vehicleRead = true, network = true))
            val after = store.read()
            val result = JSONObject().put("test", "enable_local_network").put("vehicleRead", after.vehicleRead)
                .put("network", after.network).put("otherChoicesUnchanged", after.location == before.location &&
                    after.manualControl == before.manualControl && after.resumeAfterBoot == before.resumeAfterBoot)
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "native_network") {
            val result = try { runNativeNetworkProbe() } catch (error: Exception) {
                JSONObject().put("result", "failed").put("reason", error.message?.take(100)).put("type", error.javaClass.simpleName)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "enable_boot_resume" || mode == "boot_status") {
            val store = AppConsentStore(targetContext)
            val before = store.read()
            if (mode == "fused_location" || mode == "fused_network") {
            val result=try { runFusedLocationProbe(upload = mode == "fused_network") }
                catch(error: Exception) { JSONObject().put("result","failed").put("reason",error.message?.take(100)) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "enable_local_network") {
            val store = AppConsentStore(targetContext)
            val before = store.read()

            store.write(before.copy(reviewed = true, vehicleRead = true, network = true))
            val after = store.read()
            val result = JSONObject().put("test", "enable_local_network").put("vehicleRead", after.vehicleRead)
                .put("network", after.network).put("otherChoicesUnchanged", after.location == before.location &&
                    after.manualControl == before.manualControl && after.resumeAfterBoot == before.resumeAfterBoot)
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "native_network") {
            val result = try { runNativeNetworkProbe() } catch (error: Exception) {
                JSONObject().put("result", "failed").put("reason", error.message?.take(100)).put("type", error.javaClass.simpleName)
            }
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "enable_boot_resume") {
                check(before.reviewed && before.vehicleRead) { "existing_observation_consent_required" }

                store.write(before.copy(resumeAfterBoot = true))
            }
            val choice = store.read()
            val snapshot = VehicleObservationService.snapshots.value
            val fresh = snapshot.readings.count { it.state == "수신" && it.observedAt?.let { t -> System.currentTimeMillis()-t in 0..30_000 } == true }
            val result = JSONObject().put("test", mode).put("resumeAfterBoot", choice.resumeAfterBoot)
                .put("otherChoicesUnchanged", choice.copy(resumeAfterBoot = before.resumeAfterBoot) == before)
                .put("observationActive", snapshot.active).put("freshReadingCount", fresh)
                .put("sdk", android.os.Build.VERSION.SDK_INT).put("uptimeMs", SystemClock.elapsedRealtime())
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) }); return
        }
        if (mode == "navigation" || mode == "gleo") {
            val result = try { if (mode == "navigation") runNavigationProbe() else runGleoProbe() }
                catch (error: Exception) { JSONObject().put("result", "failed").put("type", error.javaClass.simpleName) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "watch_wiper") {
            val store = AppConsentStore(targetContext)
            if (!store.read().vehicleRead) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("result", "existing_observation_consent_required") }); return
            }
            targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
            val samples = org.json.JSONArray()
            var lastTime = 0L
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (SystemClock.elapsedRealtime() < deadline && store.read().vehicleRead) {
                val frame = VehicleObservationService.snapshots.value
                val reading = frame.readings.find { it.key == "wiper.front.active" }
                if (reading?.state == "수신" && (reading.observedAt ?: 0) > lastTime) {
                    lastTime = reading.observedAt!!
                    samples.put(JSONObject().put("active", reading.value == "true").put("observedAt", lastTime)
                        .put("switch", frame.readings.find { it.key == "Wiper.getCurrentWiperSwitch.FRONT_WINDSHIELD" }?.value ?: JSONObject.NULL))
                }
                SystemClock.sleep(200)
            }
            val values = (0 until samples.length()).map { samples.getJSONObject(it).getBoolean("active") }
            val result = JSONObject().put("test", "console_wiper_observation_v1").put("source", "pleos_sdk_passive_observation")
                .put("sensorOnObserved", true in values).put("sensorOffObserved", false in values).put("samples", samples)
            targetContext.getFileStreamPath("console-wiper-observation.json").writeText(result.toString(2))
            finish(if (true in values && false in values) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        if (mode == "wiper" || mode == "restore_wiper") {
            val result = runWiperObservationProbe(restoreOnly = mode == "restore_wiper")
            targetContext.getFileStreamPath("wiper-observation-test.json").writeText(result.toString(2))
            finish(if (result.optString("result") == "passed") Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("result", result.toString()) })
            return
        }
        val store = AppConsentStore(targetContext)
        val before = store.read()
        val result = JSONObject().put("test", "observation_lifecycle_v1").put("rawValuesIncluded", false)
        if (!before.reviewed || !before.vehicleRead) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("result", "blocked_existing_consent_required") })
            return
        }
        var code = Activity.RESULT_CANCELED
        val intent = Intent(targetContext, VehicleObservationService::class.java)
        try {
            targetContext.startForegroundService(intent)
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (VehicleObservationService.snapshots.value.readings.size < 475 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            SystemClock.sleep(1500)
            val initial = VehicleObservationService.snapshots.value
            val firstTime = initial.readings.find { it.key == "wiper.front.active" }?.observedAt ?: error("wiper_not_received")
            SystemClock.sleep(6500)
            val later = VehicleObservationService.snapshots.value
            val laterTime = later.readings.find { it.key == "wiper.front.active" }?.observedAt ?: error("wiper_not_received_later")
            check(later.active && laterTime > firstTime) { "background_observations_not_advancing" }
            result.put("withoutActivityObservationAdvanced", true)
                .put("firstWiperAt", firstTime).put("laterWiperAt", laterTime)
                .put("readTaskCount", later.readings.size)
                .put("states", JSONObject(later.readings.groupingBy { it.state }.eachCount()))
            val domains = JSONObject()
            later.readings.groupBy { it.key.substringBefore('.') }.forEach { (domain, rows) ->
                domains.put(domain, JSONObject(rows.groupingBy { it.state }.eachCount()))
            }
            result.put("domains", domains)
            result.put("unresolved", org.json.JSONArray().apply {
                later.readings.filter { it.state == "조회 실패 · 원인 미확인" }.forEach {
                    put(JSONObject().put("key", it.key).put("state", it.state).put("code", it.failureCode ?: JSONObject.NULL).put("type", it.failureType ?: JSONObject.NULL))
                }
            })
            store.write(before.copy(vehicleRead = false).normalized())
            val stopDeadline = SystemClock.elapsedRealtime() + 5000
            while (VehicleObservationService.snapshots.value.active && SystemClock.elapsedRealtime() < stopDeadline) SystemClock.sleep(100)
            check(!VehicleObservationService.snapshots.value.active && VehicleObservationService.snapshots.value.readings.isEmpty()) { "withdrawal_not_cleared" }
            SystemClock.sleep(1500)
            check(VehicleObservationService.snapshots.value.readings.isEmpty()) { "late_callback_after_withdrawal" }
            check(!store.read().network && !store.read().manualControl) { "dependent_consent_not_disabled" }
            result.put("withdrawalStoppedAndCleared", true).put("lateCallbacksBlocked", true).put("dependentConsentsDisabled", true)
            code = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.put("failureType", error.javaClass.simpleName)
            result.put("failure", error.message?.take(180))
        } finally {
            targetContext.stopService(intent)
            store.write(before)
            result.put("originalChoicesRestored", store.read() == before)
            targetContext.getFileStreamPath("observation-lifecycle-test.json").writeText(result.toString(2))
        }
        finish(code, Bundle().apply { putString("result", result.toString()) })
    }
}
