package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.core.signalContract
import ai.yaho.hyundai.core.SignalMeaning
import ai.yaho.hyundai.core.observationFreshness
import ai.yaho.hyundai.vehicle.SignalReading
import ai.yaho.hyundai.vehicle.VehicleObservationService
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import org.json.JSONArray
import org.json.JSONObject

private const val COLLECTION_AUDIT_BASELINE_MINIMUM = 475
private const val COLLECTION_AUDIT_FRESHNESS_WINDOW_MS = 30_000L
private const val COLLECTION_AUDIT_WAIT_MS = 40_000L

private val collectionAuditNonErrorStates = setOf(
    "수신", "읽기 대기", "시간 초과", "값 미제공", "미지원", "미지원 기능", "미지원 부위",
    "값 형식 미지원", "권한 부족", "읽기 권한 없음",
)

private data class ReadingFreshness(
    val fresh: Boolean,
    val stale: Boolean,
    val missing: Boolean,
)

private fun SignalReading.auditFreshness(nowWallMs: Long, nowElapsedMs: Long): ReadingFreshness {
    val received = state == "수신"
    val wallAge = observedAt?.let { nowWallMs - it }
    val elapsedAge = receivedElapsed?.let { nowElapsedMs - it }
    val missing = observedAt == null || receivedElapsed == null
    val fresh = received && !missing && wallAge != null && elapsedAge != null &&
        wallAge in 0L..COLLECTION_AUDIT_FRESHNESS_WINDOW_MS &&
        elapsedAge in 0L..COLLECTION_AUDIT_FRESHNESS_WINDOW_MS
    val stale = received && !missing && !fresh
    return ReadingFreshness(fresh = fresh, stale = stale, missing = missing)
}

private fun safeAuditKey(key: String): String? = key.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,160}")) }

private fun safeAuditFailureType(type: String?): String? = type
    ?.substringAfterLast('.')
    ?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,120}")) }


internal fun Instrumentation.runCollectionAuditProbe(): JSONObject {
    val consentStore = AppConsentStore(targetContext)
    val consentBefore = consentStore.read()
    val revisionBefore = consentStore.revision()
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") ||
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("emulator", ignoreCase = true) ||
        Build.PRODUCT.startsWith("crp_avd", ignoreCase = true)

    fun consentUnchanged(): Boolean = consentBefore == consentStore.read() && revisionBefore == consentStore.revision()

    fun summarize(snapshot: VehicleSnapshot, statusOverride: String? = null, errorClass: String? = null): JSONObject {
        val readings = snapshot.readings.sortedBy { it.key }
        val stateCounts = readings.groupingBy { it.state }.eachCount()
        val waitingCount = readings.count { it.state == "읽기 대기" }
        val baselineReady = readings.size >= COLLECTION_AUDIT_BASELINE_MINIMUM && waitingCount == 0
        val choicesUnchanged = consentUnchanged()
        val currentConsentReady = consentStore.read().let { it.reviewed && it.vehicleRead }
        val rows = JSONArray()
        val nowWallMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()

        readings.forEach { reading ->
            val freshness = reading.auditFreshness(nowWallMs, nowElapsedMs)
            val row = JSONObject()
                .put("key", safeAuditKey(reading.key) ?: JSONObject.NULL)
                .put("state", reading.state)
                .put("fresh", freshness.fresh)
                .put("stale", freshness.stale)
                .put("freshnessMissing", freshness.missing)
                .put("meaning", signalContract(reading.key).meaning.name)
                .put("freshnessStatus", observationFreshness(reading.state, reading.value != null, snapshot.active,
                    reading.observedAt, reading.receivedElapsed, nowWallMs, nowElapsedMs).name)
            reading.unit?.takeIf { it in setOf("CELSIUS") }?.let { row.put("unit", it) }
            reading.failureCode?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) }
                ?.let { row.put("failureCode", it) }
            safeAuditFailureType(reading.failureType)?.let { row.put("failureType", it) }
            rows.put(row)
        }

        val counts = JSONObject()
            .put("received", stateCounts["수신"] ?: 0)
            .put("unsupported", readings.count { it.state in setOf("미지원", "미지원 기능", "값 형식 미지원") })
            .put("unsupportedArea", stateCounts["미지원 부위"] ?: 0)
            .put("permission", readings.count { it.state in setOf("권한 부족", "읽기 권한 없음") })
            .put("error", readings.count { it.state !in collectionAuditNonErrorStates })
            .put("valueMissing", stateCounts["값 미제공"] ?: 0)
            .put("timeout", stateCounts["시간 초과"] ?: 0)
            .put("waiting", waitingCount)

        val ready = baselineReady
        val status = statusOverride ?: if (ready && choicesUnchanged && currentConsentReady) "ready" else "incomplete"
        val result = JSONObject()
            .put("test", "collection_audit_v1")
            .put("source", "existing_pleos_collector_audit")
            .put("status", status)
            .put("emulator", emulator)
            .put("debugBuild", BuildConfig.DEBUG)
            .put("snapshotActive", snapshot.active)
            .put("ready", ready)
            .put("baselineMinimum", COLLECTION_AUDIT_BASELINE_MINIMUM)
            .put("thresholdIsMinimum", true)
            .put("readerCount", readings.size)
            .put("receivedMeaningUnknown", readings.count { it.state == "수신" && signalContract(it.key).meaning == SignalMeaning.UNKNOWN })
            .put("counts", counts)
            .put("stateCounts", JSONObject(stateCounts))
            .put("rows", rows)
            .put("choicesUnchanged", choicesUnchanged)
            .put("rawValuesIncluded", false)
            .put("timestampsIncluded", false)
        if (errorClass != null) result.put("errorClass", errorClass)
        return result
    }

    if (!consentBefore.reviewed || !consentBefore.vehicleRead) {
        return summarize(VehicleSnapshot(), statusOverride = "blocked").put("reason", "existing_vehicle_read_consent_required")
    }
    if (!BuildConfig.DEBUG) {
        return summarize(VehicleSnapshot(), statusOverride = "blocked").put("reason", "debug_build_required")
    }
    if (!emulator) {
        return summarize(VehicleSnapshot(), statusOverride = "blocked").put("reason", "emulator_required")
    }

    var startErrorClass: String? = null
    if (!VehicleObservationService.snapshots.value.active) {
        try {
            targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
        } catch (error: Exception) {
            startErrorClass = error.javaClass.simpleName
        }
    }

    val deadline = SystemClock.elapsedRealtime() + COLLECTION_AUDIT_WAIT_MS
    var snapshot = VehicleObservationService.snapshots.value
    while (SystemClock.elapsedRealtime() < deadline && startErrorClass == null) {
        val currentChoices = consentStore.read()
        if (!currentChoices.reviewed || !currentChoices.vehicleRead) break
        snapshot = VehicleObservationService.snapshots.value
        val waitingCount = snapshot.readings.count { it.state == "읽기 대기" }
        if (snapshot.readings.size >= COLLECTION_AUDIT_BASELINE_MINIMUM && waitingCount == 0) break
        SystemClock.sleep(100)
    }

    snapshot = VehicleObservationService.snapshots.value
    val choicesUnchanged = consentUnchanged()
    val currentConsentReady = consentStore.read().let { it.reviewed && it.vehicleRead }
    if (!currentConsentReady) {
        return summarize(VehicleSnapshot(), statusOverride = "incomplete")
            .put("snapshotActive", snapshot.active)
            .put("readerCount", JSONObject.NULL)
            .put("counts", JSONObject.NULL)
            .put("stateCounts", JSONObject.NULL)
            .put("rows", JSONArray())
            .put("ready", false)
            .put("choicesUnchanged", choicesUnchanged)
            .put("reason", "consent_withdrawn_during_audit")
    }
    val baselineReady = snapshot.readings.size >= COLLECTION_AUDIT_BASELINE_MINIMUM &&
        snapshot.readings.none { it.state == "읽기 대기" }
    val statusOverride = if (startErrorClass != null || !baselineReady || !choicesUnchanged || !currentConsentReady) {
        "incomplete"
    } else null
    return summarize(snapshot, statusOverride = statusOverride, errorClass = startErrorClass)
        .put("waitTimedOut", startErrorClass == null && !baselineReady && SystemClock.elapsedRealtime() >= deadline)
}
