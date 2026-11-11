package ai.yaho.hyundai.vehicle

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.yaho.hyundai.core.LocationEvidence
import ai.yaho.hyundai.core.validLocationEvidence

data class LiveLocationStatus(val state: String = "disabled", val fix: LocationEvidence? = null)


class FusedLocationAdapter(context: Context, private val allowed: () -> Boolean, private val changed: (LiveLocationStatus) -> Unit) {
    private val context = context.applicationContext
    private val sdk = FusedLocation(this.context)
    private val main = Handler(Looper.getMainLooper())
    private var released = false
    private var initialized = false
    private var registered = false
    private fun publish(value: LiveLocationStatus) { main.post { if (!released) changed(if(allowed()) value else LiveLocationStatus()) } }
    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (released || !allowed()) return
            if (location == null) { publish(LiveLocationStatus("no_fix")); return }
            val evidence = LocationEvidence(location.latitude,location.longitude,location.timestampMillis,
                android.os.SystemClock.elapsedRealtime(),location.confidence.takeIf { it.isFinite() },
                location.heading.takeIf { it.isFinite() && it in 0f..359.999f },Build.HARDWARE in setOf("ranchu","goldfish"))
            publish(if(validLocationEvidence(evidence,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime()))
                LiveLocationStatus("fresh",evidence) else LiveLocationStatus("stale_or_invalid"))
        }
        override fun onError(exception: Exception) { publish(LiveLocationStatus(if(exception is SecurityException) "permission_required" else "provider_error")) }
    }
    fun start() {
        if(released || initialized) return
        if(!allowed()) { changed(LiveLocationStatus()); return }
        if(context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            changed(LiveLocationStatus("permission_required")); return
        }
        try {
            if(!sdk.isSupported()) { changed(LiveLocationStatus("unsupported")); return }
            sdk.initialize();initialized=true
            publish(LiveLocationStatus("waiting"))
            sdk.registerFusedLocationCallback(listener);registered=true
            sdk.getLastKnownLocation(listener)
        } catch (_: Exception) { publish(LiveLocationStatus("provider_error")) }
    }
    fun release() {
        if(released) return
        released=true;main.removeCallbacksAndMessages(null)
        if(registered) runCatching { sdk.unregisterFusedLocationCallback(listener) }
        if(initialized) runCatching { sdk.release() }
        changed(LiveLocationStatus())
    }
}
