package ai.yaho.hyundai.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.content.Context
import ai.yaho.hyundai.core.DrivingMode

data class VehicleContext(
    val mode: DrivingMode,
    val speedKph: Int?,
    val batteryPercent: Int?,
    val areaLabel: String?,
    val latitude: Double?,
    val longitude: Double?,
    val headingDegrees: Int?,
    val sourceLabel: String,
    val isSample: Boolean,
    val locationEvidence: ai.yaho.hyundai.core.LocationEvidence? = null,
)

enum class AdapterState {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE,
}

data class VehicleSdkStatus(
    val state: AdapterState = AdapterState.NOT_CONFIGURED,
    val doorPosition: Int? = null,
    val wiper: String = "읽기 대기",
    val brake: String = "읽기 대기",
    val gear: String? = null,
    val minimumDoorPosition: Int? = null,
    val maximumDoorPosition: Int? = null,
    val accessType: String? = null,
    val error: String? = null,
)

interface VehicleContextAdapter {
    val state: AdapterState
    val displayName: String
    fun readContext(): VehicleContext?
}


class PleosVehicleContextAdapter(
    context: Context,
    private val onStatusChanged: (VehicleSdkStatus) -> Unit,
) : VehicleContextAdapter {
    private val vehicle = Vehicle(context.applicationContext)
    private val doorArea = DoorArea(DoorValue.ROW_1_LEFT)
    private var started = false
    private var released = false
    private var listenerRegistered = false

    var status: VehicleSdkStatus = VehicleSdkStatus(state = AdapterState.CONNECTING)
        private set

    override val state: AdapterState
        get() = status.state
    override val displayName = "Pleos Vehicle SDK"
    override fun readContext(): VehicleContext? = null

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            publish(status.copy(state = AdapterState.CONNECTED, doorPosition = pos, error = null))
        }

        override fun onFailed(exception: Exception) {
            publish(status.copy(state = AdapterState.UNAVAILABLE, error = "차량 서비스에서 차문 상태를 받지 못했습니다."))
        }
    }

    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var wiperRegistered = false
    private var brakeRegistered = false
    private var gearRegistered = false
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            if (!released) publish(status.copy(gear = value?.name))
        }
        override fun onFailed(exception: Exception) {
            if (!released) publish(status.copy(gear = null))
        }
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) {
            if (!released) publish(status.copy(wiper = value?.name ?: "값 미제공"))
        }
        override fun onFailed(exception: Exception) {
            if (!released) publish(status.copy(wiper = "사용 불가"))
        }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            if (!released) publish(status.copy(brake = brakeLabel(value)))
        }
        override fun onFailed(exception: Exception) {
            if (!released) publish(status.copy(brake = "사용 불가"))
        }
    }
    private fun brakeLabel(value: Boolean?) = when(value) {
        true -> "밟음"
        false -> "해제"
        null -> "값 미제공"
    }
    private fun startRoadSignals() {
        try {
            vehicle.getGear().getCurrentGearSelectionState(
                onComplete = { value -> if (!released) publish(status.copy(gear = value?.name)) },
                onFailed = { if (!released) publish(status.copy(gear = null)) })
            vehicle.getGear().registerGearSelection(gearListener)
            gearRegistered = true
        } catch (_: Exception) { publish(status.copy(gear = null)) }

        try {
            vehicle.getWiper().checkWiperStateCapabilities(
                onComplete = { config, areas ->
                    if (!released) {
                        if (config.accessType.toString() !in setOf("READ", "READ_WRITE") || !areas.containsKey(windshield)) {
                            publish(status.copy(wiper = "읽기 미지원"))
                        } else {
                            vehicle.getWiper().getCurrentWiperState(windshield,
                                onComplete = { value -> if (!released) publish(status.copy(wiper = value?.name ?: "값 미제공")) },
                                onFailed = { if (!released) publish(status.copy(wiper = "읽기 실패")) })
                            vehicle.getWiper().registerWiperState(windshield, wiperListener)
                            wiperRegistered = true
                        }
                    }
                }, onFailed = { if (!released) publish(status.copy(wiper = "사용 불가")) })
        } catch (_: Exception) { publish(status.copy(wiper = "사용 불가")) }
        try {
            vehicle.getBrake().checkBrakePedalStatusCapabilities(
                onComplete = { config ->
                    if (!released) {
                        if (config.accessType.toString() !in setOf("READ", "READ_WRITE")) {
                            publish(status.copy(brake = "읽기 미지원"))
                        } else {
                            vehicle.getBrake().getCurrentBrakePedalStatus(
                                onComplete = { value -> if (!released) publish(status.copy(brake = brakeLabel(value))) },
                                onFailed = { if (!released) publish(status.copy(brake = "읽기 실패")) })
                            vehicle.getBrake().registerBrakePedalStatus(brakeListener)
                            brakeRegistered = true
                        }
                    }
                }, onFailed = { if (!released) publish(status.copy(brake = "사용 불가")) })
        } catch (_: Exception) { publish(status.copy(brake = "사용 불가")) }
    }

    fun start() {
        if (started || released) return
        started = true
        publish(VehicleSdkStatus(state = AdapterState.CONNECTING))
        try {
            vehicle.initialize()
            startRoadSignals()
            vehicle.getDoor().checkDoorPositionCapabilities(
                onComplete = { config, areaBounds ->
                    if (released) return@checkDoorPositionCapabilities
                    val accessType = config.accessType.toString()
                    val canRead = accessType == "READ" || accessType == "READ_WRITE"
                    if (!canRead) {
                        publish(VehicleSdkStatus(state = AdapterState.UNAVAILABLE, accessType = accessType, error = "차량 SDK에서 차문 읽기를 허용하지 않았습니다."))
                        return@checkDoorPositionCapabilities
                    }

                    val bounds = areaBounds[doorArea]
                    val baseStatus = VehicleSdkStatus(
                        state = AdapterState.CONNECTING,
                        minimumDoorPosition = bounds?.minValue,
                        maximumDoorPosition = bounds?.maxValue,
                        accessType = accessType,
                    )
                    publish(status.copy(state = baseStatus.state, accessType = baseStatus.accessType, minimumDoorPosition = baseStatus.minimumDoorPosition, maximumDoorPosition = baseStatus.maximumDoorPosition))
                    val door = vehicle.getDoor()
                    door.getCurrentDoorPosition(
                        area = doorArea,
                        onComplete = { position ->
                            if (released) return@getCurrentDoorPosition
                            publish(status.copy(state = AdapterState.CONNECTED, doorPosition = position, accessType = baseStatus.accessType, minimumDoorPosition = baseStatus.minimumDoorPosition, maximumDoorPosition = baseStatus.maximumDoorPosition))
                            try {
                                door.registerDoorPosition(doorArea, doorListener)
                                listenerRegistered = true
                            } catch (_: Exception) {
                                publish(status.copy(error = "차문 위치 실시간 갱신을 시작하지 못했습니다."))
                            }
                        },
                        onFailed = {
                            if (!released) publish(baseStatus.copy(state = AdapterState.UNAVAILABLE, error = "차량 서비스에서 차문 위치를 읽지 못했습니다."))
                        },
                    )
                },
                onFailed = {
                    if (!released) publish(VehicleSdkStatus(state = AdapterState.UNAVAILABLE, error = "차량 서비스에서 차문 기능을 사용할 수 없습니다."))
                },
            )
        } catch (_: Exception) {
            publish(VehicleSdkStatus(state = AdapterState.UNAVAILABLE, error = "Pleos Vehicle SDK에 연결할 수 없습니다."))
        }
    }

    fun release() {
        if (released) return
        released = true
        try { if (gearRegistered) vehicle.getGear().unregisterGearSelection(gearListener) } catch (_: Exception) { }
        try { if (wiperRegistered) vehicle.getWiper().unregisterWiperState(windshield, wiperListener) } catch (_: Exception) { }
        try { if (brakeRegistered) vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) } catch (_: Exception) { }
        try {
            if (listenerRegistered) vehicle.getDoor().unregisterDoorPosition(doorArea, doorListener)
        } catch (_: Exception) {

        }
        try {
            vehicle.release()
        } catch (_: Exception) {

        }
    }

    private fun publish(next: VehicleSdkStatus) {
        if (released) return
        if (ai.yaho.hyundai.BuildConfig.DEBUG && (next.wiper != status.wiper || next.brake != status.brake || next.gear != status.gear)) {
            android.util.Log.i("YahoRoadSignals", "wiper=${next.wiper};brake=${next.brake};gear=${next.gear};source=pleos-sdk;upload=false")
        }
        status = next
        onStatusChanged(next)
    }
}


class SimulatorVehicleContextAdapter(
    private val mode: DrivingMode,
) : VehicleContextAdapter {
    override val state = AdapterState.CONNECTED
    override val displayName = "로컬 시뮬레이터 · 샘플"

    override fun readContext() = VehicleContext(
        mode = mode,
        speedKph = if (mode == DrivingMode.MOVING) 36 else 0,
        batteryPercent = 78,
        areaLabel = "서울 성수동 · 샘플 위치",
        latitude = 37.5446,
        longitude = 127.0557,
        headingDegrees = 80,
        sourceLabel = "시뮬레이터 샘플",
        isSample = true,
    )
}
