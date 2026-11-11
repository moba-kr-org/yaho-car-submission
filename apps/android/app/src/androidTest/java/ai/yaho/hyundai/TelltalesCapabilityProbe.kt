package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Build
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.yaho.hyundai.data.AppConsentStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val runtimePermissionNameWhitelist = listOf(
    "CAR_SAFETY",
    "PRIVATE_CAR_SAFETY",
    "VEHICLE_BINDING",
    "CONTROL_CAR_SAFETY",
)


private fun runtimePermissionHints(error: Exception): JSONArray {
    val tokens = error.message.orEmpty().split(Regex("[^A-Za-z0-9_]+"))
    return JSONArray().apply {
        runtimePermissionNameWhitelist.filter { it in tokens }.forEach(::put)
    }
}


internal fun Instrumentation.runTelltalesCapabilityProbe(): JSONObject {
    val consentStore = AppConsentStore(targetContext)
    val choicesBefore = consentStore.read()
    val revisionBefore = consentStore.revision()
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") ||
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("emulator", ignoreCase = true) ||
        Build.PRODUCT.startsWith("crp_avd", ignoreCase = true)
    val appCarSafetyPermission = "pleos.car.permission.CAR_SAFETY"
    val requiredCapabilityPermission = "pleos.car.permission.PRIVATE_CAR_SAFETY"
    val requestedPermissions = targetContext.packageManager
        .getPackageInfo(targetContext.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().toSet()
    val appCarSafetyDeclared = appCarSafetyPermission in requestedPermissions
    val appCarSafetyGranted = targetContext.checkSelfPermission(appCarSafetyPermission) == PackageManager.PERMISSION_GRANTED
    val capabilityPermissionDeclared = requiredCapabilityPermission in requestedPermissions
    val capabilityPermissionGranted = targetContext.checkSelfPermission(requiredCapabilityPermission) == PackageManager.PERMISSION_GRANTED

    fun base(status: String) = JSONObject()
        .put("test", "telltales_capability_metadata_v1")
        .put("source", "pleos_emulator_capability_metadata")
        .put("status", status)
        .put("emulator", emulator)
        .put("consentReady", choicesBefore.reviewed && choicesBefore.vehicleRead)
        .put("appCarSafetyPermissionDeclared", appCarSafetyDeclared)
        .put("appCarSafetyPermissionGranted", appCarSafetyGranted)
        .put("requiredCapabilityPermission", requiredCapabilityPermission)
        .put("requiredCapabilityPermissionDeclared", capabilityPermissionDeclared)
        .put("requiredCapabilityPermissionGranted", capabilityPermissionGranted)
        .put("permissionRequirementSource", "Vehicle2.3.2 AAR annotation")
        .put("getterCalled", false)
        .put("signalValuesIncluded", false)
        .put("actualVehicleWarningValuesValidated", false)
        .put("capabilityMethodCalled", false)
        .put("vehicleInitializeCalled", false)
        .put("choicesUnchanged", choicesBefore == consentStore.read() && revisionBefore == consentStore.revision())

    if (!emulator) return base("blocked").put("reason", "emulator_required")
    if (!choicesBefore.reviewed || !choicesBefore.vehicleRead) {
        return base("blocked").put("reason", "existing_vehicle_read_consent_required")
    }
    if (!capabilityPermissionDeclared || !capabilityPermissionGranted) {
        return base("permission_required").put("reason", "private_car_safety_permission_required")
    }

    data class CallbackResult(val accessType: String? = null, val errorClass: String? = null)

    val completed = CountDownLatch(1)
    val outcome = AtomicReference<CallbackResult?>(null)
    val capabilityMethodCalled = java.util.concurrent.atomic.AtomicBoolean(false)
    val vehicleInitializeCalled = java.util.concurrent.atomic.AtomicBoolean(false)
    var vehicle: Vehicle? = null
    try {
        runOnMainSync {
            try {
                val initializedVehicle = Vehicle(targetContext.applicationContext)
                vehicle = initializedVehicle
                vehicleInitializeCalled.set(true)
                initializedVehicle.initialize()
                val safety = initializedVehicle.getSafety()
                capabilityMethodCalled.set(true)
                safety.checkTelltalesCapabilities(
                    onComplete = { capability ->
                        outcome.compareAndSet(null, CallbackResult(accessType = capability.accessType.name))
                        completed.countDown()
                    },
                    onFailed = { error ->
                        outcome.compareAndSet(null, CallbackResult(errorClass = error.javaClass.simpleName))
                        completed.countDown()
                    },
                )
            } catch (error: Exception) {
                outcome.compareAndSet(null, CallbackResult(errorClass = error.javaClass.simpleName))
                completed.countDown()
            }
        }

        val received = completed.await(3, TimeUnit.SECONDS)
        val callback = outcome.get()
        val result = when {
            !received -> base("timeout")
            callback?.errorClass != null -> base("SDKerror").put("errorClass", callback.errorClass)
            callback?.accessType != null -> base("received")
                .put("accessType", callback.accessType)
                .put("capabilityMetadataReceived", true)
            else -> base("SDKerror").put("errorClass", "MissingCapabilityMetadata")
        }
        result.put("capabilityMethodCalled", capabilityMethodCalled.get())
            .put("vehicleInitializeCalled", vehicleInitializeCalled.get())
        return result
    } finally {
        try { runOnMainSync { vehicle?.release() } } catch (_: Exception) { }
    }
}


internal fun Instrumentation.runTelltalesReadOnceProbe(): JSONObject {
    val consentStore = AppConsentStore(targetContext)
    val choicesBefore = consentStore.read()
    val revisionBefore = consentStore.revision()
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") ||
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("emulator", ignoreCase = true) ||
        Build.PRODUCT.startsWith("crp_avd", ignoreCase = true)
    val appCarSafetyPermission = "pleos.car.permission.CAR_SAFETY"
    val requestedPermissions = targetContext.packageManager
        .getPackageInfo(targetContext.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().toSet()
    val appCarSafetyDeclared = appCarSafetyPermission in requestedPermissions
    val appCarSafetyGranted = targetContext.checkSelfPermission(appCarSafetyPermission) == PackageManager.PERMISSION_GRANTED

    fun base(status: String, getterCalls: Int = 0) = JSONObject()
        .put("test", "telltales_read_once_v1")
        .put("source", "pleos_emulator_telltale_once")
        .put("status", status)
        .put("emulator", emulator)
        .put("debugBuild", BuildConfig.DEBUG)
        .put("consentReady", choicesBefore.reviewed && choicesBefore.vehicleRead)
        .put("appCarSafetyPermissionDeclared", appCarSafetyDeclared)
        .put("appCarSafetyPermissionGranted", appCarSafetyGranted)
        .put("permissionRequirementSource", "Vehicle2.3.2 AAR annotation")
        .put("dataScope", "emulator_fixture_once")
        .put("getterCalls", getterCalls)
        .put("vehicleInitializeCalled", false)
        .put("privateCapabilityCalled", false)
        .put("listenerRegistered", false)
        .put("productionCollection", false)
        .put("choicesUnchanged", choicesBefore == consentStore.read() && revisionBefore == consentStore.revision())

    if (!emulator) return base("blocked").put("reason", "emulator_required")
    if (!choicesBefore.reviewed || !choicesBefore.vehicleRead) {
        return base("blocked").put("reason", "existing_vehicle_read_consent_required")
    }
    if (!BuildConfig.DEBUG) return base("blocked").put("reason", "debug_build_required")
    if (!appCarSafetyDeclared || !appCarSafetyGranted) {
        return base("permission_required").put("reason", "car_safety_permission_required")
    }

    data class ReadResult(
        val powerDownState: String? = null,
        val errorClass: String? = null,
        val failureStage: String? = null,
        val permissionHints: JSONArray? = null,
    )
    val completed = CountDownLatch(1)
    val outcome = AtomicReference<ReadResult?>(null)
    val getterCalls = java.util.concurrent.atomic.AtomicInteger(0)
    val vehicleInitializeCalled = java.util.concurrent.atomic.AtomicBoolean(false)
    val vehicleInitializeCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
    val getterMethodEntered = java.util.concurrent.atomic.AtomicBoolean(false)
    val failureStage = AtomicReference("initialize")
    var vehicle: Vehicle? = null
    try {
        try {
            runOnMainSync {
                try {
                    val initializedVehicle = Vehicle(targetContext.applicationContext)
                    vehicle = initializedVehicle
                    vehicleInitializeCalled.set(true)
                    initializedVehicle.initialize()
                    vehicleInitializeCompleted.set(true)
                    val safety = initializedVehicle.getSafety()
                    failureStage.set("getter_call")
                    getterCalls.incrementAndGet()
                    getterMethodEntered.set(true)
                    safety.getTelltalesInfo(
                        onComplete = { telltales ->
                            val powerDownState = try {
                                val matches = telltales.telltales.filter { it.type == TelltaleType.POWER_DOWN }
                                when (matches.size) {
                                    0 -> "not_present"
                                    1 -> when (matches.single().state) {
                                        TelltaleState.OFF -> "OFF"
                                        TelltaleState.ON -> "ON"
                                        TelltaleState.BLINK -> "BLINK"
                                        else -> "unknown"
                                    }
                                    else -> "ambiguous"
                                }
                            } catch (_: Exception) {
                                "unknown"
                            }
                            outcome.compareAndSet(null, ReadResult(powerDownState = powerDownState))
                            completed.countDown()
                        },
                        onFailed = { error ->
                            failureStage.set("getter_callback")
                            outcome.compareAndSet(null, ReadResult(
                                errorClass = error.javaClass.simpleName,
                                failureStage = "getter_callback",
                                permissionHints = runtimePermissionHints(error),
                            ))
                            completed.countDown()
                        },
                    )
                } catch (error: Exception) {
                    outcome.compareAndSet(null, ReadResult(
                        errorClass = error.javaClass.simpleName,
                        failureStage = failureStage.get(),
                        permissionHints = runtimePermissionHints(error),
                    ))
                    completed.countDown()
                }
            }
        } catch (error: Exception) {
            outcome.compareAndSet(null, ReadResult(
                errorClass = error.javaClass.simpleName,
                failureStage = failureStage.get(),
                permissionHints = runtimePermissionHints(error),
            ))
            completed.countDown()
        }

        failureStage.set("getter_callback")
        val received = try {
            completed.await(3, TimeUnit.SECONDS)
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            val result = base("SDKerror", getterCalls.get())
                .put("SDKreceived", false)
                .put("errorClass", error.javaClass.simpleName)
                .put("failureStage", failureStage.get())
                .put("runtimePermissionHints", runtimePermissionHints(error))
                .put("runtimePermissionHintsAreRequirements", false)
                .put("vehicleInitializeCalled", vehicleInitializeCalled.get())
                .put("vehicleInitializeCompleted", vehicleInitializeCompleted.get())
                .put("getterMethodEntered", getterMethodEntered.get())
            return result
        }
        val callback = outcome.get()
        val result = when {
            !received -> base("timeout", getterCalls.get()).put("failureStage", "getter_callback")
            callback?.errorClass != null -> base("SDKerror", getterCalls.get())
                .put("errorClass", callback.errorClass)
                .put("failureStage", callback.failureStage ?: failureStage.get())
                .put("runtimePermissionHints", callback.permissionHints ?: JSONArray())
                .put("runtimePermissionHintsAreRequirements", false)
            callback?.powerDownState != null -> base("received", getterCalls.get())
                .put("SDKreceived", true)
                .put("selectedPowerDownState", callback.powerDownState)
            else -> base("SDKerror", getterCalls.get()).put("SDKreceived", false)
                .put("errorClass", "MissingTelltaleData")
                .put("failureStage", failureStage.get())
                .put("runtimePermissionHints", JSONArray())
                .put("runtimePermissionHintsAreRequirements", false)
        }
        result.put("vehicleInitializeCalled", vehicleInitializeCalled.get())
            .put("vehicleInitializeCompleted", vehicleInitializeCompleted.get())
            .put("getterMethodEntered", getterMethodEntered.get())
        if (!result.has("SDKreceived")) result.put("SDKreceived", false)
        return result
    } finally {
        try { runOnMainSync { vehicle?.release() } } catch (_: Exception) { }
    }
}
