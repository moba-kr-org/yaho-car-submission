package ai.yaho.hyundai.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.yaho.hyundai.BuildConfig
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperSwitch

data class VehicleControlStatus(
    val connected: Boolean = false,
    val windowAccess: String? = null,
    val windowMinimum: Int? = null,
    val windowMaximum: Int? = null,
    val windowPosition: Int? = null,
    val windowWritable: Boolean = false,
    val wiperAccess: String? = null,
    val wiperSwitch: String? = null,
    val wiperOptions: List<String> = emptyList(),
    val wiperWritable: Boolean = false,
    val busy: Boolean = false,
    val currentTargetObserved: Boolean = false,
    val failureCode: String? = null,
    val failureType: String? = null,
    val outcome: String = "idle",
    val message: String = "차량 제어 지원 확인 중",
)


class VehicleControlAdapter(
    context: Context,
    private val canActuate: () -> Boolean,
    private val onStatusChanged: (VehicleControlStatus) -> Unit,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val windowArea = WindowArea(WindowValue.ROW_1_LEFT)
    private val wiperArea = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var started = false
    private var released = false
    private var commandId = 0L
    private var receiptConfirmed = false
    private var readBack: (((Boolean, Int?, String?) -> Unit) -> Unit)? = null
    private var commandLabel = ""
    private var commandStarted = 0L
    private var readSequence = 0L
    var status = VehicleControlStatus()
        private set

    private fun publish(update: (VehicleControlStatus) -> VehicleControlStatus) {
        main.post {
            if (!released) {
                status = update(status)
                onStatusChanged(status)
            }
        }
    }

    fun start() {
        if (started || released) return
        started = true
        try {
            vehicle.initialize()
            publish { it.copy(connected = true) }
            discoverWindow()
            discoverWiper()
            main.postDelayed({
                if (!released && (status.windowAccess == null || status.wiperAccess == null)) {
                    publish { it.copy(message = "일부 제어 지원 응답이 없습니다. 확인된 기능만 사용할 수 있습니다") }
                }
            }, 5000L)
        } catch (_: Exception) {
            publish { it.copy(connected = false, message = "차량 제어 서비스 연결 실패") }
        }
    }

    private fun discoverWindow() {
        try {
            vehicle.getWindow().checkWindowPositionCapabilities(
                onComplete = { config, areas ->
                    val bounds = areas[windowArea]
                    val minimum = bounds?.minValue
                    val maximum = bounds?.maxValue

                    val supported = config.accessType == AccessType.READ_WRITE && minimum == 0 && maximum != null && maximum > 0
                    if (BuildConfig.DEBUG) Log.d("YahoVehicleControl", "window access=${config.accessType} row1left=$minimum..$maximum writable=$supported")
                    publish { it.copy(windowAccess = config.accessType.name, windowMinimum = minimum, windowMaximum = maximum,
                        windowWritable = supported, message = "차량별 지원 기능만 사용할 수 있습니다") }
                    if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                        if (bounds != null && !released) vehicle.getWindow().getCurrentWindowPosition(windowArea,
                            onComplete = { value -> publish { it.copy(windowPosition = value) } }, onFailed = { })
                    }
                },
                onFailed = { publish { it.copy(windowWritable = false, message = "창문 제어 권한 또는 지원 정보를 확인하지 못했습니다") } },
            )
        } catch (_: Exception) {
            publish { it.copy(windowWritable = false, message = "창문 제어를 사용할 수 없습니다") }
        }
    }

    private fun discoverWiper() {
        try {
            vehicle.getWiper().checkWiperSwitchCapabilities(
                onComplete = { config, areas ->
                    val choices = areas[wiperArea].orEmpty().filter { it !in setOf(WiperSwitch.OTHER, WiperSwitch.SERVICE) }
                    if (BuildConfig.DEBUG) Log.d("YahoVehicleControl", "wiper access=${config.accessType} front=${choices.joinToString { it.name }}")
                    publish { it.copy(wiperAccess = config.accessType.name, wiperOptions = choices.map { value -> value.name },
                        wiperWritable = config.accessType == AccessType.READ_WRITE && choices.isNotEmpty()) }
                    if (wiperArea in areas && !released && config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE)) {
                        vehicle.getWiper().getCurrentWiperSwitch(wiperArea,
                            onComplete = { value -> publish { it.copy(wiperSwitch = value?.name) } }, onFailed = { })
                    }
                },
                onFailed = { publish { it.copy(wiperWritable = false, message = "와이퍼 제어 권한 또는 지원 정보를 확인하지 못했습니다") } },
            )
        } catch (_: Exception) {
            publish { it.copy(wiperWritable = false, message = "와이퍼 제어를 사용할 수 없습니다") }
        }
    }


    fun setWindowPosition(target: Int) = main.post {
        if (released || !started || status.busy || status.outcome == "unknown" || !status.windowWritable || !canActuate()) return@post
        val minimum = status.windowMinimum ?: return@post
        val maximum = status.windowMaximum ?: return@post
        if (target !in minimum..maximum) return@post
        val id = beginCommand("운전석 창문 변경 요청 중", "창문") { complete ->
            vehicle.getWindow().getCurrentWindowPosition(windowArea,
                onComplete = { value -> complete(value == target, value, null) }, onFailed = { complete(false, null, null) })
        }
        try {
            vehicle.getWindow().setWindowPosition(windowArea, target,
                onComplete = { _, _ -> main.post { acknowledge(id) } },
                onFailed = { error -> main.post { recordFailure(id, error) } })
        } catch (_: Exception) { unknown(id) }
    }


    fun setWiperSwitch(targetName: String) = main.post {
        if (released || !started || status.busy || status.outcome == "unknown" || !status.wiperWritable || targetName !in status.wiperOptions || !canActuate()) return@post
        val target = WiperSwitch.entries.firstOrNull { it.name == targetName } ?: return@post
        val id = beginCommand("앞 와이퍼 변경 요청 중", "와이퍼") { complete ->
            vehicle.getWiper().getCurrentWiperSwitch(wiperArea,
                onComplete = { value -> complete(value == target, null, value?.name) }, onFailed = { complete(false, null, null) })
        }
        try {
            vehicle.getWiper().setWiperSwitch(wiperArea, target,
                onComplete = { _, _ -> main.post { acknowledge(id) } },
                onFailed = { error -> main.post { recordFailure(id, error) } })
        } catch (_: Exception) { unknown(id) }
    }

    private fun beginCommand(message: String, label: String, read: ((Boolean, Int?, String?) -> Unit) -> Unit): Long {
        val id = ++commandId
        receiptConfirmed = false
        readBack = read
        commandLabel = label
        commandStarted = android.os.SystemClock.elapsedRealtime()
        status = status.copy(busy = true, outcome = "pending", currentTargetObserved = false, message = message)
        onStatusChanged(status)
        main.postDelayed({ if (!released && commandId == id && !receiptConfirmed) unknown(id) }, 5000L)
        main.postDelayed({ reconcile(id) }, 500L)
        return id
    }

    private fun acknowledge(id: Long) {
        if (released || commandId != id) return
        receiptConfirmed = true
        if (status.currentTargetObserved) {
            status = status.copy(busy = false, outcome = "verified", message = "$commandLabel 응답과 현재 상태를 확인했습니다")
            onStatusChanged(status)
        }
    }


    private fun reconcile(id: Long) {
        if (released || commandId != id || status.outcome == "verified") return
        if (!canActuate() || android.os.SystemClock.elapsedRealtime() - commandStarted >= 30_000L) {
            unknown(id); return
        }

        if (!receiptConfirmed && android.os.SystemClock.elapsedRealtime() - commandStarted < 5_000L) {
            main.postDelayed({ reconcile(id) }, 500L); return
        }
        val sequence = ++readSequence
        var settled = false
        fun finish(matches: Boolean, position: Int?, wiper: String?) { main.post {
            if (settled || released || commandId != id || sequence != readSequence) return@post
            settled = true
            status = status.copy(windowPosition = position ?: status.windowPosition,
                wiperSwitch = wiper ?: status.wiperSwitch, currentTargetObserved = matches)
            if (matches && receiptConfirmed) {
                status = status.copy(busy = false, outcome = "verified", message = "$commandLabel 응답과 현재 상태를 확인했습니다")
            } else if (matches && status.outcome == "unknown") {
                status = status.copy(message = "현재 $commandLabel 상태는 요청과 같습니다. 실행 응답은 미확인으로 남기며 재실행하지 않습니다")
            }
            onStatusChanged(status)
            if (status.outcome != "verified") main.postDelayed({ reconcile(id) }, 1000L)
        } }
        main.postDelayed({ finish(false, null, null) }, 1500L)
        try { readBack?.invoke(::finish) } catch (_: Exception) { finish(false, null, null) }
    }

    private fun recordFailure(id: Long, error: Exception) {
        if (released || commandId != id) return
        status = status.copy(failureCode = snapshotErrorCode(error), failureType = error.javaClass.name)
        unknown(id)
    }

    private fun unknown(id: Long) {
        if (released || commandId != id || status.outcome == "verified") return
        status = status.copy(busy = false, outcome = "unknown", message = if (status.currentTargetObserved)
            "현재 $commandLabel 상태는 요청과 같습니다. 실행 응답은 미확인으로 남기며 재실행하지 않습니다"
            else "변경 결과를 확인하지 못했습니다. 상태만 다시 확인하며 자동 재실행하지 않습니다")
        onStatusChanged(status)
    }

    fun stop() {
        if (released) return
        if (status.busy) {
            status = status.copy(busy = false, outcome = "unknown", message = "화면 전환으로 결과 확인이 중단됐습니다. 자동 재실행하지 않습니다")
            onStatusChanged(status)
        }
        released = true
        ++commandId
        main.removeCallbacksAndMessages(null)
        try { vehicle.release() } catch (_: Exception) { }
    }
}
