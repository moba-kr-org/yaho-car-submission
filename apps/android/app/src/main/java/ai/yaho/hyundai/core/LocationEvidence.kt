package ai.yaho.hyundai.core


data class LocationEvidence(
    val latitude: Double, val longitude: Double, val observedAt: Long,
    val receivedElapsed: Long, val confidence: Float?, val heading: Float?,
    val emulator: Boolean,
)

fun validLocationEvidence(value: LocationEvidence?, now: Long, elapsed: Long): Boolean {
    if (value == null || !value.latitude.isFinite() || value.latitude !in -90.0..90.0 ||
        !value.longitude.isFinite() || value.longitude !in -180.0..180.0 ||
        (value.latitude == 0.0 && value.longitude == 0.0)) return false
    return value.observedAt > 0 && now-value.observedAt in -5000..30_000 &&
        elapsed-value.receivedElapsed in 0..30_000
}
fun locationMatchesObservation(value: LocationEvidence?, observationAt: Long, now: Long, elapsed: Long) =
    validLocationEvidence(value,now,elapsed) && kotlin.math.abs(value!!.observedAt-observationAt) <= 15_000
