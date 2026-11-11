package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Build
import ai.pleos.playground.vehicle.Vehicle
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.snapshotErrorCode
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runUnresolvedReadsProbe(): JSONObject {
    val store = AppConsentStore(targetContext)
    val before = store.read()
    val revision = store.revision()
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("crp_avd")
    val result = JSONObject().put("test", "unresolved_reads_v1").put("rawValuesIncluded", false)
    if (!BuildConfig.DEBUG || !emulator || !before.reviewed || !before.vehicleRead)
        return result.put("status", "blocked")
    val rows = JSONArray()
    val vehicle = Vehicle(targetContext.applicationContext)
    fun unchanged() = before == store.read() && revision == store.revision()
    fun invokeRead(key: String, permission: String, operation: (((JSONObject) -> Unit), ((Exception) -> Unit)) -> Unit) {
        val row = JSONObject().put("key", key)
        if (!unchanged() || !store.read().vehicleRead) { rows.put(row.put("status", "consent_changed")); return }
        if (targetContext.checkSelfPermission("pleos.car.permission.$permission") != PackageManager.PERMISSION_GRANTED) {
            rows.put(row.put("status", "permission_missing")); return
        }
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<JSONObject?>(null)
        fun failure(error: Exception, stage: String) {
            val entry = JSONObject().put("status", "error").put("stage", stage)
                .put("errorClass", error.javaClass.simpleName)
                .put("causeClass", error.cause?.javaClass?.simpleName ?: JSONObject.NULL)
                .put("errorCode", snapshotErrorCode(error) ?: JSONObject.NULL)
                .put("sdkStack", JSONArray(error.stackTrace.filter {
                    it.className.startsWith("ai.pleos.playground.vehicle.")
                }.take(4).map { "${it.className}.${it.methodName}" }))
            if (outcome.compareAndSet(null, entry)) latch.countDown()
        }
        runOnMainSync {
            try { operation({ value -> if (outcome.compareAndSet(null, value.put("status", "received"))) latch.countDown() },
                { failure(it, "callback") }) }
            catch (error: Exception) { failure(error, "invoke") }
        }
        latch.await(3, TimeUnit.SECONDS)
        val value = outcome.get() ?: JSONObject().put("status", "timeout")
        value.keys().forEach { row.put(it, value.get(it)) }
        rows.put(row)
    }
    try {
        runOnMainSync { vehicle.initialize() }
        invokeRead("Light.checkFrontFogLightStateCapabilities", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().checkFrontFogLightStateCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Light.getCurrentFrontFogLightState", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().getCurrentFrontFogLightState(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Light.checkFrontFogLightSwitchCapabilities", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().checkFrontFogLightSwitchCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Light.getFrontFogLightSwitch", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().getFrontFogLightSwitch(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Light.checkRearFogLightStateCapabilities", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().checkRearFogLightStateCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Light.getCurrentRearFogLightState", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().getCurrentRearFogLightState(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Light.checkRearFogLightSwitchCapabilities", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().checkRearFogLightSwitchCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Light.getRearFogLightSwitch", "CAR_LIGHTS") { ok, fail ->
            vehicle.getLight().getRearFogLightSwitch(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Safety.checkEngineOilLevelStateCapabilities", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().checkEngineOilLevelStateCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Safety.getEngineOilLevelState", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().getEngineOilLevelState(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Safety.checkBrakeFluidLevelStateCapabilities", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().checkBrakeFluidLevelStateCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Safety.getBrakeFluidLevelState", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().getBrakeFluidLevelState(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Safety.checkTransmissionOilLevelStateCapabilities", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().checkTransmissionOilLevelStateCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Safety.getTransmissionOilLevelState", "CAR_SAFETY") { ok, fail ->
            vehicle.getSafety().getTransmissionOilLevelState(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        invokeRead("Toll.checkEtcsVoiceGuidanceVolumeCapabilities", "CAR_TOLL") { ok, fail ->
            vehicle.getToll().checkEtcsVoiceGuidanceVolumeCapabilities(onComplete = { config, _ ->
                ok(JSONObject().put("accessType", config.accessType.name))
            }, onFailed = fail)
        }
        invokeRead("Toll.getCurrentEtcsVoiceGuidanceVolume", "CAR_TOLL") { ok, fail ->
            vehicle.getToll().getCurrentEtcsVoiceGuidanceVolume(onComplete = { value ->
                ok(JSONObject().put("valuePresent", value != null))
            }, onFailed = fail)
        }
        result.put("status", if (unchanged()) "completed" else "consent_changed")
    } catch (error: Exception) {
        result.put("status", "initialization_error").put("errorClass", error.javaClass.simpleName)
    } finally {
        runOnMainSync { vehicle.release() }
    }
    return result.put("rows", if (unchanged()) rows else JSONArray())
        .put("choicesUnchanged", unchanged()).put("writesPerformed", false)
}
