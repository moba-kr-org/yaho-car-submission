package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Build
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.VehicleObservationService
import org.json.JSONObject


internal fun Instrumentation.runWarningProbe(): JSONObject {
    val store = AppConsentStore(targetContext)
    val before = store.read()
    if (!before.vehicleRead) return JSONObject().put("result","blocked").put("reason","existing_consent_required")
    targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
    val deadline = SystemClock.elapsedRealtime()+20_000
    var rows = emptyList<WarningObservation>()
    var active = false
    while (SystemClock.elapsedRealtime()<deadline) {
        val snapshot = VehicleObservationService.snapshots.value
        active = snapshot.active
        rows = snapshot.readings.filter { it.key == "Safety.hasLowWasherFluidWarning" || it.key == "Safety.hasPowerDownWarningIndicator" || it.key.startsWith("Safety.hasSeatBeltNotBuckledWarning.") }
            .map { WarningObservation(it.key,it.state,it.value,it.observedAt,it.receivedElapsed) }
        if (rows.count { it.state == "수신" && it.receivedElapsed != null }>=1) break
        SystemClock.sleep(100)
    }
    val initialReceipts = rows.associate { it.key to it.receivedElapsed }
    val refreshDeadline = SystemClock.elapsedRealtime()+15_000
    var refreshed = false
    while (SystemClock.elapsedRealtime()<refreshDeadline) {
        val snapshot=VehicleObservationService.snapshots.value
        rows=snapshot.readings.filter { isSupportedWarningKey(it.key) }
            .map { WarningObservation(it.key,it.state,it.value,it.observedAt,it.receivedElapsed) }
        active=snapshot.active
        refreshed=rows.any { it.state == "수신" && it.receivedElapsed != null && initialReceipts[it.key]?.let { first -> it.receivedElapsed > first } == true }
        if(refreshed) break
        SystemClock.sleep(100)
    }
    val now = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime()
    val assessment = assessVehicleWarnings(rows,active && store.read().vehicleRead,now,elapsed)
    val expired = assessVehicleWarnings(rows,true,now+30_001,elapsed+30_001)
    val withdrawn = assessVehicleWarnings(rows,false,now,elapsed)
    val result = JSONObject().put("test","warning_sdk_v1").put("sdkSource","pleos")
        .put("emulator",android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        .put("scope","passive_sdk_refresh_not_warning_on_off")
        .put("actualWarningOnOffValidated",false).put("freshWarningRefreshed",refreshed)
        .put("checked",assessment.checked).put("unavailable",assessment.unavailable).put("activeCount",assessment.active.size)
        .put("monotonicReceipts",rows.count { it.state == "수신" && it.receivedElapsed != null })
        .put("expiredSuppressed",expired.active.isEmpty()).put("withdrawalSuppressed",withdrawn.active.isEmpty())
        .put("choicesUnchanged",before == store.read()).put("rawValuesIncluded",false).put("externalRequest",false)
    return result.put("result",if(assessment.checked>=1 && refreshed && expired.active.isEmpty() && withdrawn.active.isEmpty() && before == store.read()) "passed" else "incomplete")
}

private val warningInventoryKeys = listOf(
    "Safety.hasLowWasherFluidWarning",
    "Safety.hasPowerDownWarningIndicator",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_1_LEFT",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_1_CENTER",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_1_RIGHT",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_2_LEFT",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_2_CENTER",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_2_RIGHT",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_3_LEFT",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_3_CENTER",
    "Safety.hasSeatBeltNotBuckledWarning.ROW_3_RIGHT",
)

private fun Instrumentation.ensureWarningCollectorActive(store: AppConsentStore): Boolean = runCatching {
    if (!store.read().vehicleRead) return@runCatching false
    if (!VehicleObservationService.snapshots.value.active) {
        targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
    }
    val deadline = SystemClock.elapsedRealtime() + 5_000
    while (SystemClock.elapsedRealtime() < deadline && store.read().vehicleRead &&
        !VehicleObservationService.snapshots.value.active) {
        SystemClock.sleep(100)
    }
    store.read().vehicleRead && VehicleObservationService.snapshots.value.active
}.getOrDefault(false)

private fun inventoryState(state: String?): String = when (state) {
    "수신" -> "received"
    "미지원", "미지원 부위" -> "unsupported"
    "읽기 대기" -> "waiting"
    null -> "not_observed"
    else -> "unavailable"
}


internal fun Instrumentation.runWarningInventoryProbe(): JSONObject {
    val store = AppConsentStore(targetContext)
    val before = store.read()
    val revisionBefore = store.revision()
    if (!before.reviewed || !before.vehicleRead) {
        return JSONObject().put("test", "warning_inventory_v1").put("result", "blocked")
            .put("reason", "existing_vehicle_read_consent_required")
    }
    if (!ensureWarningCollectorActive(store)) {
        val choicesUnchanged = before == store.read() && revisionBefore == store.revision()
        return JSONObject().put("test", "warning_inventory_v1").put("result", "incomplete")
            .put("reason", "collector_start_unavailable").put("choicesUnchanged", choicesUnchanged)
    }

    val started = SystemClock.elapsedRealtime()
    val deadline = started + 10_000
    while (SystemClock.elapsedRealtime() < deadline && store.read().vehicleRead &&
        VehicleObservationService.snapshots.value.active) {
        SystemClock.sleep(100)
    }

    val snapshot = VehicleObservationService.snapshots.value
    val byKey = snapshot.readings.filter { it.key in warningInventoryKeys }.associateBy { it.key }
    val items = org.json.JSONArray()
    warningInventoryKeys.forEach { key ->
        val state = inventoryState(byKey[key]?.state)
        items.put(JSONObject().put("key", key).put("state", state).put("receivable", state == "received"))
    }
    val choicesUnchanged = before == store.read() && revisionBefore == store.revision()
    val activeWindowObserved = snapshot.active && SystemClock.elapsedRealtime() - started >= 10_000
    val result = JSONObject()
        .put("test", "warning_inventory_v1")
        .put("source", "pleos_sdk_warning_inventory")
        .put("collectorActive", snapshot.active)
        .put("activeWindowObserved", activeWindowObserved)
        .put("keys", items)
        .put("choicesUnchanged", choicesUnchanged)
        .put("rawValuesIncluded", false)
    return result.put("result", if (activeWindowObserved && choicesUnchanged) "passed" else "incomplete")
}

private data class WarningReceipt(val observedAt: Long, val receivedElapsed: Long)
private data class WarningCycleProgress(
    var offBeforeOn: WarningReceipt? = null,
    var onAfterOff: WarningReceipt? = null,
    var offAfterOn: WarningReceipt? = null,
    var differentReceipts: Int = 0,
)

private fun WarningReceipt.follows(previous: WarningReceipt): Boolean =
    observedAt > previous.observedAt && receivedElapsed > previous.receivedElapsed

private fun warningConsoleBase(emulator: Boolean, result: String, choicesUnchanged: Boolean, reason: String? = null) =
    JSONObject()
        .put("source", "pleos_emulator_sdk_console_test")
        .put("staticMetadata", JSONObject().put("emulator", emulator).put("sdkInt", Build.VERSION.SDK_INT)
            .put("collector", "VehicleObservationService"))
        .put("matchedKey", JSONObject.NULL)
        .put("phaseBooleans", JSONObject().put("offBeforeOn", false).put("onAfterOff", false).put("offAfterOn", false))
        .put("elapsedMs", 0)
        .put("differentReceipts", 0)
        .put("choicesUnchanged", choicesUnchanged)
        .put("result", result)
        .apply { if (reason != null) put("reason", reason) }

internal fun warningConsoleProbeFailure(error: Exception): JSONObject =
    warningConsoleBase(
        emulator = Build.HARDWARE in setOf("ranchu", "goldfish"),
        result = "failed",
        choicesUnchanged = false,
        reason = error.javaClass.simpleName,
    )


internal fun Instrumentation.runWarningConsoleProbe(): JSONObject {
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish")
    if (!emulator) return warningConsoleBase(false, "blocked", false, "emulator_required")

    val store = AppConsentStore(targetContext)
    val before = store.read()
    val revisionBefore = store.revision()
    if (!before.reviewed || !before.vehicleRead) {
        return warningConsoleBase(true, "blocked", true, "existing_vehicle_read_consent_required")
    }
    if (!ensureWarningCollectorActive(store)) {
        val choicesUnchanged = before == store.read() && revisionBefore == store.revision()
        return warningConsoleBase(true, "incomplete", choicesUnchanged, "collector_start_unavailable")
    }

    val progress = linkedMapOf<String, WarningCycleProgress>()
    val lastReceipt = mutableMapOf<String, WarningReceipt>()
    val started = SystemClock.elapsedRealtime()
    val deadline = started + 90_000
    var matchedKey: String? = null
    var bestProgress: WarningCycleProgress? = null
    var bestProgressRank = -1
    fun emitPhase(phase: String, key: String) {
        sendStatus(0, Bundle().apply {
            putString("warningPhase", phase)
            putString("warningKey", key)
        })
    }

    while (SystemClock.elapsedRealtime() < deadline && store.read().vehicleRead) {
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val snapshot = VehicleObservationService.snapshots.value
        if (snapshot.active) {
            for (row in snapshot.readings) {
                if (!isSupportedWarningKey(row.key) || row.state != "수신" || row.value !in setOf("true", "false")) continue
                val observedAt = row.observedAt ?: continue
                val receivedElapsed = row.receivedElapsed ?: continue
                if (wallNow - observedAt !in -5_000..30_000 || elapsedNow - receivedElapsed !in 0..30_000) continue
                val receipt = WarningReceipt(observedAt, receivedElapsed)
                val previousReceipt = lastReceipt[row.key]
                if (previousReceipt != null && !receipt.follows(previousReceipt)) continue
                lastReceipt[row.key] = receipt

                val cycle = progress.getOrPut(row.key) { WarningCycleProgress() }
                cycle.differentReceipts++
                when {
                    cycle.offBeforeOn == null && row.value == "false" -> {
                        cycle.offBeforeOn = receipt
                        emitPhase("off_before_on", row.key)
                    }
                    cycle.offBeforeOn != null && cycle.onAfterOff == null && row.value == "true" &&
                        receipt.follows(cycle.offBeforeOn!!) -> {
                        cycle.onAfterOff = receipt
                        emitPhase("on_after_off", row.key)
                    }
                    cycle.onAfterOff != null && cycle.offAfterOn == null && row.value == "false" &&
                        receipt.follows(cycle.onAfterOff!!) -> {
                        cycle.offAfterOn = receipt
                        matchedKey = row.key
                        emitPhase("off_after_on", row.key)
                    }
                }
                val rank = when {
                    cycle.offAfterOn != null -> 3
                    cycle.onAfterOff != null -> 2
                    cycle.offBeforeOn != null -> 1
                    else -> 0
                }
                if (rank > bestProgressRank) {
                    bestProgress = cycle
                    bestProgressRank = rank
                }
                if (matchedKey != null) break
            }
        }
        if (matchedKey != null) break
        SystemClock.sleep(200)
    }

    val matchedProgress = matchedKey?.let(progress::get) ?: bestProgress
    val choicesUnchanged = before == store.read() && revisionBefore == store.revision()
    val phases = JSONObject()
        .put("offBeforeOn", matchedProgress?.offBeforeOn != null)
        .put("onAfterOff", matchedProgress?.onAfterOff != null)
        .put("offAfterOn", matchedProgress?.offAfterOn != null)
    val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
    val result = JSONObject()
        .put("source", "pleos_emulator_sdk_console_test")
        .put("staticMetadata", JSONObject().put("emulator", true).put("sdkInt", Build.VERSION.SDK_INT)
            .put("collector", "VehicleObservationService"))
        .put("matchedKey", matchedKey ?: JSONObject.NULL)
        .put("phaseBooleans", phases)
        .put("elapsedMs", elapsed)
        .put("differentReceipts", matchedProgress?.differentReceipts ?: 0)
        .put("choicesUnchanged", choicesUnchanged)
    val passed = matchedKey != null && matchedProgress?.differentReceipts?.let { it >= 3 } == true &&
        phases.optBoolean("offBeforeOn") && phases.optBoolean("onAfterOff") && phases.optBoolean("offAfterOn") &&
        choicesUnchanged && elapsed <= 90_000
    return result.put("result", if (passed) "passed" else "incomplete")
}
