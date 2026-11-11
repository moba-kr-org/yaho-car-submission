package ai.yaho.hyundai.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.yaho.hyundai.core.projectWiperState
import android.content.Context
import android.os.Handler
import android.os.Looper


data class SignalReading(val key: String, val state: String, val value: String? = null, val observedAt: Long? = null, val failureCode: String? = null, val failureType: String? = null, val receivedElapsed: Long? = null, val unit: String? = null)
data class VehicleSnapshot(val readings: List<SignalReading> = emptyList(), val active: Boolean = false)
internal data class SnapshotTask(val key: String, val unit: String? = null, val read: ((Any?) -> Unit, (Exception) -> Unit) -> Unit)

class VehicleSnapshotCollector(context: Context, private val onUpdate: (VehicleSnapshot) -> Unit) {
    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var epoch = 0
    private var started = false
    private val readings = linkedMapOf<String, SignalReading>()
    private var tasks = emptyList<SnapshotTask>()
    private var warningTasks = emptyList<SnapshotTask>()
    private var cursor = 0
    private var active = 0

    fun start() {
        if (started) return
        started = true
        running = true
        try {
            vehicle.initialize()
            val allTasks = tasks()
            warningTasks = allTasks.filter { ai.yaho.hyundai.core.isSupportedWarningKey(it.key) }
            tasks = allTasks.filterNot { ai.yaho.hyundai.core.isSupportedWarningKey(it.key) }
            fastWarningSweep()
            fastWiperSweep()
            sweep()
        } catch (_: Exception) {
            onUpdate(VehicleSnapshot(listOf(SignalReading("Vehicle", "연결 실패")), false))
        }
    }

    private fun sweep() {
        if (!running) return
        epoch += 1
        cursor = 0
        active = 0
        tasks.forEach { task -> if (readings[task.key] == null) readings[task.key] = SignalReading(task.key, "읽기 대기", unit = task.unit) }
        emit()
        pump(epoch)
    }

    private fun pump(generation: Int) {
        if (!running || epoch != generation) return
        while (active < 4 && cursor < tasks.size) {
            val task = tasks[cursor++]
            active += 1
            var finished = false
            lateinit var timeout: Runnable
            fun complete(state: String, value: String? = null, error: Exception? = null) {
                handler.post {
                    if (!running || epoch != generation || finished) return@post
                    finished = true
                    handler.removeCallbacks(timeout)
                    readings[task.key] = SignalReading(task.key, state, value,
                        if (state == "수신") System.currentTimeMillis() else null,
                        error?.let(::snapshotErrorCode), error?.javaClass?.name,
                        if (state == "수신") android.os.SystemClock.elapsedRealtime() else null,
                        unit = task.unit)
                    active -= 1
                    emit()
                    pump(generation)
                }
            }
            timeout = Runnable { complete("시간 초과") }
            handler.postDelayed(timeout, 2_000)
            try {
                task.read({ value ->
                    val scalar = when(value) {
                        is Boolean -> value.toString()
                        is Number -> value.toDouble().takeIf { it.isFinite() }?.let { value.toString() }
                        is Enum<*> -> value.name
                        is String -> value.take(160)
                        else -> null
                    }
                    complete(if (scalar != null) "수신" else "값 미제공", scalar)
                }, { error -> complete(snapshotErrorState(error), error = error) })
            } catch (error: Exception) { complete(snapshotErrorState(error), error = error) }
        }
        if (cursor == tasks.size && active == 0) {
            if (ai.yaho.hyundai.BuildConfig.DEBUG) {
                android.util.Log.i("YahoSnapshot", "readers=${readings.size};received=${readings.values.count { it.state == "수신" }};source=pleos-sdk;upload=false")
                val summary = org.json.JSONObject().put("completedAt", System.currentTimeMillis())
                    .put("states", org.json.JSONObject(readings.values.groupingBy { it.state }.eachCount()))
                android.util.Log.i("YahoSnapshotAudit", summary.toString())
                readings.values.groupBy { it.key.substringBefore('.') }.forEach { (domain, rows) ->
                    android.util.Log.i("YahoSnapshotAudit", org.json.JSONObject().put("domain", domain)
                        .put("states", org.json.JSONObject(rows.groupingBy { it.state }.eachCount())).toString())
                }
            }
            handler.postDelayed({ if (running && epoch == generation) sweep() }, 15_000)
        }
    }

    private fun emit() = onUpdate(VehicleSnapshot(readings.values.toList(), running))


    private fun fastWiperSweep() {
        if (!running) return
        var done = false
        val activeKey = "wiper.front.active"
        val stateKey = "wiper.front.state"
        fun finish(state: String, projection: ai.yaho.hyundai.core.WiperStateProjection? = null, error: Exception? = null) { handler.post {
            if (!running || done) return@post
            done = true
            val callbackReceived = projection != null
            val observedAt = if (callbackReceived) System.currentTimeMillis() else null
            val receivedElapsed = if (callbackReceived) android.os.SystemClock.elapsedRealtime() else null
            val activeState = when {
                projection == null -> state
                projection.active != null -> "수신"
                else -> "값 미제공"
            }
            val enumState = if (projection?.enumName != null) "수신" else if (projection != null) "값 미제공" else state
            readings[activeKey] = SignalReading(activeKey, activeState, projection?.active?.toString(), observedAt,
                error?.let(::snapshotErrorCode), error?.javaClass?.name, receivedElapsed)
            readings[stateKey] = SignalReading(stateKey, enumState, projection?.enumName, observedAt,
                error?.let(::snapshotErrorCode), error?.javaClass?.name, receivedElapsed)
            emit()
            handler.postDelayed({ fastWiperSweep() }, 5_000)
        } }
        handler.postDelayed({ finish("시간 초과") }, 2_000)
        try {
            vehicle.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD),
                onComplete = { value ->
                    val projection = projectWiperState(value?.name)
                    finish(if (projection.active != null) "수신" else "값 미제공", projection = projection)
                }, onFailed = { error -> finish(snapshotErrorState(error), error = error) })
        } catch (error: Exception) { finish(snapshotErrorState(error), error = error) }
    }


    private fun fastWarningSweep() {
        if (!running || warningTasks.isEmpty()) return
        var next = 0
        var pending = 0
        fun pumpWarnings() {
            if (!running) return
            while (pending < 4 && next < warningTasks.size) {
                val task = warningTasks[next++]
                pending++
                var finished = false
                lateinit var timeout: Runnable
                fun finish(state: String, value: String? = null, error: Exception? = null) {
                    handler.post {
                        if (!running || finished) return@post
                        finished = true
                        handler.removeCallbacks(timeout)
                        readings[task.key] = SignalReading(task.key, state, value,
                            if (state == "수신") System.currentTimeMillis() else null,
                            error?.let(::snapshotErrorCode), error?.javaClass?.name,
                            if (state == "수신") android.os.SystemClock.elapsedRealtime() else null,
                            unit = task.unit)
                        pending--
                        emit()
                        pumpWarnings()
                    }
                }
                timeout = Runnable { finish("시간 초과") }
                handler.postDelayed(timeout, 2_000)
                try {
                    task.read({ value ->
                        if (value is Boolean) finish("수신", value.toString()) else finish("값 미제공")
                    }, { error -> finish(snapshotErrorState(error), error = error) })
                } catch (error: Exception) { finish(snapshotErrorState(error), error = error) }
            }
            if (next == warningTasks.size && pending == 0) handler.postDelayed({ fastWarningSweep() }, 5_000)
        }
        pumpWarnings()
    }

    fun release() {
        running = false
        epoch += 1
        handler.removeCallbacksAndMessages(null)
        runCatching { vehicle.release() }
        readings.clear()
        onUpdate(VehicleSnapshot())
    }

    private fun tasks(): List<SnapshotTask> = expandedSnapshotTasks(vehicle)
}
