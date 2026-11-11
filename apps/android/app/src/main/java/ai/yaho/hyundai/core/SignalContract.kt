package ai.yaho.hyundai.core


enum class SignalMeaning(val label: String) {
    SETTING("설정·스위치 값"), REPORTED_POSITION("SDK 보고 위치"),
    REPORTED_QUANTITY("SDK 보고 수치"), REPORTED_STATE("SDK 보고 상태"),
    WARNING_FLAG("개별 경고 신호"), DERIVED("동일 응답 파생값"), UNKNOWN("의미 확인 전"),
}
data class SignalContract(val title: String, val meaning: SignalMeaning, val area: String?,
    val cadence: String, val confirmedUnit: String? = null)

private val knownSignalMethods: Map<String, Pair<String, SignalMeaning>> = mapOf(
    "Brake.getCurrentBrakePedalStatus" to ("브레이크 페달 상태" to SignalMeaning.REPORTED_STATE),
    "Display.getCurrentTemperatureDisplayUnit" to ("온도 표시 단위" to SignalMeaning.SETTING),
    "Door.getCurrentDoorPosition" to ("도어 개폐 위치" to SignalMeaning.REPORTED_POSITION),
    "Door.getCurrentGloveBoxLockState" to ("글로브박스 잠금 상태" to SignalMeaning.REPORTED_STATE),
    "Door.getDoorLockState" to ("도어 잠금 상태" to SignalMeaning.REPORTED_STATE),
    "DrivingMode.getCruiseControlState" to ("크루즈 컨트롤 상태" to SignalMeaning.REPORTED_STATE),
    "DrivingMode.getCruiseControlTargetSpeed" to ("크루즈 목표 속도" to SignalMeaning.SETTING),
    "EvBattery.getEvBatteryCapacity" to ("구동 배터리 용량" to SignalMeaning.REPORTED_QUANTITY),
    "EvBattery.getEvBatteryLevel" to ("구동 배터리 레벨" to SignalMeaning.REPORTED_QUANTITY),
    "EvBattery.getEvBatteryStateOfCharge" to ("구동 배터리 충전 상태" to SignalMeaning.REPORTED_QUANTITY),
    "EvBattery.getEvChargeCurrentDrawLimit" to ("충전 전류 제한" to SignalMeaning.SETTING),
    "EvBattery.getEvChargeSwitchState" to ("충전 스위치 상태" to SignalMeaning.SETTING),
    "EvBattery.isEvChargePortOpen" to ("충전구 열림 여부" to SignalMeaning.REPORTED_STATE),
    "Gear.getCurrentGearSelectionState" to ("선택 기어" to SignalMeaning.REPORTED_STATE),
    "HVAC.getHvacDefrosterState" to ("성에 제거 상태" to SignalMeaning.REPORTED_STATE),
    "HVAC.getHvacTemperature" to ("공조 설정 온도" to SignalMeaning.SETTING),
    "HVAC.getHvacTemperatureCurrent" to ("공조 현재 측정 온도" to SignalMeaning.REPORTED_QUANTITY),
    "Light.getCabinLightSwitch" to ("실내등 스위치" to SignalMeaning.SETTING),
    "Light.getCurrentCabinLightState" to ("실내등 실제 상태" to SignalMeaning.REPORTED_STATE),
    "Light.getCurrentHighBeamLightState" to ("상향등 실제 상태" to SignalMeaning.REPORTED_STATE),
    "Light.getCurrentLowBeamLightState" to ("하향등 실제 상태" to SignalMeaning.REPORTED_STATE),
    "Light.getCurrentReadingLightState" to ("독서등 실제 상태" to SignalMeaning.REPORTED_STATE),
    "Light.getCurrentTailLampLightState" to ("미등 실제 상태" to SignalMeaning.REPORTED_STATE),
    "Light.getHighBeamLightSwitch" to ("상향등 스위치" to SignalMeaning.SETTING),
    "Light.getLowBeamLightSwitch" to ("하향등 스위치" to SignalMeaning.SETTING),
    "Light.getReadingLightSwitch" to ("독서등 스위치" to SignalMeaning.SETTING),
    "Light.getTailLampLightSwitch" to ("미등 스위치" to SignalMeaning.SETTING),
    "Odometer.getCurrentDistanceDriven" to ("누적 주행거리" to SignalMeaning.REPORTED_QUANTITY),
    "Odometer.getRangeRemainingDistance" to ("남은 주행 가능 거리" to SignalMeaning.REPORTED_QUANTITY),
    "Safety.hasPowerDownWarningIndicator" to ("출력 저하 개별 경고 신호" to SignalMeaning.WARNING_FLAG),
    "Safety.hasSeatAirbagEnabled" to ("좌석 에어백 활성 여부" to SignalMeaning.REPORTED_STATE),
    "Seat.getCurrentSeatBackrestAnglePosition" to ("좌석 등받이 각도 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatBeltBuckledState" to ("안전벨트 체결 상태" to SignalMeaning.REPORTED_STATE),
    "Seat.getCurrentSeatBeltHeightPosition" to ("안전벨트 높이 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatDepthPosition" to ("좌석 깊이 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatForeAftPosition" to ("좌석 앞뒤 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatHeadrestAnglePosition" to ("헤드레스트 각도 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatHeadrestForeAftPosition" to ("헤드레스트 앞뒤 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatHeadrestHeightAftPosition" to ("헤드레스트 높이 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatHeightPosition" to ("좌석 높이 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatLumbarForeAftPosition" to ("허리받침 앞뒤 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatLumbarSideSupportPosition" to ("허리받침 측면 지지 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getCurrentSeatOccupancy" to ("좌석 탑승 감지 상태" to SignalMeaning.REPORTED_STATE),
    "Seat.getCurrentSeatTiltPosition" to ("좌석 기울기 위치" to SignalMeaning.REPORTED_POSITION),
    "Seat.getHvacSeatTemperature" to ("좌석 온도/열선 설정 값" to SignalMeaning.SETTING),
    "Seat.getHvacSeatVentilationState" to ("좌석 통풍 상태" to SignalMeaning.REPORTED_STATE),
    "SideMirror.getHvacSideMirrorHeatState" to ("사이드미러 열선 상태" to SignalMeaning.REPORTED_STATE),
    "SteeringWheel.getCurrentSteeringWheelAngle" to ("스티어링휠 각도" to SignalMeaning.REPORTED_QUANTITY),
    "SteeringWheel.getHvacSteeringWheelHeatState" to ("스티어링휠 열선 상태" to SignalMeaning.REPORTED_STATE),
    "TurnSignal.getCurrentTurnSignalState" to ("방향지시등 상태" to SignalMeaning.REPORTED_STATE),
    "Window.getCurrentWindowBlindPosition" to ("창문 블라인드 위치" to SignalMeaning.REPORTED_POSITION),
    "Window.getCurrentWindowLockState" to ("창문 잠금 상태" to SignalMeaning.REPORTED_STATE),
    "Window.getCurrentWindowPosition" to ("창문 개폐 위치" to SignalMeaning.REPORTED_POSITION),
    "Wiper.getCurrentWiperState" to ("와이퍼 실제 모드" to SignalMeaning.REPORTED_STATE),
    "Wiper.getCurrentWiperSwitch" to ("와이퍼 스위치 모드" to SignalMeaning.SETTING),
 )
private val signalAreaLabels = mapOf(
    "ROW_1_LEFT" to "앞좌석 왼쪽",
    "ROW_1_RIGHT" to "앞좌석 오른쪽",
    "ROW_2_LEFT" to "뒷좌석 왼쪽",
    "ROW_2_RIGHT" to "뒷좌석 오른쪽",
    "ROW_2_CENTER" to "뒷좌석 중앙",
    "HOOD" to "후드",
    "REAR" to "뒤 도어/트렁크",
    "FRONT_WINDSHIELD" to "앞 유리",
    "REAR_WINDSHIELD" to "뒤 유리",
    "DRIVER_LEFT" to "왼쪽 미러",
    "DRIVER_RIGHT" to "오른쪽 미러",
 )
fun signalContract(key: String): SignalContract {
    if (key == "wiper.front.active") return SignalContract("앞 와이퍼 ON/OFF", SignalMeaning.DERIVED,
        "앞 유리", "앞 와이퍼 응답 후 5초 대기")
    if (key == "wiper.front.state") return SignalContract("앞 와이퍼 실제 모드", SignalMeaning.REPORTED_STATE,
        "앞 유리", "앞 와이퍼 응답 후 5초 대기")
    val parts = key.split('.')
    val method = parts.take(2).joinToString(".")
    val definition = knownSignalMethods[method]
    val cadence = if (isSupportedWarningKey(key)) "경고 묶음 완료 후 5초 대기" else "전체 조회 완료 후 15초 대기"
    return SignalContract(definition?.first ?: method, definition?.second ?: SignalMeaning.UNKNOWN,
        parts.getOrNull(2)?.let { signalAreaLabels[it] ?: it }, cadence,
        if (method == "HVAC.getHvacTemperature") "CELSIUS" else null)
}

enum class ObservationFreshness(val label: String) {
    FRESH("최근 수신"), STALE("오래된 값"), STOPPED("관측 중지"),
    UNAVAILABLE("현재 값 미수신"), TIMESTAMP_INVALID("수신 시각 확인 필요"),
}

fun observationFreshness(state: String, hasValue: Boolean, collecting: Boolean,
    observedAt: Long?, receivedElapsed: Long?, now: Long, elapsed: Long): ObservationFreshness {
    if (!collecting) return ObservationFreshness.STOPPED
    if (state != "수신" || !hasValue) return ObservationFreshness.UNAVAILABLE
    if (observedAt == null || receivedElapsed == null || observedAt <= 0 || receivedElapsed < 0 ||
        observedAt > now || receivedElapsed > elapsed) return ObservationFreshness.TIMESTAMP_INVALID
    return if (now - observedAt <= 30_000 && elapsed - receivedElapsed <= 30_000)
        ObservationFreshness.FRESH else ObservationFreshness.STALE
}
