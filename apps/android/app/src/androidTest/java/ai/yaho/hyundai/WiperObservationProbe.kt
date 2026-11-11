package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import ai.yaho.hyundai.core.DrivingUxState
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.DrivingUxMonitor
import ai.yaho.hyundai.vehicle.VehicleControlAdapter
import ai.yaho.hyundai.vehicle.VehicleControlStatus
import ai.yaho.hyundai.vehicle.VehicleObservationService
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runWiperObservationProbe(restoreOnly: Boolean = false): JSONObject {
    val result = JSONObject().put("test", "emulator_wiper_readback_v1").put("source", "pleos_emulator_sdk")
    val store = AppConsentStore(targetContext)
    val originalChoice = store.read()
    check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator_only" }
    check(originalChoice.reviewed && originalChoice.vehicleRead && originalChoice.manualControl) { "existing_control_consent_required" }
    val status = AtomicReference(VehicleControlStatus())
    val ux = AtomicReference(DrivingUxState())
    val speed = AtomicReference<Float?>(null)
    var monitor: DrivingUxMonitor? = null
    var controls: VehicleControlAdapter? = null
    fun allowed(): Boolean {
        val snapshot = VehicleObservationService.snapshots.value
        val gear = snapshot.readings.find { it.key == "Gear.getCurrentGearSelectionState" }
        val freshGear = gear?.state == "수신" && gear.observedAt?.let { System.currentTimeMillis() - it in 0..30_000 } == true
        return store.read().manualControl && freshGear && gear?.value in setOf("PARK", "GEAR_PARK") &&
            speed.get()?.let { it.isFinite() && kotlin.math.abs(it) < .5f } == true && !ux.get().restricted
    }
    fun waitFor(timeout: Long = 12_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        return condition()
    }
    var changed = false
    try {
        runOnMainSync {
            targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
            monitor = DrivingUxMonitor(targetContext, { ux.set(it) }, { speed.set(it) }).also { it.start() }
            controls = VehicleControlAdapter(targetContext, ::allowed, { status.set(it) }).also { it.start() }
        }
        check(waitFor { status.get().wiperSwitch != null && allowed() }) { "parked_control_or_sdk_unavailable" }
        val initial = status.get()
        result.put("parkedAndPlatformAllowed", true).put("initialSwitch", initial.wiperSwitch)
        if (restoreOnly) {
            check(initial.wiperWritable && initial.wiperSwitch in setOf("OFF", "CONTINUOUS_LEVEL_1")) { "restore_state_not_recognized" }
            if (initial.wiperSwitch != "OFF") runOnMainSync { controls?.setWiperSwitch("OFF") }
            val restored = waitFor(40_000) {
                val rows = VehicleObservationService.snapshots.value.readings
                rows.any { it.key == "wiper.front.active" && it.state == "수신" && it.value == "false" } &&
                    rows.any { it.key == "Wiper.getCurrentWiperSwitch.FRONT_WINDSHIELD" && it.state == "수신" && it.value == "OFF" }
            }
            result.put("restoreOnly", true).put("originalSwitchRestored", restored).put("result", if (restored) "passed" else "restore_unconfirmed_no_retry")
            return result
        }
        check(initial.wiperWritable && initial.wiperSwitch == "OFF" && "CONTINUOUS_LEVEL_1" in initial.wiperOptions) { "requires_supported_low_and_initial_off" }
        val before = VehicleObservationService.snapshots.value.readings.find { it.key == "wiper.front.active" }
        check(before?.state == "수신" && before.value == "false") { "initial_sensor_not_off" }
        runOnMainSync { controls?.setWiperSwitch("CONTINUOUS_LEVEL_1") }
        check(waitFor(32_000) { status.get().outcome == "verified" }) { "switch_receipt_unresolved_no_retry" }
        check(status.get().outcome == "verified") { "switch_outcome_unknown_no_retry" }
        changed = true
        result.put("switchContinuousReadbackVerified", true)
        val sensorOn = waitFor { VehicleObservationService.snapshots.value.readings.any { it.key == "wiper.front.active" && it.state == "수신" && it.value == "true" && (it.observedAt ?: 0) > (before.observedAt ?: 0) } }
        result.put("automaticSensorOnObserved", sensorOn)

        check(allowed()) { "restore_requires_parked_platform_permission" }
        runOnMainSync { controls?.setWiperSwitch("OFF") }
        check(waitFor { status.get().outcome == "unknown" || status.get().wiperSwitch == "OFF" }) { "restore_timeout" }
        check(status.get().outcome == "verified" && status.get().wiperSwitch == "OFF") { "restore_unknown_no_retry" }
        changed = false
        result.put("originalSwitchRestored", true)
        val sensorOff = waitFor { VehicleObservationService.snapshots.value.readings.any { it.key == "wiper.front.active" && it.state == "수신" && it.value == "false" } }
        result.put("automaticSensorOffObserved", sensorOff)
        result.put("result", if (sensorOn && sensorOff) "passed" else "sensor_does_not_follow_switch_in_emulator")
    } catch (error: Exception) {
        result.put("result", "blocked_or_failed").put("reason", error.message?.take(120))
            .put("targetStateObserved", status.get().currentTargetObserved)
            .put("failureType", status.get().failureType ?: JSONObject.NULL)
            .put("failureCode", status.get().failureCode ?: JSONObject.NULL)
    } finally {

        if (changed && status.get().outcome == "verified" && allowed()) {
            runOnMainSync { controls?.setWiperSwitch("OFF") }
            val restored = waitFor { status.get().outcome == "unknown" || status.get().wiperSwitch == "OFF" }
            result.put("originalSwitchRestored", restored && status.get().outcome == "verified" && status.get().wiperSwitch == "OFF")
        }
        runOnMainSync { controls?.stop(); monitor?.release() }
        result.put("consentUnchanged", store.read() == originalChoice)
    }
    return result
}
