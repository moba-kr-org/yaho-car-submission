package ai.yaho.hyundai.vehicle

import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.yaho.hyundai.core.DrivingUxState


class DrivingUxMonitor(private val context: Context, private val onChanged: (DrivingUxState) -> Unit, private val onSpeed: (Float?) -> Unit, private val readSpeed: Boolean = true) {
    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var released = false
    private var properties: CarPropertyManager? = null
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (released || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            val speed = (value.value as? Number)?.toFloat()?.takeIf { it.isFinite() && value.status == 0 }?.let { kotlin.math.abs(it) * 3.6f }
            handler.post { if (!released) onSpeed(speed) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { handler.post { if (!released) onSpeed(null) } }
    }
    private fun publish(value: CarUxRestrictions) {
        if (released) return
        val state = DrivingUxState(true, value.isRequiresDistractionOptimization, value.activeRestrictions)
        onChanged(state)
        if (ai.yaho.hyundai.BuildConfig.DEBUG) android.util.Log.i("YahoDrivingUx", "known=true;requiresDO=${state.requiresOptimization};flags=${state.flags}")
    }
    fun start() {
        if (android.os.Build.VERSION.SDK_INT < 29) { onChanged(DrivingUxState()); return }
        try {
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                handler.post {
                    if (released) return@post
                    if (!ready) { onChanged(DrivingUxState()); return@post }
                    try {
                        try { if (readSpeed) {
                            properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                            val value = properties?.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
                            value?.let { speedCallback.onChangeEvent(it) }
                            properties?.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
                        }
                        } catch (_: Exception) { onSpeed(null) }
                        manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        manager?.registerListener { publish(it) }
                        manager?.currentCarUxRestrictions?.let(::publish)
                    } catch (_: Exception) { onChanged(DrivingUxState()) }
                }
            }
        } catch (_: Exception) { onChanged(DrivingUxState()) }
        catch (_: LinkageError) { onChanged(DrivingUxState()) }
    }
    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        runCatching { properties?.unregisterCallback(speedCallback) }
        onSpeed(null)
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        onChanged(DrivingUxState())
    }
}
