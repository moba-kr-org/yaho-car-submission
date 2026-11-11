package ai.yaho.hyundai.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

data class NavigationSdkStatus(
    val state: AdapterState = AdapterState.NOT_CONFIGURED,
    val message: String = "내비 서비스 연결 대기 · 위치 조회는 별도 동의 후 시작합니다.",
)


internal class NavigationContextAdapter(
    context: Context,
    private val onStatusChanged: (NavigationSdkStatus) -> Unit,
    private val canReadLocation: () -> Boolean = { false },
    private val onRouteChanged: (ai.yaho.hyundai.core.NavigationRouteObservation) -> Unit = {},
    private val canReadRoute: () -> Boolean = { false },
    private val canReadTurn: () -> Boolean = { false },
    private val onTurnChanged: (NavigationTurn?) -> Unit = {},
) {
    private val helper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var started = false
    private var registered = false
    private var connected = false
    private var routePending = false
    private var turnRequestDeadline = 0L
    private var turnRoutePending = false
    private val routeTimeout = Runnable {
        if(routePending && !released) { routePending=false;onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("timeout")) }
    }
    private var locationPending = false
    private val connectionTimeout = Runnable {
        if (!connected && !released) {
            onStatusChanged(NavigationSdkStatus(AdapterState.UNAVAILABLE, "내비 서비스 연결 응답을 받지 못했습니다. 다시 연결해 주세요."))
            release()
        }
    }
    private val locationTimeout = Runnable {
        if (locationPending && !released) {
            locationPending = false
            onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED, "내비 연결됨 · 위치 요청 응답 시간 초과 · 다시 요청할 수 있습니다."))
        }
    }
    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val receivedAt = SystemClock.elapsedRealtime()
            handler.post {
                if (released || turnRoutePending || !canReadTurn() || turnRequestDeadline == 0L || receivedAt > turnRequestDeadline) return@post
                turnRequestDeadline = 0L
                val next = tbtInfoList.firstOrNull()?.toNavigationTurn(receivedAt)
                onTurnChanged(next)
                android.util.Log.d("YahoNavigation", "turn_probe_response valid=${next != null} count=${tbtInfoList.size}")
            }
        }

        override fun onRouteCancelled() { handler.post { clearTurn() } }
        override fun onRouteEnded() { handler.post { clearTurn() } }
        override fun onDestinationChanged() { handler.post { clearTurn() } }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { handler.post { clearTurn() } }
        override fun onNavigationStatus(status: NavigationStatus) {
            if (status != NavigationStatus.ROUTE_DRIVE) handler.post { clearTurn() }
        }

        override fun onRouteStateInfo(info: ai.pleos.playground.navi.data.RouteStateInfo) {
            val mode=info.naviMode.name
            handler.post {
                if (released) return@post
                if (turnRoutePending && canReadTurn() && SystemClock.elapsedRealtime() <= turnRequestDeadline) {
                    turnRoutePending = false
                    if (mode == "ROUTE_GUIDING" || mode == "SIMULATION_GUIDING") {
                        try { helper.getTBTInfo() } catch (_: Exception) { clearTurn() }
                    } else {
                        clearTurn()
                        android.util.Log.d("YahoNavigation", "turn_probe_no_active_route")
                    }
                    return@post
                }
                if(released || !routePending || !canReadRoute()) return@post
                routePending=false;handler.removeCallbacks(routeTimeout)
                onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("received",mode,android.os.SystemClock.elapsedRealtime()))
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            val valid = info.latitude.isFinite() && info.latitude in -90.0..90.0 &&
                info.longitude.isFinite() && info.longitude in -180.0..180.0
            handler.post {
                if (released || !locationPending || !canReadLocation()) return@post
                locationPending = false
                handler.removeCallbacks(locationTimeout)
                onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED,
                    if (valid) "내비 연결됨 · 위치 콜백 수신 · 좌표·주소 미저장 · 서버 전송 없음 · 실차 정확도 미검증"
                    else "내비 연결됨 · 유효한 위치를 받지 못했습니다. 샘플 좌표로 대체하지 않습니다."))
            }
        }
        override fun onError(errorCode: NaviErrorCode) {
            handler.post {
                if(released) return@post
                if (turnRequestDeadline != 0L) clearTurn()
                if(routePending) {routePending=false;handler.removeCallbacks(routeTimeout);onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("error"))}
                if (!locationPending) return@post
                locationPending = false
                handler.removeCallbacks(locationTimeout)
                onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED, "내비 연결됨 · 위치 요청 오류 (${errorCode.name})"))
            }
        }
    }

    fun start() {
        if (started || released) return
        started = true
        onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTING, "내비 서비스에 연결 중 · 위치는 아직 요청하지 않았습니다."))
        handler.postDelayed(connectionTimeout, 12_000L)
        try {
            helper.initialize(onConnected = {
                handler.post {
                    if (released) return@post
                    try {
                        helper.addListener(listener)
                        registered = true
                        connected = true
                        handler.removeCallbacks(connectionTimeout)
                        onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED, "내비 서비스 연결됨 · 위치 조회는 별도 동의 후 시작합니다."))
                        if (ai.yaho.hyundai.BuildConfig.DEBUG) android.util.Log.i("YahoNavigation", "service_connected=true;location_requested=false")
                        if (canReadLocation()) requestLocationOnce()
                    } catch (_: Exception) { failConnection() }
                }
            }, onFailed = { handler.post { if (!released) failConnection() } })
        } catch (_: Exception) { failConnection() }
    }

    fun requestLocationOnce() {
        if (released || !connected || locationPending || routePending || !canReadLocation()) return
        locationPending = true
        onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED, "내비 연결됨 · 동의한 위치 1회 조회 중"))
        handler.postDelayed(locationTimeout, 12_000L)
        try { helper.getCurrentLocationInfo() }
        catch (_: Exception) {
            locationPending = false
            handler.removeCallbacks(locationTimeout)
            onStatusChanged(NavigationSdkStatus(AdapterState.CONNECTED, "내비 연결됨 · 위치 요청을 시작하지 못했습니다."))
        }
    }


    fun requestRouteStateOnce() {
        if(released || !connected || routePending || locationPending || !canReadRoute()) {
            onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("blocked"));return
        }
        routePending=true
        onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("pending"))
        handler.postDelayed(routeTimeout,12_000)
        try { helper.getRouteStateInfo() }
        catch (_: Exception) {routePending=false;handler.removeCallbacks(routeTimeout);onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation("error"))}
    }


    fun requestTurnOnce() {
        if (released || !connected || locationPending || routePending || !canReadTurn()) return
        turnRequestDeadline = SystemClock.elapsedRealtime() + 2_000
        turnRoutePending = true
        try {
            helper.getRouteStateInfo()
        } catch (_: Exception) {
            clearTurn()
        }
    }

    fun clearTurn() {
        turnRequestDeadline = 0
        turnRoutePending = false
        onTurnChanged(null)
    }

    private fun failConnection() {
        if (released) return
        onStatusChanged(NavigationSdkStatus(AdapterState.UNAVAILABLE, "내비 서비스에 연결하지 못했습니다. 다시 연결해 주세요."))
        release()
    }

    fun release() {
        if (released) return
        released = true
        connected = false
        locationPending = false
        routePending = false
        clearTurn()
        onRouteChanged(ai.yaho.hyundai.core.NavigationRouteObservation())
        handler.removeCallbacksAndMessages(null)
        try { if (registered) helper.removeListener(listener) }
        catch (_: Exception) { }
        finally { runCatching { helper.release() } }
    }
}
