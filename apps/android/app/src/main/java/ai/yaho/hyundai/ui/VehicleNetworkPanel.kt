package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.NetworkLoadState
import ai.yaho.hyundai.core.networkDisplayState
import ai.yaho.hyundai.core.networkAccessState
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.data.BackendApi
import ai.yaho.hyundai.vehicle.VehicleContext
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.TimeoutCancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

internal data class VehicleNetworkUiState(
    val display: ai.yaho.hyundai.core.NetworkDisplayState,
    val interpretation: String, val rainDetails: String, val weatherDetails: String,
    val observationTime: String, val weatherTime: String, val message: String,
    val sharing: Boolean, val liveLocation: Boolean,
    val notice: String? = null,
)


@Composable
internal fun rememberVehicleNetworkState(
    backend: BackendApi, variant: ExperimentVariant, hasInstall: Boolean,
    snapshot: VehicleSnapshot, location: VehicleContext?,
): VehicleNetworkUiState {
    val context = LocalContext.current.applicationContext
    val store = remember { AppConsentStore(context) }
    val consents = rememberAppConsents()
    var loadState by remember(variant) { mutableStateOf(NetworkLoadState.WAITING) }
    var message by remember(variant) { mutableStateOf("설치 연결 후 중앙 네트워크를 이용할 수 있습니다.") }
    var participants by remember(variant) { mutableStateOf(0) }
    var interpretation by remember(variant) { mutableStateOf("새 관측을 기다리고 있습니다.") }
    var rainDetails by remember { mutableStateOf("유효한 와이퍼 관측을 기다리고 있습니다.") }
    var interpretationCode by remember(variant) { mutableStateOf("") }
    var crossCheckCode by remember(variant) { mutableStateOf("") }
    var weatherDetails by remember { mutableStateOf("공식 기상 대조: 아직 미확인") }
    var expiresAt by remember(variant) { mutableStateOf<Long?>(null) }
    var receivedElapsed by remember(variant) { mutableStateOf<Long?>(null) }
    var nowEpoch by remember { mutableStateOf(System.currentTimeMillis()) }
    var nowElapsed by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    var observationTime by remember(variant) { mutableStateOf("미확인") }
    var weatherTime by remember(variant) { mutableStateOf("미확인") }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowEpoch = System.currentTimeMillis()
            nowElapsed = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val fresh = ai.yaho.hyundai.core.isNetworkAssessmentFresh(expiresAt, nowEpoch, receivedElapsed, nowElapsed)
    val locationReady = location?.let { it.locationEvidence == null || ai.yaho.hyundai.core.validLocationEvidence(it.locationEvidence, nowEpoch, nowElapsed) } == true
    val access = networkAccessState(backend.isConfigured, hasInstall, consents.reviewed && consents.vehicleRead && consents.network, locationReady)
    val display = networkDisplayState(loadState, fresh, participants, access)
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestLocation by rememberUpdatedState(location)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(backend, lifecycle, variant, hasInstall, consents.network, consents.vehicleRead, consents.reviewed, consents.liveLocationNetwork) {
        receivedElapsed = null
        loadState = NetworkLoadState.WAITING
        if (!backend.isConfigured) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

            receivedElapsed = null
            loadState = NetworkLoadState.WAITING
            while (isActive) {
                try {
                    val desired = store.read().let { it.reviewed && it.vehicleRead && it.network }

                    for (target in ExperimentVariant.entries) {
                        if (store.withdrawalPending(target)) {
                            val revision = store.revision()
                            if (backend.setNetworkSharing(target,false).success) store.acknowledgeWithdrawal(target,revision)
                        }
                    }

                    if (!desired || !hasInstall) {
                        receivedElapsed = null
                        loadState = NetworkLoadState.WAITING
                        message = if (!desired) "신규 공유·위치 조회를 중지했습니다. 서버 철회는 연결 복구 후 다시 확인합니다." else "설치 연결을 기다립니다. 전송하지 않았습니다."
                        delay(1000)
                        continue
                    }
                    val candidate = latestLocation
                    val usableLocation = candidate?.takeIf { it.locationEvidence == null || ai.yaho.hyundai.core.validLocationEvidence(it.locationEvidence, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()) }
                    if (usableLocation == null) {
                        receivedElapsed = null; loadState = NetworkLoadState.WAITING
                        message = "신선한 위치와 OS 권한을 기다립니다. 샘플 위치로 대체하지 않습니다."
                        delay(1000); continue
                    }
                    val state = backend.networkState(variant)
                    val stillAllowed = { store.read().let { it.reviewed && it.vehicleRead && it.network && (usableLocation.locationEvidence == null || it.liveLocationNetwork) } }
                    val enabled = stillAllowed() && state.isSuccess && (state.getOrDefault(false) || backend.setNetworkSharing(variant, true).success)
                    if (enabled && stillAllowed()) {
                        message = backend.pushNetworkSnapshot(variant, latestSnapshot, usableLocation, canSend = stillAllowed).text
                    } else message = "서버의 공유 동의 확인을 기다리고 있습니다. 아직 전송하지 않았습니다."
                    if (store.read().let { it.reviewed && it.vehicleRead && it.network }) {
                        val response = backend.networkContext(variant, usableLocation, canQuery = {
                            store.read().let { it.reviewed && it.vehicleRead && it.network && (usableLocation.locationEvidence == null || it.liveLocationNetwork) }
                        })
                        if (response.isSuccess) {
                            val item = response.getOrNull()?.optJSONArray("items")?.optJSONObject(0)
                            loadState = if (item == null) NetworkLoadState.EMPTY else NetworkLoadState.RECEIVED
                            receivedElapsed = android.os.SystemClock.elapsedRealtime()
                            expiresAt = item?.optString("expiresAt")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                            observationTime = item?.optString("latestObservedAt")?.let(::networkTimeLabel) ?: "미확인"
                            weatherTime = item?.optJSONObject("weather")?.optString("observedAt")?.let(::networkTimeLabel) ?: "미확인"
                            participants = item?.optInt("participantCount") ?: 0
                            interpretationCode = item?.optString("interpretation").orEmpty()
                            crossCheckCode = item?.optString("weatherCrossCheck").orEmpty()
                            interpretation = when (item?.optString("interpretation")) {
                                "possible_precipitation" -> "지속 와이퍼 사용 비율 충족 · 강수 가능성"
                                "insufficient_evidence" -> "판단에 필요한 관측 수·최신성·비율이 부족합니다."
                                "no_collective_precipitation_signal" -> "현재 지속 와이퍼 사용 비율은 기준 미만입니다. 맑음을 뜻하지 않습니다."
                                else -> "유효한 주변 관측이 없습니다."
                            }
                            rainDetails = "와이퍼 유효 관측 ${item?.optInt("wiperKnownCount") ?: 0}곳 · 지속 작동 ${item?.optInt("sustainedWiperCount") ?: 0}곳 · 최소 10곳/80%/30초 기준"
                            weatherDetails = when (item?.optString("weatherCrossCheck")) {
                                "supports" -> "공식 기상 대조: 해당 기상 격자의 강수 실황도 있습니다. 차량 위치의 강수 확정은 아닙니다."
                                "conflicts" -> "공식 기상 대조: 차량 관측과 상충합니다. 추가 확인이 필요합니다."
                                "weather_only" -> "공식 기상정보 있음 · 차량 관측 판단은 별도입니다."
                                else -> "공식 기상 대조: 미연결·만료 또는 시험 채널 · 확인되지 않았습니다."
                            }
                        } else {
                            loadState = NetworkLoadState.OFFLINE
                            receivedElapsed = null
                            participants = 0
                            rainDetails = "유효한 관측을 확인하지 못했습니다."
                            weatherDetails = "공식 기상 대조: 연결 실패 · 최신 상태 미확인"
                            interpretation = "중앙 정보 연결 실패 · 이전 정보를 현재 상태로 표시하지 않습니다."
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    loadState = NetworkLoadState.TIMEOUT
                    receivedElapsed = null
                    participants = 0
                    rainDetails = "관측 집계 응답 지연"
                    weatherDetails = "공식 기상 대조: 응답 미확인"
                    message = "중앙 서버 응답 지연 · 다음 주기에 다시 확인합니다."
                    interpretation = "최신 중앙 정보를 확인하지 못했습니다."
                }
                delay(15_000)
            }
        }
    }

    return VehicleNetworkUiState(display, interpretation, rainDetails, weatherDetails, observationTime, weatherTime,
        if (access == ai.yaho.hyundai.core.NetworkAccessState.READY) message else display.status,
        consents.network, consents.liveLocationNetwork,
        ai.yaho.hyundai.core.rainNoticeText(display.showAssessment, interpretationCode, crossCheckCode, location?.isSample != false))
}

@Composable
internal fun VehicleNetworkPanel(state: VehicleNetworkUiState, guidance: RainGuidanceState) {
    val (display, interpretation, rainDetails, weatherDetails, observationTime, weatherTime, message, sharing, liveLocation) = state
    SettingsPanel(title = "주변 차량 네트워크") {
        SettingsStatusLine(if (display.showAssessment) interpretation else display.status, sharing)
        Text("참여 ${display.participantLabel} · 반경 약 3km · 최근 2분", style = MaterialTheme.typography.labelMedium)
        if (display.showAssessment) Text(rainDetails, style = MaterialTheme.typography.bodyMedium)
        SettingsFinePrint {
            if (display.showAssessment) {
                Text(weatherDetails, style = MaterialTheme.typography.bodySmall)
                Text("차량 관측 $observationTime · 기상 실황 $weatherTime", style = MaterialTheme.typography.bodySmall)
            } else Text("이전 집계는 만료됐거나 연결을 확인하는 중입니다. 공식 기상 대조는 최신 집계 수신 후 표시합니다.", style = MaterialTheme.typography.bodySmall)
            Text(if (liveLocation) "위치 SDK 모드 · 에뮬레이터는 시험 채널 · 정확도/실차 검증 전 안전 확정 안내가 아닙니다." else "현재 시험: 실제 SDK 값 + 샘플 위치의 시뮬레이션 채널입니다. 실제 도로 안내가 아닙니다.", style = MaterialTheme.typography.bodySmall)
            Text(if (sharing) "시작 안내의 네트워크 공유 동의를 사용합니다. 권한에서 해제할 수 있습니다." else "네트워크 공유가 꺼져 있습니다. 권한에서 켤 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            if (message != display.status || display.showAssessment) Text(message, style = MaterialTheme.typography.bodySmall)
            Text("300m 격자 중심 기준이며 실제 위치 정확도는 미확인입니다. 센서 위치 오차는 격자 경계 오차와 별개입니다. 설치 수는 검증된 독립 차량 수가 아니며, 강수·결빙·사고 확정이 아닙니다.", style = MaterialTheme.typography.bodySmall)
            Text("앱이 전면에서 실행 중일 때 약 15초마다 전송·조회합니다. 공유를 끄면 신규 전송을 멈추고 서버의 기여 기록을 철회합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    SettingsPanel(title = "안내 음성") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("주요 안내 자동 읽기", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("일반 안내는 5분에 한 번까지", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = guidance.automaticVoice, enabled = guidance.voiceReady || guidance.automaticVoice,
                onCheckedChange = guidance.onAutomaticVoice)
        }
        SettingsFinePrint {
            Text(guidance.voiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("기본값은 꺼짐입니다. 더 높은 우선순위는 현재 안내를 중단할 수 있습니다. 만료된 안내는 다시 읽지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun networkTimeLabel(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(java.time.ZoneId.of("Asia/Seoul"))
        .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")) + " KST"
}.getOrDefault("미확인")
