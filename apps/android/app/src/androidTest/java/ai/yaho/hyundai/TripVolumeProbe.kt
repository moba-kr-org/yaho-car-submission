package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.vehicle.VehicleObservationService
import org.json.JSONArray
import org.json.JSONObject


internal fun Instrumentation.runTripVolumeProbe(): JSONObject {
    val store = AppConsentStore(targetContext)
    val before = store.read()
    val revision = store.revision()
    fun unchanged() = before == store.read() && revision == store.revision()
    val result = JSONObject().put("test", "trip_volume_60s_v1").put("rawValuesIncluded", false)
    if (!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("crp_avd")) || !before.reviewed || !before.vehicleRead)
        return result.put("status", "blocked")
    targetContext.startForegroundService(Intent(targetContext, VehicleObservationService::class.java))
    val warmDeadline = SystemClock.elapsedRealtime() + 40_000
    while (SystemClock.elapsedRealtime() < warmDeadline && unchanged()) {
        val frame = VehicleObservationService.snapshots.value
        if (frame.active && frame.readings.size >= 476 && frame.readings.none { it.state == "읽기 대기" }) break
        SystemClock.sleep(20)
    }
    val initial = VehicleObservationService.snapshots.value
    if (!initial.active || initial.readings.size < 476 || initial.readings.any { it.state == "읽기 대기" })
        return result.put("status", "warmup_incomplete")
    val last = initial.readings.associate { it.key to it.receivedElapsed }.toMutableMap()
    val counts = linkedMapOf<String, Int>()
    val start = SystemClock.elapsedRealtime()
    while (SystemClock.elapsedRealtime() - start < 60_000 && unchanged()) {
        VehicleObservationService.snapshots.value.readings.forEach { row ->
            val stamp = row.receivedElapsed
            if (row.state == "수신" && stamp != null && stamp != last[row.key]) {
                counts[row.key] = (counts[row.key] ?: 0) + 1
                last[row.key] = stamp
            }
        }
        SystemClock.sleep(20)
    }
    if (!unchanged()) return result.put("status", "consent_changed")
    return result.put("status", "completed").put("durationMs", SystemClock.elapsedRealtime() - start)
        .put("pollIntervalMs", 20).put("choicesUnchanged", true)
        .put("rows", JSONArray(counts.entries.sortedBy { it.key }.map { (key, count) ->
            JSONObject().put("key", key).put("receipts", count)
        })).put("totalReceipts", counts.values.sum())
}
