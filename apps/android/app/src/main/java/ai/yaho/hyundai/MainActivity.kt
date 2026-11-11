package ai.yaho.hyundai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import ai.yaho.hyundai.core.modeFromGear
import ai.yaho.hyundai.core.DrivingUxState
import ai.yaho.hyundai.vehicle.DrivingUxMonitor
import ai.yaho.hyundai.core.DrivingMode
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.GleoActionParser
import ai.yaho.hyundai.core.GleoCommand
import ai.yaho.hyundai.core.GleoDebugDiagnostics
import ai.yaho.hyundai.core.GleoDebugTraceToken
import ai.yaho.hyundai.core.GleoIntentParameterNormalization
import ai.yaho.hyundai.core.GleoIntentParameterPolicy
import ai.yaho.hyundai.core.GleoObservationClock
import ai.yaho.hyundai.core.GleoObservationFreshnessPolicy
import ai.yaho.hyundai.core.GleoPendingReviewDecision
import ai.yaho.hyundai.core.GleoPendingReviewPolicy
import ai.yaho.hyundai.core.GleoParseResult
import ai.yaho.hyundai.data.BackendApi
import ai.yaho.hyundai.data.ConsentStore
import ai.yaho.hyundai.data.DebugInstallationCredentialStore
import ai.yaho.hyundai.data.InstallationCredentialProvider
import ai.yaho.hyundai.data.IdempotencyKeyStore
import ai.yaho.hyundai.data.ReleaseCredentialProvider
import ai.yaho.hyundai.data.SimulatorStateStore
import ai.yaho.hyundai.ui.YahoHyundaiApp
import ai.yaho.hyundai.ui.YahoHyundaiTheme
import ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter
import ai.yaho.hyundai.vehicle.PleosVehicleContextAdapter
import ai.yaho.hyundai.vehicle.NavigationContextAdapter
import ai.yaho.hyundai.vehicle.NavigationSdkStatus
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import ai.yaho.hyundai.vehicle.VehicleSnapshotCollector
import ai.yaho.hyundai.vehicle.VehicleSdkStatus
import org.json.JSONArray
import org.json.JSONObject

data class PendingGleoAction(
    val command: GleoCommand,
    val variant: ExperimentVariant,
)

class MainActivity : ComponentActivity() {
    private lateinit var appConsentStore: ai.yaho.hyundai.data.AppConsentStore
    private var appConsentListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val observationNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    private fun requestObservationNotification() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            observationNotificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
    }
    private val speedPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startDrivingMonitor()
        requestObservationNotification()
    }

    private fun startDrivingMonitor() {
        drivingUxMonitor?.release()
        drivingUxMonitor = DrivingUxMonitor(this,
            { state -> runOnUiThread { drivingUx.value = state } },
            { speed -> runOnUiThread { actualSpeedKph.value = speed } },
            readSpeed = appConsentStore.read().vehicleRead,
        ).also { it.start() }
    }
    private var fusedAdapter: ai.yaho.hyundai.vehicle.FusedLocationAdapter? = null
    private val liveLocationStatus = mutableStateOf(ai.yaho.hyundai.vehicle.LiveLocationStatus())
    private val fusedPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { startFusedLocation() }
    private fun startFusedLocation() {
        fusedAdapter?.release(); fusedAdapter = null
        liveLocationStatus.value = ai.yaho.hyundai.vehicle.LiveLocationStatus()
        if (!appConsentStore.read().liveLocationNetwork) return
        fusedAdapter = ai.yaho.hyundai.vehicle.FusedLocationAdapter(applicationContext,
            allowed = { appConsentStore.read().liveLocationNetwork && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
            changed = { status -> runOnUiThread { liveLocationStatus.value = status } },
        ).also { it.start() }
    }
    private lateinit var credentials: InstallationCredentialProvider
    private lateinit var backend: BackendApi
    private lateinit var consentStore: ConsentStore
    private lateinit var simulatorStateStore: SimulatorStateStore
    private var vehicleAdapter: PleosVehicleContextAdapter? = null

    private var navigationAdapter: NavigationContextAdapter? = null
    private val navigationSdkStatus = mutableStateOf(NavigationSdkStatus())
    private val navigationTurn = mutableStateOf<ai.yaho.hyundai.vehicle.NavigationTurn?>(null)
    private val navigationRouteStatus = mutableStateOf(ai.yaho.hyundai.core.NavigationRouteObservation())
    private var routeReadApproved = false

    private val actualSpeedKph = mutableStateOf<Float?>(null)
    private val drivingUx = mutableStateOf(DrivingUxState())
    private var drivingUxMonitor: DrivingUxMonitor? = null

    private val activeVariant = mutableStateOf(ExperimentVariant.A)
    private val drivingMode = mutableStateOf(DrivingMode.UNKNOWN)
    private val vehicleSdkStatus = mutableStateOf(VehicleSdkStatus())
    private val pendingGleoAction = mutableStateOf<PendingGleoAction?>(null)
    private val bannerMessage = mutableStateOf<String?>(null)
    private val gleoDebugDiagnostics = GleoDebugDiagnostics(BuildConfig.DEBUG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appConsentStore = ai.yaho.hyundai.data.AppConsentStore(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ai.yaho.hyundai.vehicle.VehicleObservationService.snapshots.collect { sdkSnapshot.value = it }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            setTaskDescription(android.app.ActivityManager.TaskDescription.Builder()
                .setLabel("YAHO Hyundai")
                .setIcon(R.mipmap.ic_launcher)
                .setPrimaryColor(android.graphics.Color.rgb(247, 247, 245))
                .build())
        }
        credentials = if (BuildConfig.DEBUG) {
            DebugInstallationCredentialStore(applicationContext)
        } else {
            ReleaseCredentialProvider()
        }
        backend = BackendApi(credentials, IdempotencyKeyStore(applicationContext))
        consentStore = ConsentStore(applicationContext)
        simulatorStateStore = SimulatorStateStore(applicationContext)
        drivingMode.value = DrivingMode.UNKNOWN
        startVehicleAdapter()
        handleGleoIntent(intent)
        setContent {
            YahoHyundaiTheme {
                YahoHyundaiApp(
                    backend = backend,
                    credentials = credentials,
                    consentStore = consentStore,
                    activeVariant = activeVariant,
                    drivingMode = drivingMode,
                    drivingUx = drivingUx.value,
                    actualSpeedKph = actualSpeedKph.value,
                    onRequestSpeedPermission = {
                        if (checkSelfPermission("android.car.permission.CAR_SPEED") != android.content.pm.PackageManager.PERMISSION_GRANTED) speedPermissionRequest.launch("android.car.permission.CAR_SPEED")
                        else requestObservationNotification()
                    },
                    vehicleSdkStatus = vehicleSdkStatus,
                    sdkSnapshot = sdkSnapshot.value,
                    navigationSdkStatus = navigationSdkStatus.value,
                    onReadNavigation = ::readNavigationOnce,
                    navigationTurn = navigationTurn.value,
                    onReadDrivingTurn = { navigationAdapter?.requestTurnOnce() },
                    onClearDrivingTurn = { navigationAdapter?.clearTurn() },
                    navigationRouteStatus = navigationRouteStatus.value,
                    onReadNavigationRoute = {
                        if(ai.yaho.hyundai.core.canUseParkedExperience(drivingMode.value,drivingUx.value) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            routeReadApproved=true;navigationAdapter?.requestRouteStateOnce()
                        }
                    },
                    liveLocationStatus = liveLocationStatus.value,
                    onRequestLiveLocationPermission = { fusedPermissionRequest.launch("pleos.car.permission.FUSED_LOCATION") },
                    simulatorStateStore = simulatorStateStore,
                    pendingGleoAction = pendingGleoAction,
                    bannerMessage = bannerMessage,
                    onGleoActionHandled = ::markGleoActionHandled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleoIntent(intent)
    }

    private val sdkSnapshot = mutableStateOf(VehicleSnapshot())

    override fun onStart() {
        super.onStart()
        appConsentListener = appConsentStore.listen { runOnUiThread { applyAppConsents() } }
        applyAppConsents()
    }

    private fun applyAppConsents() {
        val choice = appConsentStore.read()
        startDrivingMonitor()
        if (choice.vehicleRead) {
            startVehicleAdapter()
            try {
                startForegroundService(Intent(this, ai.yaho.hyundai.vehicle.VehicleObservationService::class.java))
            } catch (_: Exception) {
                sdkSnapshot.value = VehicleSnapshot(listOf(ai.yaho.hyundai.vehicle.SignalReading("Vehicle", "관측 서비스 시작 제한")), false)
            }
        } else {
            stopService(Intent(this, ai.yaho.hyundai.vehicle.VehicleObservationService::class.java))
            sdkSnapshot.value = VehicleSnapshot()
            vehicleAdapter?.release(); vehicleAdapter = null
            vehicleSdkStatus.value = VehicleSdkStatus()
            drivingMode.value = DrivingMode.UNKNOWN
        }
        startNavigationAdapter()
        startFusedLocation()
    }

    override fun onStop() {
        routeReadApproved=false
        navigationRouteStatus.value=ai.yaho.hyundai.core.NavigationRouteObservation()
        fusedAdapter?.release(); fusedAdapter = null
        liveLocationStatus.value = ai.yaho.hyundai.vehicle.LiveLocationStatus()
        appConsentListener?.let(appConsentStore::unlisten)
        appConsentListener = null
        drivingUxMonitor?.release()
        drivingUxMonitor = null
        navigationAdapter?.release()
        navigationAdapter = null
        navigationSdkStatus.value = NavigationSdkStatus()
        vehicleAdapter?.release()
        vehicleAdapter = null
        vehicleSdkStatus.value = VehicleSdkStatus()
        drivingMode.value = DrivingMode.UNKNOWN
        super.onStop()
    }

    private fun readNavigationOnce() {
        if (!ai.yaho.hyundai.core.canUseParkedExperience(drivingMode.value, drivingUx.value)) return
        if (navigationSdkStatus.value.state != ai.yaho.hyundai.vehicle.AdapterState.CONNECTED) {
            startNavigationAdapter()
            return
        }
        navigationAdapter?.requestLocationOnce()
    }

    private fun startNavigationAdapter() {
        navigationAdapter?.release()
        navigationAdapter = NavigationContextAdapter(applicationContext,
            onStatusChanged = { status -> runOnUiThread { navigationSdkStatus.value = status } },
            canReadLocation = { appConsentStore.read().location },
            onRouteChanged = { value -> runOnUiThread {
                navigationRouteStatus.value=value
                if(value.state != "pending") routeReadApproved=false
            } },
            canReadRoute = {routeReadApproved && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)},
            canReadTurn = {
                BuildConfig.DEBUG && appConsentStore.read().vehicleRead &&
                    vehicleSdkStatus.value.gear == "GEAR_DRIVE" && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            },
            onTurnChanged = { navigationTurn.value = it },
        ).also(NavigationContextAdapter::start)
    }

    private fun startVehicleAdapter() {
        if (!appConsentStore.read().vehicleRead) return
        if (vehicleAdapter != null) return
        vehicleAdapter = PleosVehicleContextAdapter(applicationContext) { status ->
            runOnUiThread {
                vehicleSdkStatus.value = status
                drivingMode.value = modeFromGear(status.gear)
            }
        }.also(PleosVehicleContextAdapter::start)
    }

    private fun handleGleoIntent(incoming: Intent?) {
        val action = incoming?.action ?: return
        if (action !in GLEO_FUNCTIONS) return

        val trace = gleoDebugDiagnostics.begin(action, android.os.SystemClock.elapsedRealtime())
        try {
            processGleoIntent(incoming, action, trace)
        } finally {
            gleoDebugDiagnostics.discard(trace)
        }
    }

    private fun processGleoIntent(incoming: Intent, action: String, trace: GleoDebugTraceToken?) {
        val parameters = when (val normalized = readGleoIntentParameters(incoming)) {
            is GleoIntentParameterNormalization.Valid -> normalized
            is GleoIntentParameterNormalization.Invalid -> {
                respondToGleo(action, "fail", "The request parameters were invalid.", trace = trace)
                bannerMessage.value = "Gleo 요청 정보 형식이 올바르지 않습니다."
                return
            }
        }
        when (val result = GleoActionParser.parse(action, parameters.actionParam, parameters.directParameters)) {
            is GleoParseResult.Invalid -> {
                respondToGleo(action, "fail", "The request could not be understood.", trace = trace)
                bannerMessage.value = "Gleo 요청을 확인하지 못했습니다. 다시 요청해 주세요."
            }

            is GleoParseResult.MissingFields -> {
                respondToGleo(
                    action,
                    "asking",
                    "Please provide ${result.fields.joinToString(", ")} and review the request in the app.",
                    JSONArray().put(JSONObject().put("missingFields", JSONArray(result.fields))),
                    trace,
                )
                bannerMessage.value = "필수 정보가 부족합니다: ${result.fields.joinToString(", ")}"
            }

            is GleoParseResult.Valid -> when (val command = result.command) {
                is GleoCommand.GetDrivingContext -> respondWithDrivingContext(command, trace)
                is GleoCommand.ReportRoadEvent -> {
                    when (val decision = GleoPendingReviewPolicy.decide(pendingGleoAction.value?.command, command)) {
                        is GleoPendingReviewDecision.KeepCurrent -> {
                            respondPendingReviewExists(command, decision.command, trace)
                            return
                        }
                        is GleoPendingReviewDecision.Start -> Unit
                    }
                    pendingGleoAction.value = PendingGleoAction(command, activeVariant.value)
                    val message = if (drivingMode.value == DrivingMode.PARKED) {
                        "Please review and explicitly confirm the road report in the app."
                    } else {
                        "Please park the vehicle, review the road report, and explicitly confirm it in the app."
                    }
                    respondToGleo(
                        command.functionName,
                        "asking",
                        message,
                        JSONArray().put(JSONObject().put("kind", command.kind)),
                        trace,
                    )
                    bannerMessage.value = "제보 내용이 준비됐습니다. 앱에서 내용을 확인하고 직접 보내 주세요."
                }

                is GleoCommand.RequestServiceBooking -> {
                    when (val decision = GleoPendingReviewPolicy.decide(pendingGleoAction.value?.command, command)) {
                        is GleoPendingReviewDecision.KeepCurrent -> {
                            respondPendingReviewExists(command, decision.command, trace)
                            return
                        }
                        is GleoPendingReviewDecision.Start -> Unit
                    }
                    pendingGleoAction.value = PendingGleoAction(command, activeVariant.value)
                    val message = if (drivingMode.value == DrivingMode.PARKED) {
                        "Please review and explicitly approve the service request in the app. No booking was made."
                    } else {
                        "Please park the vehicle and explicitly approve the service request in the app. No booking was made."
                    }
                    respondToGleo(
                        command.functionName,
                        "asking",
                        message,
                        JSONArray().put(JSONObject().put("serviceType", command.serviceType).put("booking", "not_submitted")),
                        trace,
                    )
                    bannerMessage.value = "정비 요청을 준비했습니다. 확인 전에는 정비소로 전송되지 않습니다."
                }
            }
        }
    }

    private fun readGleoIntentParameters(incoming: Intent): GleoIntentParameterNormalization {
        val extrasResult = runCatching { incoming.extras }
        if (extrasResult.isFailure) return GleoIntentParameterNormalization.Invalid(ACTION_PARAM_EXTRA)
        val extras = extrasResult.getOrNull()
            ?: return GleoIntentParameterPolicy.normalize(false, null, emptyMap())

        val actionParamPresent = extras.containsKey(ACTION_PARAM_EXTRA)
        val actionParam = if (actionParamPresent) {
            val raw = runCatching { extras.get(ACTION_PARAM_EXTRA) }
            if (raw.isFailure) return GleoIntentParameterNormalization.Invalid(ACTION_PARAM_EXTRA)
            raw.getOrNull()
        } else null

        val directParameterExtras = mutableMapOf<String, Any?>()
        for (key in GLEO_DIRECT_PARAMETER_KEYS) {
            if (!extras.containsKey(key)) continue
            val raw = runCatching { extras.get(key) }
            if (raw.isFailure) return GleoIntentParameterNormalization.Invalid(key)
            directParameterExtras[key] = raw.getOrNull()
        }
        return GleoIntentParameterPolicy.normalize(actionParamPresent, actionParam, directParameterExtras)
    }

    private fun respondPendingReviewExists(
        requested: GleoCommand,
        pending: GleoCommand,
        trace: GleoDebugTraceToken?,
    ) {
        respondToGleo(
            requested.functionName,
            "asking",
            "Please review and explicitly confirm the pending request before starting another request.",
            JSONArray().put(JSONObject()
                .put("pendingFunction", pending.functionName)
                .apply { if (pending is GleoCommand.ReportRoadEvent) put("pendingKind", pending.kind) }
                .put("newRequestStarted", false)),
            trace,
        )
        bannerMessage.value = "검토 대기 중인 요청을 먼저 처리해 주세요. 새 요청은 준비되지 않았습니다."
    }

    private fun respondWithDrivingContext(command: GleoCommand.GetDrivingContext, trace: GleoDebugTraceToken?) {
        val consent = appConsentStore.read()
        val snapshot = ai.yaho.hyundai.vehicle.VehicleObservationService.snapshots.value
        val observationServiceActive = consent.vehicleRead && snapshot.active
        val freshness = GleoObservationFreshnessPolicy.summarize(
            consented = consent.vehicleRead,
            serviceActive = observationServiceActive,
            nowElapsedMillis = android.os.SystemClock.elapsedRealtime(),
            observations = snapshot.readings.map { GleoObservationClock(it.state == "수신", it.receivedElapsed) },
        )
        val context = JSONObject().put("scope", command.scope ?: "all")
            .put("source", "pleos_sdk_cache").put("simulatedValuesIncluded", false)
        if (command.scope != "navigation") context.put("vehicle", JSONObject()
            .put("observationConsent", consent.vehicleRead)
            .put("observationServiceActive", observationServiceActive)
            .put("freshReadingCount", freshness.count)
            .put("freshestObservationAgeMs", freshness.freshestAgeMillis ?: JSONObject.NULL)
            .put("status", when {
                !consent.vehicleRead -> "consent_required"
                !observationServiceActive -> "observation_inactive"
                freshness.count == 0 -> "awaiting_fresh_observation"
                else -> "fresh_observations_available"
            }))
        if (command.scope != "vehicle") context.put("navigation", JSONObject()
            .put("state", navigationSdkStatus.value.state.name.lowercase())
            .put("locationConsent", consent.location).put("coordinatesIncluded", false))
        respondToGleo(command.functionName, "success",
            "Returns cached SDK availability only. No sample speed, location or battery is substituted. Private signal values are not broadcast.",
            JSONArray().put(context), trace)
        bannerMessage.value = "실제 SDK 수신 상태를 전달했습니다. 샘플 값과 개인 위치는 포함하지 않았습니다."
    }

    private fun markGleoActionHandled(functionName: String) {
        val pending = pendingGleoAction.value ?: return
        if (pending.command.functionName != functionName) return
        pendingGleoAction.value = null
    }

    private fun respondToGleo(
        functionName: String,
        status: String,
        englishMessage: String,
        data: JSONArray? = null,
        trace: GleoDebugTraceToken?,
    ) {
        val response = JSONObject()
            .put("functionName", functionName)
            .put("status", status)
            .put("message", englishMessage)
        data?.let { response.put("data", it.toString()) }
        sendBroadcast(
            Intent(GLEO_RESPONSE_ACTION)
                .putExtra("packageName", packageName)
                .putExtra("response", response.toString()),
        )
        if (BuildConfig.DEBUG && trace != null) {
            gleoDebugDiagnostics.complete(trace, status, android.os.SystemClock.elapsedRealtime())?.let {
                Log.d(GLEO_TRACE_TAG, it.toLogLine())
            }
        }
    }

    companion object {
        private const val GLEO_TRACE_TAG = "YahoGleoTrace"
        private const val ACTION_PARAM_EXTRA = "ACTION_PARAM"
        private val GLEO_DIRECT_PARAMETER_KEYS = listOf("scope", "kind", "serviceType", "requestedDate", "notes")
        private const val GLEO_RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private val GLEO_FUNCTIONS = setOf("GET_DRIVING_CONTEXT", "REPORT_ROAD_EVENT", "REQUEST_SERVICE_BOOKING")
    }
}
