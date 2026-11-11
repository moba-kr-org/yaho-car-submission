package ai.yaho.hyundai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import ai.yaho.hyundai.BuildConfig
import ai.yaho.hyundai.PendingGleoAction
import ai.yaho.hyundai.core.ConsentChoice
import ai.yaho.hyundai.core.DrivingMode
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.GleoCommand
import ai.yaho.hyundai.core.SafetyGuards
import ai.yaho.hyundai.core.ServiceDateParser
import ai.yaho.hyundai.data.ApiMessage
import ai.yaho.hyundai.data.BackendApi
import ai.yaho.hyundai.data.ConsentStore
import ai.yaho.hyundai.data.InstallationCredentialProvider
import ai.yaho.hyundai.data.KnowledgeResult
import ai.yaho.hyundai.data.OwnerReportState
import ai.yaho.hyundai.data.OwnerReportStore
import ai.yaho.hyundai.data.RoadFeedItem
import ai.yaho.hyundai.data.SimulatorStateStore
import ai.yaho.hyundai.vehicle.AdapterState
import ai.yaho.hyundai.vehicle.NavigationSdkStatus
import ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter
import ai.yaho.hyundai.vehicle.VehicleSdkStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.time.LocalDate
import java.time.ZoneId

private val Ink = Color(0xFF241F1A)
private val Page = Color(0xFFF7F7F5)
private val CardSurface = GlassFill
private val CardRaised = GlassInset
private val Muted = Color(0xFF6F6861)
private val Line = Color(0xFFCFCBC5).copy(alpha = .55f)
private val Mint = Color(0xFFB84A08)
private val Amber = Color(0xFF8C5800)
private val Coral = Color(0xFFB6342B)

private enum class HomeAction(val title: String, val section: SettingsSection) {
    CALL("AI 전화", SettingsSection.SERVICE),
    SERVICE("정비·예약", SettingsSection.SERVICE),
    ROAD("주변 도로", SettingsSection.ROAD),
    REPORT("빠른 제보", SettingsSection.ROAD),
}

private data class ServiceRequestApproval(
    val review: ai.yaho.hyundai.core.ServiceRequestReview,
    val vehicle: ai.yaho.hyundai.vehicle.VehicleContext,
    val pendingGleoAction: PendingGleoAction?,
)

private sealed interface ConfirmAction {
    data object SubmitReport : ConfirmAction
    data class SubmitServiceRequest(val approval: ServiceRequestApproval) : ConfirmAction
    data class EventFeedback(val item: RoadFeedItem, val state: String) : ConfirmAction
    data class OwnerReport(val state: String) : ConfirmAction
    data object WithdrawConsent : ConfirmAction
}

@Composable
internal fun YahoHyundaiApp(
    backend: BackendApi,
    credentials: InstallationCredentialProvider,
    consentStore: ConsentStore,
    activeVariant: MutableState<ExperimentVariant>,
    drivingMode: MutableState<DrivingMode>,
    drivingUx: ai.yaho.hyundai.core.DrivingUxState,
    actualSpeedKph: Float?,
    onRequestSpeedPermission: () -> Unit,
    vehicleSdkStatus: MutableState<VehicleSdkStatus>,
    sdkSnapshot: ai.yaho.hyundai.vehicle.VehicleSnapshot,
    navigationSdkStatus: NavigationSdkStatus,
    onReadNavigation: () -> Unit,
    navigationTurn: ai.yaho.hyundai.vehicle.NavigationTurn?,
    onReadDrivingTurn: () -> Unit,
    onClearDrivingTurn: () -> Unit,
    navigationRouteStatus: ai.yaho.hyundai.core.NavigationRouteObservation,
    onReadNavigationRoute: () -> Unit,
    liveLocationStatus: ai.yaho.hyundai.vehicle.LiveLocationStatus,
    onRequestLiveLocationPermission: () -> Unit,
    simulatorStateStore: SimulatorStateStore,
    pendingGleoAction: MutableState<PendingGleoAction?>,
    bannerMessage: MutableState<String?>,
    onGleoActionHandled: (functionName: String) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val appConsents = rememberAppConsents()
    val manualProfileStore = remember { ai.yaho.hyundai.data.VehicleManualProfileStore(appContext) }
    var manualProfile by remember { mutableStateOf(manualProfileStore.read()) }
    val appConsentStore = remember { ai.yaho.hyundai.data.AppConsentStore(appContext) }
    val saveAppConsents: (ai.yaho.hyundai.data.AppConsents) -> Unit = { next ->
        val previous = appConsentStore.read()
        appConsentStore.write(next)
        if (next.liveLocationNetwork && !previous.liveLocationNetwork) onRequestLiveLocationPermission()
        if (next.vehicleRead && (!previous.reviewed || !previous.vehicleRead)) onRequestSpeedPermission()
    }
    val variant by activeVariant
    val mode by drivingMode
    val vehicle = remember(mode) { SimulatorVehicleContextAdapter(mode).readContext()!! }

    val networkLocation = if (!appConsents.liveLocationNetwork) vehicle else liveLocationStatus.fix?.let { fix ->
        vehicle.copy(latitude = fix.latitude, longitude = fix.longitude,
            headingDegrees = fix.heading?.toInt(), areaLabel = "위치 SDK 관측",
            sourceLabel = if (fix.emulator) "FusedLocation 에뮬레이터" else "FusedLocation",
            isSample = fix.emulator, locationEvidence = fix)
    }
    val coroutineScope = rememberCoroutineScope()
    val consentChoice = remember(variant) { mutableStateOf(consentStore.isGranted(variant)) }
    val consentChecked = remember(variant) { mutableStateOf(consentStore.isGranted(variant)) }
    val shareConsent by consentChecked
    val consentGranted by consentChoice
    val ownerReportStore = remember { OwnerReportStore(appContext) }
    var ownerReport by remember(variant) { mutableStateOf(ownerReportStore.read(variant)) }
    var feed by remember { mutableStateOf<List<RoadFeedItem>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedReceivedElapsed by remember { mutableStateOf<Long?>(null) }
    val feedLifecycle = LocalLifecycleOwner.current.lifecycle
    var feedLoading by remember { mutableStateOf(false) }
    var knowledge by remember { mutableStateOf<List<KnowledgeResult>>(emptyList()) }
    var knowledgeError by remember { mutableStateOf<String?>(null) }
    var knowledgeLoading by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SettingsSection.VEHICLE) }
    var searchQuery by remember { mutableStateOf("타이어 공기압 경고등") }
    var reportKind by remember(variant) {
        mutableStateOf((pendingGleoAction.value?.command as? GleoCommand.ReportRoadEvent)?.kind ?: "road_hazard")
    }
    var serviceType by remember(variant) {
        mutableStateOf((pendingGleoAction.value?.command as? GleoCommand.RequestServiceBooking)?.serviceType ?: "타이어 점검")
    }
    var serviceDate by remember(variant) {
        mutableStateOf(
            (pendingGleoAction.value?.command as? GleoCommand.RequestServiceBooking)?.requestedDate
                ?: defaultServiceDate(),
        )
    }
    var serviceNotes by remember(variant) {
        mutableStateOf((pendingGleoAction.value?.command as? GleoCommand.RequestServiceBooking)?.notes.orEmpty())
    }
    var phoneBooking by remember(variant) { mutableStateOf(false) }
    var workshopName by remember(variant) { mutableStateOf("") }
    var warningServiceOrigin by remember(variant) { mutableStateOf<ai.yaho.hyundai.core.WarningServiceOrigin?>(null) }
    var selectedConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var serviceSubmissionStarted by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var installationRevision by remember { mutableIntStateOf(0) }
    var feedRevision by remember { mutableIntStateOf(0) }
    var bookingRevision by remember { mutableIntStateOf(0) }

    val identity = remember(variant, installationRevision) { credentials.credentialsFor(variant) }
    val isParked = ai.yaho.hyundai.core.canUseParkedExperience(mode, drivingUx)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(isParked) {
        if (!isParked) { focusManager.clearFocus(force = true); keyboard?.hide(); selectedConfirm = null }
    }
    val hasInstall = identity != null
    val canShare = isParked && hasInstall && backend.isConfigured && !isBusy
    val warningAssessment = rememberWarningAssessment(sdkSnapshot, appConsents.vehicleRead)

    fun currentServiceRequestReview(): ai.yaho.hyundai.core.ServiceRequestReview {
        val collectingWarnings = appConsents.vehicleRead && sdkSnapshot.active
        val currentWarnings = ai.yaho.hyundai.core.assessVehicleWarnings(
            sdkSnapshot.readings.map {
                ai.yaho.hyundai.core.WarningObservation(it.key, it.state, it.value, it.observedAt, it.receivedElapsed)
            },
            collectingWarnings,
            System.currentTimeMillis(),
            android.os.SystemClock.elapsedRealtime(),
        )
        val notes = ai.yaho.hyundai.core.warningServiceNotes(
            serviceNotes, warningServiceOrigin, currentWarnings, collectingWarnings,
        )
        return ai.yaho.hyundai.core.ServiceRequestReview(
            variant = variant,
            installationId = credentials.credentialsFor(variant)?.installationId,
            serviceType = serviceType,
            requestedDateInput = serviceDate,
            normalizedRequestedDate = ServiceDateParser.normalize(serviceDate),
            notes = notes.ifBlank { null },
            latitude = vehicle.latitude.takeUnless { phoneBooking },
            longitude = vehicle.longitude.takeUnless { phoneBooking },
            locationIsSample = !phoneBooking && vehicle.isSample,
            warningOrigin = warningServiceOrigin,
            workshopName = workshopName.trim().takeIf { phoneBooking },
        )
    }

    fun captureServiceRequestApproval() = ServiceRequestApproval(
        review = currentServiceRequestReview(),
        vehicle = vehicle,
        pendingGleoAction = pendingGleoAction.value,
    )

    fun serviceRequestApprovalIsCurrent(approval: ServiceRequestApproval): Boolean {
        val sourceVariant = approval.pendingGleoAction
            ?.takeIf { it.command is GleoCommand.RequestServiceBooking }
            ?.variant
        return ai.yaho.hyundai.core.isCurrentServiceRequestReview(approval.review, currentServiceRequestReview()) &&
            approval.pendingGleoAction === pendingGleoAction.value &&
            (sourceVariant == null || sourceVariant == approval.review.variant)
    }

    val latestServiceApprovalCheck by rememberUpdatedState(newValue = { approval: ServiceRequestApproval ->
        isParked && serviceRequestApprovalIsCurrent(approval)
    })

    fun cancelPendingGleoAction(expected: PendingGleoAction) {
        if (isBusy || serviceSubmissionStarted) return
        val current = pendingGleoAction.value ?: return
        if (current !== expected || current.variant != variant || current.command.functionName != expected.command.functionName) {
            resultMessage = "현재 Gleo 요청이나 구성이 바뀌었습니다. 화면의 요청을 다시 확인해 주세요."
            return
        }
        when (current.command) {
            is GleoCommand.RequestServiceBooking -> {
                phoneBooking = false
                warningServiceOrigin = null
                serviceType = "타이어 점검"
                serviceDate = defaultServiceDate()
                serviceNotes = ""
            }
            is GleoCommand.ReportRoadEvent -> reportKind = "road_hazard"
            is GleoCommand.GetDrivingContext -> return
        }
        selectedConfirm = null
        onGleoActionHandled(current.command.functionName)
        resultMessage = "앱에서 준비 중이던 Gleo 요청을 취소했습니다. 이미 서버에 접수된 제보·정비 요청 상태는 변경하지 않았습니다."
    }

    LaunchedEffect(appConsents.reviewed, appConsents.network, variant) {
        consentChecked.value = appConsents.reviewed && appConsents.network
        if (appConsents.reviewed && !appConsents.network) {
            consentChoice.value = false
            ai.yaho.hyundai.core.ExperimentVariant.entries.forEach { target ->
                consentStore.setGranted(target, false)
            }
        }
    }

    LaunchedEffect(pendingGleoAction.value?.command) {
        when (val command = pendingGleoAction.value?.command) {
            is GleoCommand.ReportRoadEvent -> { reportKind = command.kind; section = SettingsSection.ROAD }
            is GleoCommand.RequestServiceBooking -> {
                section = SettingsSection.SERVICE
                phoneBooking = false
                warningServiceOrigin = null
                serviceType = command.serviceType
                serviceDate = command.requestedDate
                serviceNotes = command.notes.orEmpty()
            }
            else -> Unit
        }
    }

    LaunchedEffect(variant, installationRevision, feedRevision, appConsents.reviewed, appConsents.network, feedLifecycle) {
        feedReceivedElapsed = null
        if (credentials.credentialsFor(variant) == null || !backend.isConfigured) {
            feed = emptyList()
            feedError = if (!backend.isConfigured) backend.configurationMessage else "이 구성의 로컬 설치 인증이 필요합니다."
            return@LaunchedEffect
        }
        feedLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                feedLoading = feed.isEmpty()
                val response = backend.fetchFeed(variant)
                currentCoroutineContext().ensureActive()
                feed = response.getOrNull().orEmpty()
                feedError = response.exceptionOrNull()?.message
                feedReceivedElapsed = if (response.isSuccess) android.os.SystemClock.elapsedRealtime() else null
                feedLoading = false

                if (!appConsents.reviewed || !appConsents.network) break
                delay(15_000)
            } while (true)
        }
    }

    suspend fun ensureConsent(): Boolean {
        if (!consentChecked.value) {
            resultMessage = "전송 동의를 확인한 뒤 계속해 주세요."
            return false
        }
        if (consentChoice.value) return true
        val response = backend.updateConsent(variant, true)
        if (response.success) {
            consentStore.setGranted(variant, true)
            consentChoice.value = true
            consentChecked.value = true
            return true
        }
        resultMessage = response.text
        return false
    }

    fun refreshFeed() {
        if (!isParked || isBusy) return
        feedRevision += 1
    }

    suspend fun execute(action: ConfirmAction) {
        if (!isParked) {
            resultMessage = "안전한 곳에 주차한 뒤 계속해 주세요."
            return
        }
        if (action != ConfirmAction.WithdrawConsent && !backend.isConfigured) {
            resultMessage = backend.configurationMessage
            return
        }
        if (action != ConfirmAction.WithdrawConsent && !hasInstall) {
            resultMessage = "이 구성의 로컬 설치를 연결한 뒤 계속해 주세요."
            return
        }

        isBusy = true
        resultMessage = null
        try {
            when (action) {
                ConfirmAction.SubmitReport -> {
                    if (!ensureConsent()) return
                    val pending = pendingGleoAction.value
                    val command = (pending?.command as? GleoCommand.ReportRoadEvent)
                        ?: GleoCommand.ReportRoadEvent(reportKind)
                    val response = backend.submitReport(variant, command, vehicle)
                    if (response.success && response.recordId != null) {
                        ownerReportStore.save(variant, response.recordId)
                        ownerReport = ownerReportStore.read(variant)
                    }
                    resultMessage = if (response.success) {
                        "제보를 접수했습니다. 주변 피드는 300m 단위 집계로 표시됩니다."
                    } else response.text
                    if (response.success) feedRevision += 1
                    onGleoActionHandled("REPORT_ROAD_EVENT")
                }

                is ConfirmAction.SubmitServiceRequest -> {
                    val approval = action.approval
                    if (!serviceRequestApprovalIsCurrent(approval)) {
                        resultMessage = "요청 내용이나 차량 정보가 바뀌었습니다. 최신 내용을 다시 검토하고 승인해 주세요."
                        return
                    }
                    val review = approval.review
                    val normalizedDate = review.normalizedRequestedDate
                    if (normalizedDate == null) {
                        resultMessage = "희망 일시를 ISO 8601 날짜 또는 날짜·시간 형식으로 입력해 주세요."
                        return
                    }
                    val notes = review.notes.orEmpty()
                    if (notes.length > 500 || notes.contains('\u0000')) {
                        resultMessage = "경고 확인 문구를 포함한 메모는 500자 이내여야 합니다. 메모를 줄여 주세요."
                        return
                    }
                    if (credentials.credentialsFor(review.variant)?.installationId != review.installationId) {
                        resultMessage = "설치 연결이 바뀌었습니다. 요청 대상을 다시 확인해 주세요."
                        return
                    }
                    val command = GleoCommand.RequestServiceBooking(review.serviceType, normalizedDate, review.notes)
                    val response = backend.requestService(review.variant, command, approval.vehicle, approved = true,
                        approvalStillCurrent = { latestServiceApprovalCheck(approval) },
                        workshopName = review.workshopName,
                    )
                    bookingRevision += 1
                    resultMessage = if (response.success) {
                        if (review.workshopName != null) "AI 전화 요청을 저장했습니다. 전화 연동 대기 중입니다."
                        else "정비 요청을 접수했습니다. 예약 확정은 정비소 응답 후 표시됩니다."
                    } else response.text
                    val sourceAction = approval.pendingGleoAction
                    if (sourceAction != null && sourceAction.command is GleoCommand.RequestServiceBooking &&
                        pendingGleoAction.value === sourceAction) {
                        onGleoActionHandled("REQUEST_SERVICE_BOOKING")
                    }
                }

                is ConfirmAction.EventFeedback -> {
                    if (!ensureConsent()) return
                    val source = if (vehicle.isSample) "simulator" else "user"
                    val response = backend.sendEventFeedback(variant, action.item.eventKey, action.state, source = source)
                    resultMessage = response.text
                    if (response.success) feedRevision += 1
                }

                is ConfirmAction.OwnerReport -> {
                    if (!ensureConsent()) return
                    val report = ownerReport ?: return
                    val response = backend.updateOwnedReport(variant, report.reportId, action.state)
                    resultMessage = response.text
                    if (response.success) {
                        ownerReportStore.save(variant, report.reportId, action.state)
                        ownerReport = ownerReportStore.read(variant)
                        feedRevision += 1
                    }
                }

                ConfirmAction.WithdrawConsent -> {
                    appContext.getSharedPreferences("vehicle_network_choice", 0).edit().putBoolean("${variant.wireKey}_sharing", false).commit()
                    val response = backend.updateConsent(variant, false)
                    resultMessage = response.text
                    if (response.success) {
                        consentStore.setGranted(variant, false)
                        consentChoice.value = false
                        consentChecked.value = false
                        ownerReportStore.clear(variant)
                        ownerReport = null
                        feed = emptyList()
                        feedError = "공유 동의를 철회했습니다."
                        feedRevision += 1
                    }
                }
            }
        } catch (_: Exception) {
            resultMessage = "요청 결과를 확인할 수 없습니다. 같은 내용을 다시 확인해 주세요."
            when (action) {
                ConfirmAction.SubmitReport -> onGleoActionHandled("REPORT_ROAD_EVENT")
                is ConfirmAction.SubmitServiceRequest -> {
                    val sourceAction = action.approval.pendingGleoAction
                    if (sourceAction != null && sourceAction.command is GleoCommand.RequestServiceBooking &&
                        pendingGleoAction.value === sourceAction) {
                        onGleoActionHandled("REQUEST_SERVICE_BOOKING")
                    }
                }
                else -> Unit
            }
        } finally {
            isBusy = false
        }
    }

    val currentGear = vehicleSdkStatus.value.gear
    var settingsOpen by remember { mutableStateOf(false) }
    val aboveSettingsLimit = ai.yaho.hyundai.core.exceedsSettingsSpeedLimit(actualSpeedKph)
    val settingsAllowed = !drivingUx.restricted && !aboveSettingsLimit
    LaunchedEffect(settingsAllowed) {
        if (!settingsAllowed) settingsOpen = false
    }
    LaunchedEffect(pendingGleoAction.value, isParked, settingsAllowed) {
        if (pendingGleoAction.value != null && isParked && settingsAllowed) settingsOpen = true
    }
    val showDrivingFace = !settingsOpen || !settingsAllowed
    fun openSettings(target: SettingsSection) {
        if (settingsAllowed) {
            section = target
            settingsOpen = true
        }
    }
    BackHandler(enabled = !showDrivingFace) { settingsOpen = false }
    val showTurnPreviewControls = BuildConfig.DEBUG && android.os.Build.HARDWARE in setOf("ranchu", "goldfish")
    var turnPreviewMode by remember { mutableStateOf(TurnPreviewMode.OFF) }
    val inDrive = currentGear == "GEAR_DRIVE"
    val canTouchDrivingCompanion = drivingUx.known &&
        actualSpeedKph?.let { it.isFinite() && kotlin.math.abs(it) < .5f } == true
    val turnPreviewVisible = showTurnPreviewControls && showDrivingFace && inDrive &&
        turnPreviewMode != TurnPreviewMode.OFF
    val readDrivingTurn by rememberUpdatedState(onReadDrivingTurn)
    val clearDrivingTurn by rememberUpdatedState(onClearDrivingTurn)
    LaunchedEffect(turnPreviewVisible, turnPreviewMode) {
        if (turnPreviewVisible && turnPreviewMode == TurnPreviewMode.SDK) {
            feedLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    while (true) {
                        readDrivingTurn()
                        delay(2_500)
                    }
                } finally {
                    clearDrivingTurn()
                }
            }
        }
    }


    val networkUiState = rememberVehicleNetworkState(backend, variant, hasInstall, sdkSnapshot, networkLocation)
    val visibilityAssessment = rememberVisibilityAssessment(sdkSnapshot, appConsents.reviewed && appConsents.vehicleRead)


    val demoWarning = rememberDemoWarning()
    val priorityNotice = rememberDrivingPriority(warningAssessment, feed, feedReceivedElapsed, feedError,
        networkLocation, appConsents.reviewed && appConsents.vehicleRead && appConsents.network, networkUiState.notice, visibilityAssessment.notice)
    val rainGuidance = rememberRainGuidance(priorityNotice)
    val homeVehicle = rememberHomeVehicleStatus(sdkSnapshot, appConsents.reviewed && appConsents.vehicleRead,
        warningAssessment.active.size, warningAssessment.checked)
    val canOpenHomeDialog = showDrivingFace && isParked && settingsAllowed && appConsents.reviewed


    val homeNotice = rememberHomeNotice(warningAssessment, demoWarning, null,
        appConsents.reviewed && appConsents.vehicleRead)
    val warningOnHome = homeNotice is ai.yaho.hyundai.core.HomeNotice.Warning

    val canInspect = isParked && settingsAllowed && appConsents.reviewed
    var inspection by remember { mutableStateOf<ai.yaho.hyundai.core.HomeNotice.Warning?>(null) }
    val inspectionSymptoms = remember { androidx.compose.runtime.mutableStateMapOf<String, List<String>>() }
    LaunchedEffect(canInspect) { if (!canInspect) inspection = null }

    fun openHyundaiServiceNetwork(): Boolean {
        val page = Intent(Intent.ACTION_VIEW, Uri.parse(ai.yaho.hyundai.core.HYUNDAI_SERVICE_NETWORK_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { appContext.startActivity(page) }.isSuccess
    }
    var homeAction by remember { mutableStateOf<HomeAction?>(null) }
    var homeControlsOpen by remember { mutableStateOf(false) }
    var showTalk by remember { mutableStateOf(false) }
    var showHomeVehicle by remember { mutableStateOf(false) }
    var homeReply by remember { mutableStateOf<Pair<Long, String>?>(null) }
    LaunchedEffect(canOpenHomeDialog) {
        if (!canOpenHomeDialog) {
            showTalk = false
            homeAction = null
            homeControlsOpen = false
            showHomeVehicle = false
            homeReply = null
        }
    }
    LaunchedEffect(homeAction, showHomeVehicle) {
        if (homeAction != null || showHomeVehicle) showTalk = false
    }
    LaunchedEffect(homeReply) {
        val reply = homeReply ?: return@LaunchedEffect
        rainGuidance.speakReply(reply.second)
        delay(8_000)
        if (homeReply == reply) homeReply = null
    }


    val canControlVehicle = appConsents.manualControl && currentGear == "GEAR_PARK" && !drivingUx.restricted &&
        actualSpeedKph?.let { it.isFinite() && it >= 0f && it < .5f } == true
    val controlBlockedReason = when {
        !appConsents.manualControl -> "권한에서 창문·와이퍼 조작에 동의해 주세요."
        currentGear != "GEAR_PARK" -> "P단에서 조작해 주세요."
        !drivingUx.known || actualSpeedKph == null -> "차량 상태를 확인하고 있습니다."
        else -> "차량이 완전히 정차한 뒤 조작해 주세요."
    }
    val inlineControlsVisible = homeControlsOpen && canOpenHomeDialog
    val manualControls = rememberManualVehicleControls(
        active = inlineControlsVisible || (!showDrivingFace && section == SettingsSection.VEHICLE),
        canControl = canControlVehicle,
    )

    val settingsContent: @Composable (SettingsSection, HomeAction?) -> Unit = { activeSection, action ->
    pendingGleoAction.value?.let { pending ->
        GleoRequestCard(
            action = pending,
            isParked = isParked,
            currentVariant = variant,
            enabled = !isBusy && !serviceSubmissionStarted,
            onCancel = { cancelPendingGleoAction(pending) },
            onSwitchVariant = {
                if (!isBusy && !serviceSubmissionStarted) {
                    activeVariant.value = pending.variant
                    resultMessage = null
                }
            },
        )
    }
    if (activeSection == SettingsSection.ROAD && action != HomeAction.REPORT) {
        VehicleNetworkPanel(networkUiState, rainGuidance)
        if (action == HomeAction.ROAD) SettingsPanel {
            TextButton(onClick = {
                activeVariant.value = ExperimentVariant.A
                homeAction = HomeAction.REPORT
            }, enabled = pendingGleoAction.value == null) { Text("빠른 제보") }
        }
    }
    if (activeSection == SettingsSection.PERMISSIONS) {
        AppConsentPanel(appConsents, false, !drivingUx.restricted, saveAppConsents)
        SettingsPanel(title = "위치 권한 상태") {
            Text(when (liveLocationStatus.state) {
                "fresh" -> "새 위치 수신"
                "permission_required" -> "OS 위치 권한 필요"
                "unsupported" -> "현재 환경 미지원"
                "waiting" -> "위치 수신 대기"
                "no_fix" -> "위치 미제공"
                "stale_or_invalid" -> "위치가 오래됐거나 유효하지 않음"
                "provider_error" -> "위치 서비스 오류"
                else -> "꺼짐"
            })
            Text("앱 전면 관측 · 위치 정확도 미확인", color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
            if (appConsents.liveLocationNetwork && liveLocationStatus.state == "permission_required") {
                OutlinedButton(onClick = onRequestLiveLocationPermission, enabled = !drivingUx.restricted) {
                    Text("OS 위치 권한 확인")
                }
            }
        }
    }
    if (activeSection == SettingsSection.ACCOUNT) AccountPreviewPanel()
    if (activeSection == SettingsSection.NAVIGATION) NavigationDestinationPanel(isParked && !drivingUx.restricted && !isBusy)
    if (activeSection == SettingsSection.NAVIGATION) NavigationRoutePanel(navigationRouteStatus,isParked && !drivingUx.restricted && navigationSdkStatus.state == AdapterState.CONNECTED,onReadNavigationRoute)
    if (activeSection == SettingsSection.VEHICLE) CompanionVehicleActions(
        manualControls, canControlVehicle, controlBlockedReason, onRequestSpeedPermission,
    )
    if (activeSection == SettingsSection.VEHICLE && action == null) {
        SettingsPanel(title = "내 차 상태") {
            homeVehicle.energy.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(homeVehicle.warning, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { section = SettingsSection.DIAGNOSTICS }) { Text("경고 확인") }
        }
        SettingsTiles(listOf(
            SettingsTile("휴대폰 연결", "블루투스 기기 관리", "phone", if (isParked && settingsAllowed) ({
                try {
                    appContext.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(appContext, "차량에서 블루투스 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(appContext, "차량에서 블루투스 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }) else null),
        ))
        SettingsDetails("차량 연결 상세", "설명서 적용 기준 및 수신 정보") {
            VehicleManualProfilePanel(manualProfile, isParked && !drivingUx.restricted) { profile ->
                val saved = if (profile == null) manualProfileStore.clear() else manualProfileStore.write(profile)
                if (saved) manualProfile = manualProfileStore.read()
                saved
            }
            SdkSnapshotPanel(sdkSnapshot)
            VisibilityPanel(visibilityAssessment)
            VehiclePanel(
                mode = mode,
                vehicle = vehicle,
                vehicleSdkStatus = vehicleSdkStatus.value,
                actualVehicleState = vehicleSdkStatus.value.state,
                actualVehicleName = "Pleos Vehicle SDK",
                navigationState = navigationSdkStatus.state,
                navigationName = "Pleos NaviHelper SDK",
                onModeChanged = { newMode ->
                    simulatorStateStore.write(newMode)
                    drivingMode.value = newMode
                },
            )
        }
    }
    if (activeSection == SettingsSection.DIAGNOSTICS) VehicleWarningPanel(
        assessment = warningAssessment,
        manualProfile = manualProfile,
        enabled = isParked && !drivingUx.restricted && !isBusy,
        onPrepareService = { item ->

            if (canInspect && warningAssessment.active.any { it.key == item.key && it.observedAt == item.observedAt })
                inspection = ai.yaho.hyundai.core.HomeNotice.Warning(item, ai.yaho.hyundai.core.WarningSource.SDK)
            else resultMessage = "새 경고 상태와 정차 여부를 다시 확인해 주세요."
        },
    )
    if (activeSection == SettingsSection.DIAGNOSTICS) {
        SettingsDetails("고장코드 사전", "코드나 증상을 직접 검색") { DtcCatalogPanel(isParked) }
    }

    if (activeSection == SettingsSection.NAVIGATION) {
        SettingsPanel(title = "내비 위치") {
            Text(navigationSdkStatus.message)
            Text("현재 위치 수신을 확인합니다. 목적지 변경은 위에서 별도로 요청할 수 있습니다.",
                color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = onReadNavigation,
                enabled = isParked && appConsents.location && navigationSdkStatus.state != AdapterState.CONNECTING,
            ) {
                Text(if (!appConsents.location) "위치 조회 동의 필요"
                    else if (navigationSdkStatus.state == AdapterState.CONNECTED) "위치 다시 확인" else "내비 서비스 다시 연결")
            }
        }
    }

    if (activeSection == SettingsSection.NAVIGATION && showTurnPreviewControls) {
        SettingsDetails("개발자 도구", "에뮬레이터에서 주행 화면 확인") {
            TurnPreviewSettings(turnPreviewMode) { turnPreviewMode = it }
        }
    }

    bannerMessage.value?.let { text ->
        InfoStrip(text = text, tone = Mint, onDismiss = { bannerMessage.value = null })
    }

    if (activeSection == SettingsSection.ROAD && action != HomeAction.ROAD) {
    if (action == null) VariantPicker(
        selected = variant,
        enabled = pendingGleoAction.value == null,
        onSelect = { activeVariant.value = it; resultMessage = null },
    )

    if (variant == ExperimentVariant.A) {
        ReportPanel(
            kind = reportKind,
            onKindChange = { reportKind = it },
            startVoice = action == HomeAction.REPORT,
            shareConsent = shareConsent,
            consentGranted = consentGranted,
            canShare = canShare,
            isBusy = isBusy,
            onReport = { selectedConfirm = ConfirmAction.SubmitReport },
        )
        if (action == null) OwnerReportPanel(
            report = ownerReport,
            isEnabled = canShare && consentGranted,
            isBusy = isBusy,
            onConfirm = { selectedConfirm = ConfirmAction.OwnerReport("confirm") },
            onClear = { selectedConfirm = ConfirmAction.OwnerReport("clear") },
        )
    } else {
        ConsentPanel(
            shareConsent = shareConsent,
            consentGranted = consentGranted,
            canToggle = isParked && !isBusy,
            onConsentChange = { checked ->
                if (!checked && consentGranted) selectedConfirm = ConfirmAction.WithdrawConsent
                else consentChecked.value = checked
            },
        )
        FeedPanel(
            items = feed,
            error = feedError,
            loading = feedLoading,
            isSample = vehicle.isSample,
            isEnabled = canShare && consentChecked.value,
            onRefresh = ::refreshFeed,
            onFeedback = { item, state -> selectedConfirm = ConfirmAction.EventFeedback(item, state) },
        )
    }

    }

    if (activeSection == SettingsSection.SERVICE) warningServiceOrigin?.let { origin ->
        SettingsPanel(title = "경고 기반 점검 초안") {
            Text(ai.yaho.hyundai.core.warningServiceStatus(origin, warningAssessment, appConsents.vehicleRead && sdkSnapshot.active))
        }
    }
    if (activeSection == SettingsSection.SERVICE) KnowledgeAndServicePanel(
        query = searchQuery,
        onQueryChange = { if (isParked) searchQuery = it },
        results = knowledge,
        error = knowledgeError,
        loading = knowledgeLoading,
        phoneBooking = phoneBooking,
        onBookingModeChange = { if (isParked && !isBusy && pendingGleoAction.value == null) phoneBooking = it },
        canChangeBookingMode = isParked && pendingGleoAction.value == null,
        workshopName = workshopName,
        onWorkshopNameChange = { workshopName = it },
        serviceType = serviceType,
        onServiceTypeChange = { serviceType = it; warningServiceOrigin = null },
        serviceDate = serviceDate,
        onServiceDateChange = { serviceDate = it },
        serviceNotes = serviceNotes,
        onServiceNotesChange = { serviceNotes = it },
        enabled = canShare,
        isBusy = isBusy,
        onSearch = {
            if (!isParked) return@KnowledgeAndServicePanel
            coroutineScope.launch {
                knowledgeLoading = true
                knowledgeError = null
                val response = backend.searchKnowledge(variant, searchQuery.trim())
                knowledge = response.getOrNull().orEmpty()
                knowledgeError = response.exceptionOrNull()?.message
                if (response.isSuccess && knowledge.isEmpty()) knowledgeError = "등록된 근거가 없습니다."
                knowledgeLoading = false
            }
        },
        onRequestService = {
            selectedConfirm = ConfirmAction.SubmitServiceRequest(captureServiceRequestApproval())
        },
    )

    if (activeSection == SettingsSection.SERVICE) {
        androidx.compose.runtime.key(variant,identity?.installationId) {
            BookingStatusPanel(backend,variant,bookingRevision,
                enabled = isParked && !drivingUx.restricted && hasInstall && backend.isConfigured && !isBusy)
        }
    }

    if (activeSection == SettingsSection.SERVICE && action == null) {
        SettingsPanel(title = "현대 공식 서비스 연결") {
            SettingsStatusLine("경고 이해 → 증상 정리 → 현대 공식 서비스", true)
            Text("야호는 차량 경고를 풀어 설명하고 상담 전 증상을 정리해 드려요. 상담과 예약은 현대 공식 채널에서 이어가요.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                if (!openHyundaiServiceNetwork()) Toast.makeText(appContext, "이 차량에서 서비스 안내 페이지를 열 수 없습니다. hyundai.com → 고객지원 → 서비스 네트워크를 이용해 주세요.", Toast.LENGTH_LONG).show()
            }, enabled = isParked && settingsAllowed) { Text("현대 공식 정비 안내 열기") }
        }
    }

    if (activeSection == SettingsSection.ROAD && action == null) {
        SettingsDetails("네트워크 연결 상세", if (hasInstall) "차량 설치 연결됨" else "차량 설치 연결 필요") {
        InstallationPanel(
            variant = variant,
            hasInstall = hasInstall,
            installationId = identity?.installationId,
            backendConfigured = backend.isConfigured,
            configurationMessage = backend.configurationMessage,
            canProvision = backend.canProvisionLocalInstallation && isParked,
            connectionMessage = connectionMessage,
            onProvision = {
                if (!isParked || isBusy) return@InstallationPanel
                coroutineScope.launch {
                    isBusy = true
                    connectionMessage = null
                    val response = backend.provisionLocalInstallation(variant)
                    connectionMessage = response.text
                    if (response.success) {
                        installationRevision += 1
                        feedRevision += 1
                    }
                    isBusy = false
                }
            },
            isBusy = isBusy,
        )
        }
    }

    resultMessage?.let { InfoStrip(text = it, tone = if (it.contains("실패") || it.contains("확인할 수 없습니다")) Coral else Mint) }
    }

    Surface(color = Page, modifier = Modifier.fillMaxSize().blur(if (!appConsents.reviewed) 22.dp else 0.dp)) {
        Box(Modifier.fillMaxSize()) {
        if (showDrivingFace) {
            GlassBackdrop()
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val narrowWindow = maxWidth < 950.dp
                if (turnPreviewVisible && turnPreviewMode == TurnPreviewMode.MAP) {
                    DrivingRouteMap(Modifier.fillMaxSize())
                } else {
                    InteractiveCompanion(
                        modifier = if (narrowWindow) Modifier.align(Alignment.TopCenter).padding(top = 92.dp).fillMaxSize(.64f)
                            else Modifier.fillMaxSize(.94f),
                        touchEnabled = isParked || (inDrive && canTouchDrivingCompanion),

                        onTalk = if (canOpenHomeDialog) ({
                            if (showTalk || homeReply != null) { showTalk = false; homeReply = null }
                            else showTalk = true
                        }) else null,
                        talking = canOpenHomeDialog && (showTalk || homeReply != null),
                    )
                }
                CompanionQuickRail(
                    actions = listOf(
                        CompanionShortcut("정비·예약", "요청과 진행 상태", "calendar", enabled = canOpenHomeDialog) {
                            if (!isBusy && pendingGleoAction.value == null) phoneBooking = false
                            homeAction = HomeAction.SERVICE
                        },
                        CompanionShortcut("AI 전화", "정비소 문의 요청", "phone",
                            enabled = canOpenHomeDialog && !isBusy && pendingGleoAction.value == null) {
                            phoneBooking = true
                            homeAction = HomeAction.CALL
                        },
                        CompanionShortcut("주변 도로", "도로 정보 확인", "road", enabled = canOpenHomeDialog) {
                            homeAction = HomeAction.ROAD
                        },
                        CompanionShortcut("창문·와이퍼", if (inlineControlsVisible) "옵션 접기" else "차량 조작", "window", enabled = canOpenHomeDialog) {
                            homeControlsOpen = !homeControlsOpen
                        },
                    ),
                    modifier = (if (narrowWindow) Modifier.align(Alignment.BottomStart)
                        .padding(start = 28.dp, bottom = 125.dp).width(if (inlineControlsVisible) 350.dp else 268.dp)
                    else Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                        .width(if (inlineControlsVisible) 350.dp else 268.dp))
                        .alpha(if (warningOnHome) .5f else 1f),
                    expandedContent = if (inlineControlsVisible) ({
                        Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                            CompanionVehicleActions(manualControls, canControlVehicle, controlBlockedReason,
                                onRequestSpeedPermission, inline = true)
                        }
                    }) else null,
                )
                homeReply?.let { (_, reply) ->
                    GlassCard(Modifier.align(Alignment.TopCenter)
                        .padding(top = if (narrowWindow) 128.dp else 52.dp)
                        .widthIn(max = 520.dp)) {
                        Text(reply, Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                            color = Ink, fontSize = 25.sp, lineHeight = 33.sp)
                    }
                }
                SindangWeatherBadge(
                    backend,
                    Modifier.align(Alignment.TopStart).padding(28.dp),
                )
                QuickReportButton(
                    modifier = Modifier.align(Alignment.TopEnd).padding(28.dp),
                    enabled = canOpenHomeDialog && pendingGleoAction.value == null,
                    onClick = {
                        activeVariant.value = ExperimentVariant.A
                        homeAction = HomeAction.REPORT
                    },
                )
                if (turnPreviewVisible && turnPreviewMode == TurnPreviewMode.SDK) {
                    DrivingTurnPreview(
                        navigationTurn,
                        Modifier.align(Alignment.TopEnd).padding(top = 128.dp, end = 28.dp).widthIn(max = 470.dp),
                    )
                }
                SettingsCornerButton(isOpen = false, enabled = settingsAllowed) {
                    openSettings(SettingsSection.VEHICLE)
                }


                HomeVehicleCard(homeNotice, homeVehicle, isParked && settingsAllowed, canOpenHomeDialog,
                    { (homeNotice as? ai.yaho.hyundai.core.HomeNotice.Warning)?.let { inspection = it } },
                    canOpenHomeDialog, { showHomeVehicle = true },
                    Modifier.align(Alignment.BottomEnd).padding(28.dp).width(if (narrowWindow) 360.dp else 400.dp),
                    headerAction = if (demoWarning.available && isParked) ({ DemoWarningButton(demoWarning) }) else null)

                rainGuidance.banner?.takeIf { homeNotice is ai.yaho.hyundai.core.HomeNotice.Idle }?.let { text ->
                    val rainSlot = if (narrowWindow) Modifier.align(Alignment.TopCenter).padding(top = 110.dp).widthIn(max = 560.dp)
                        else Modifier.align(Alignment.TopEnd).padding(top = 220.dp, end = 28.dp).width(500.dp)
                    if (canOpenHomeDialog) RainNoticeCard(text, rainGuidance.canRead, rainGuidance.onRead, rainGuidance.onDismiss, rainSlot)
                    else GlassCard(rainSlot) { Text(text, Modifier.padding(16.dp), color = Ink, fontSize = 22.sp, lineHeight = 29.sp) }
                }
                if (canOpenHomeDialog && showTalk) {
                    CompanionTalkComposer(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = if (narrowWindow) 108.dp else 36.dp)
                            .padding(horizontal = 38.dp).widthIn(max = 660.dp).fillMaxWidth(),
                        onDismiss = { showTalk = false },
                        onQuestion = { question ->
                            val answer = when {
                                listOf("정비", "예약", "전화").any { it in question } ->
                                    "정비·예약에서 요청 내용을 확인하고 보낼 수 있어요."
                                listOf("공기압", "타이어").any { it in question } ->
                                    "공기압 수치는 계기판에서 확인해 주세요."
                                listOf("고장코드", "고장 코드", "DTC").any { it in question } ->
                                    "고장코드 자동 수신은 아직 연결 전이에요. 설정의 고장코드 사전에서 알고 있는 코드를 찾아볼 수 있어요."
                                listOf("내 차", "내차", "차량", "배터리", "연료", "경고").any { it in question } ->
                                    homeVehicle.summary
                                listOf("도로", "앞 구간", "비 와", "비와", "노면", "블랙아이스").any { it in question } ->
                                    priorityNotice?.takeIf { !it.key.startsWith("vehicle:") }?.text
                                        ?: "지금 확인된 주변 도로 안내가 없어요."
                                else -> "야호 OS 대화 연결을 준비 중이에요. 지금은 내 차 상태나 주변 도로를 물어볼 수 있어요."
                            }
                            showTalk = false
                            homeReply = android.os.SystemClock.elapsedRealtime() to answer
                        },
                    )
                }
            }
        } else {
            SettingsLayout(section, { section = it }, { settingsOpen = false }) {
                settingsContent(section, null)

            }
        }
        }
    }

    homeAction?.takeIf { canOpenHomeDialog }?.let { action ->
        HomeActionDialog(action.title, onDismiss = { homeAction = null }) {
            settingsContent(action.section, action)
        }
    }
    inspection?.takeIf { canInspect }?.let { opened ->
        val live = (homeNotice as? ai.yaho.hyundai.core.HomeNotice.Warning)
            ?.takeIf { it.warning.key == opened.warning.key && it.source == opened.source }
        val shown = live ?: opened
        HomeActionDialog("점검 준비 · ${shown.warning.guidance.title}", onDismiss = { inspection = null }) {
            WarningInspectionContent(
                warning = shown.warning, source = shown.source, signalCurrent = live != null,
                selected = inspectionSymptoms["${shown.source}:${shown.warning.key}"].orEmpty(),
                onToggle = { id -> val k = "${shown.source}:${shown.warning.key}"
                    inspectionSymptoms[k] = ai.yaho.hyundai.core.toggleSymptom(inspectionSymptoms[k].orEmpty(), id) },
                onOpenOfficial = ::openHyundaiServiceNetwork,
            )
        }
    }
    if (canOpenHomeDialog && showHomeVehicle) {
        HomeActionDialog("내 차 상태", onDismiss = { showHomeVehicle = false }) {
            SettingsPanel {
                homeVehicle.energy.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Text("타이어 공기압 · 미확인 (계기판에서 확인)")
                Text(homeVehicle.warning)
                Text("고장코드 자동 수신 · 미연동", color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
                warningAssessment.active.firstOrNull()?.let { Text(it.guidance.title) }
                TextButton(onClick = {
                    showHomeVehicle = false
                    openSettings(SettingsSection.DIAGNOSTICS)
                }) { Text("경고 및 고장코드 보기") }
            }
        }
    }
    InitialConsentDialog(appConsents, !drivingUx.restricted, saveAppConsents)

    selectedConfirm?.takeIf { isParked }?.let { action ->
        val serviceAction = action as? ConfirmAction.SubmitServiceRequest
        val serviceReview = serviceAction?.approval?.review
        ConfirmationDialog(
            action = action,
            variant = serviceReview?.variant ?: variant,
            kind = reportKind,
            vehicle = serviceAction?.approval?.vehicle ?: vehicle,
            serviceType = serviceReview?.serviceType ?: serviceType,
            serviceDate = serviceReview?.requestedDateInput ?: serviceDate,
            serviceNotes = if (serviceReview != null) serviceReview.notes.orEmpty() else
                ai.yaho.hyundai.core.warningServiceNotes(serviceNotes, warningServiceOrigin, warningAssessment, appConsents.vehicleRead && sdkSnapshot.active),
            feedItem = (action as? ConfirmAction.EventFeedback)?.item,
            busy = isBusy,
            onDismiss = { if (!isBusy) selectedConfirm = null },
            onConfirm = {
                if (!isBusy && !(serviceAction != null && serviceSubmissionStarted)) {
                    if (serviceAction != null && !serviceRequestApprovalIsCurrent(serviceAction.approval)) {
                        selectedConfirm = null
                        resultMessage = "요청 내용이나 차량 정보가 바뀌었습니다. 최신 내용을 다시 검토하고 승인해 주세요."
                    } else {
                        if (serviceAction != null) serviceSubmissionStarted = true
                        selectedConfirm = null
                        coroutineScope.launch {
                            try {
                                execute(action)
                            } finally {
                                if (serviceAction != null) serviceSubmissionStarted = false
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun QuickReportButton(modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.alpha(if (enabled) 1f else .62f).clip(CircleShape)
        .background(Color(0xFFFFE2CB).copy(alpha = .62f))
        .border(1.dp, Color(0xFFF5D5BA), CircleShape)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).widthIn(min = 142.dp)
        .padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text("빠른 제보", color = Mint, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VehiclePanel(
    mode: DrivingMode,
    vehicle: ai.yaho.hyundai.vehicle.VehicleContext,
    vehicleSdkStatus: VehicleSdkStatus,
    actualVehicleState: AdapterState,
    actualVehicleName: String,
    navigationState: AdapterState,
    navigationName: String,
    onModeChanged: (DrivingMode) -> Unit,
) {
    SettingsPanel(title = "차량 상태", category = "SDK 관측과 샘플") {
        Text("와이퍼 ${vehicleSdkStatus.wiper} · 브레이크 ${vehicleSdkStatus.brake}", style = MaterialTheme.typography.bodyMedium)
        SettingsFinePrint { Text("SDK 읽기 전용 · 별도 동의 시 중앙 네트워크 공유. 와이퍼 작동과 브레이크 입력만으로 강우나 미끄러짐을 확정할 수 없습니다.", style = MaterialTheme.typography.bodySmall) }
        val doorPositionLabel = when {
            vehicleSdkStatus.doorPosition == null -> "읽기 대기"
            vehicleSdkStatus.minimumDoorPosition == 0 && vehicleSdkStatus.maximumDoorPosition == 1 && vehicleSdkStatus.doorPosition == 0 -> "닫힘 (0)"
            vehicleSdkStatus.minimumDoorPosition == 0 && vehicleSdkStatus.maximumDoorPosition == 1 && vehicleSdkStatus.doorPosition == 1 -> "열림 (1)"
            else -> vehicleSdkStatus.doorPosition.toString()
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .42f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("실시간 SDK · 운전석 앞문", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("읽기 전용 · ROW_1_LEFT", color = Muted, fontSize = 24.sp)
            }
            Text(doorPositionLabel, color = if (vehicleSdkStatus.doorPosition == null) Muted else Mint, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardRaised).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(modifier = Modifier.size(57.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFFFE7D3)), contentAlignment = Alignment.Center) {
                Text("I5", color = Mint, fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("IONIQ 5 · 샘플 차량", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("${vehicle.areaLabel ?: "위치 미제공"}  ·  배터리 ${vehicle.batteryPercent ?: "—"}%", color = Muted, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(
                label = when (mode) {
                    DrivingMode.PARKED -> "주차"
                    DrivingMode.MOVING -> "P 아님 · 조작 제한"
                    DrivingMode.UNKNOWN -> "상태 미상"
                },
                color = if (mode == DrivingMode.PARKED) Mint else Amber,
            )
        }
        Text("주행 상태 자동 확인 · ${vehicleSdkStatus.gear ?: "기어 신호 미제공"} · P 이외의 상태에서는 긴 조작을 제한합니다.", color = Muted, fontSize = 26.sp)
        if (mode != DrivingMode.PARKED) {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3DC)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("잠금", color = Amber, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                Text("제보·지식 검색·정비 요청은 주차 후 이용할 수 있습니다.", color = Color(0xFF6B4816), fontSize = 24.sp, lineHeight = 36.sp)
            }
        }
        HorizontalDivider(color = Line)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            AdapterLine(actualVehicleName, actualVehicleState, Modifier.weight(1f))
            AdapterLine(navigationName, navigationState, Modifier.weight(1f))
        }
        Text(
            "차량 카드는 SDK 관측과 명시된 샘플을 구분합니다. 조회·위치·버튼 조작·네트워크 공유는 시작 안내와 설정의 동의를 따릅니다. 위치 조회에 동의하면 내비 연결 후 수신을 확인하며, 경로 변경은 아직 실행하지 않습니다.",
            color = Muted,
            fontSize = 24.sp,
            lineHeight = 36.sp,
        )
    }
}

@Composable
private fun AdapterLine(name: String, state: AdapterState, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, color = Muted, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when (state) {
                AdapterState.NOT_CONFIGURED -> "미연결 · NOT_CONFIGURED"
                AdapterState.CONNECTING -> "차량 상태 읽는 중"
                AdapterState.UNAVAILABLE -> "사용할 수 없음"
                AdapterState.CONNECTED -> "연결됨"
            },
            color = if (state == AdapterState.CONNECTED) Mint else Amber,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun VariantPicker(selected: ExperimentVariant, enabled: Boolean, onSelect: (ExperimentVariant) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
        .background(Color(0xFFEFE5DB)).padding(5.dp)) {
        ExperimentVariant.entries.forEach { variant ->
            val active = variant == selected
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable(enabled = enabled) { onSelect(variant) },
                shape = RoundedCornerShape(13.dp),
                color = if (active) Color.White else Color.Transparent,
                shadowElevation = if (active) 2.dp else 0.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(variant.label, color = if (active) Mint else Muted, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(variant.subtitle, color = Muted, fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun GleoRequestCard(
    action: PendingGleoAction,
    isParked: Boolean,
    currentVariant: ExperimentVariant,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSwitchVariant: () -> Unit,
) {
    PanelCard(eyebrow = "GLEO 요청", title = "앱에서 확인이 필요합니다", accent = Mint) {
        val description = when (val command = action.command) {
            is GleoCommand.ReportRoadEvent -> "${roadKindTitle(command.kind)} 제보가 준비됐습니다."
            is GleoCommand.RequestServiceBooking -> "${command.serviceType} 정비 요청이 준비됐습니다."
            is GleoCommand.GetDrivingContext -> "현재 운행 맥락을 확인했습니다."
        }
        Text(description, color = Ink, fontSize = 28.sp)
        Text(
            if (isParked) "전송 전 내용을 확인하고 버튼을 직접 눌러 주세요." else "시뮬레이터가 주행 중입니다. 주차 상태로 바꾼 뒤 계속해 주세요.",
            color = Muted,
            fontSize = 26.sp,
            lineHeight = 36.sp,
        )
        Text("요청 구성 ${action.variant.label} · 현재 ${currentVariant.label}", color = Muted, fontSize = 24.sp)
        if (action.command is GleoCommand.RequestServiceBooking || action.command is GleoCommand.ReportRoadEvent) {
            if (action.variant == currentVariant) {
                OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("요청 준비 취소")
                }
                Text("앱의 Gleo 대기 요청만 해제합니다. 이미 서버에 접수된 요청 상태는 바꾸지 않습니다.", color = Muted, fontSize = 24.sp)
            } else {
                OutlinedButton(onClick = onSwitchVariant, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("${action.variant.label} 구성으로 전환")
                }
            }
        }
    }
}

@Composable
private fun ReportPanel(
    kind: String,
    onKindChange: (String) -> Unit,
    startVoice: Boolean,
    shareConsent: Boolean,
    consentGranted: Boolean,
    canShare: Boolean,
    isBusy: Boolean,
    onReport: () -> Unit,
) {
    var manualChoice by remember { mutableIntStateOf(0) }
    var hasChosen by remember { mutableStateOf(!startVoice) }
    SettingsPanel(title = "도로 상황 선택") {
        VoiceInput("예: 앞에 사고가 났어요", autoStart = startVoice, cancelSignal = manualChoice) { speech ->
            val matched = ai.yaho.hyundai.core.roadReportKindFromSpeech(speech)
            if (matched == null) "제보 유형을 직접 선택하거나 다시 말해 주세요."
            else {
                hasChosen = true
                onKindChange(matched)
                "${roadKindTitle(matched)} 선택됨 · 내용을 확인해 주세요."
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "road_hazard" to "도로 위험물",
                "stalled_vehicle" to "정차 차량",
                "traffic" to "정체",
                "slippery_report" to "미끄러움",
                "rain_report" to "강우",
                "accident_report" to "사고 목격",
                "other" to "기타",
            ).forEach { (code, label) ->
                FilterChip(
                    selected = hasChosen && kind == code,
                    onClick = { manualChoice++; hasChosen = true; onKindChange(code) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 64.dp),
                    enabled = !isBusy,
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFE6CE), selectedLabelColor = Mint,
                    ),
                )
            }
        }
        HorizontalDivider()
        Text(if (shareConsent) "확인 후 위치·상황·발생 시각을 공유합니다."
            else "정보 공유가 꺼져 있어요. 권한에서 차량 네트워크 공유를 켜 주세요.",
            color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onReport,
            enabled = hasChosen && canShare && shareConsent && !isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color.White),
        ) {
            if (isBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Text(if (consentGranted) "제보 내용 확인" else "동의 및 제보 검토")
        }
    }
}

@Composable
private fun OwnerReportPanel(
    report: OwnerReportState?,
    isEnabled: Boolean,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
) {
    PanelCard(eyebrow = "A · 제출 후 재확인", title = "내가 보낸 제보", accent = Amber) {
        if (report == null) {
            Text("제보를 제출하면 여기에서 본인 제보를 다시 확인하거나 철회할 수 있습니다.", color = Muted, fontSize = 26.sp, lineHeight = 36.sp)
        } else {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${roadKindTitle(report.status)} · 내 제보", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("제출 후 직접 확인할 수 있습니다.", color = Muted, fontSize = 24.sp)
                }
                StatusPill(report.status.uppercase(), if (report.status == "clear") Muted else Mint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onConfirm, enabled = isEnabled && !isBusy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("다시 확인", fontSize = 26.sp)
                }
                OutlinedButton(onClick = onClear, enabled = isEnabled && !isBusy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF73514C))) {
                    Text("제보 철회", color = Coral, fontSize = 26.sp)
                }
            }
        }
    }
}

@Composable
private fun FeedPanel(
    items: List<RoadFeedItem>,
    error: String?,
    loading: Boolean,
    isSample: Boolean,
    isEnabled: Boolean,
    onRefresh: () -> Unit,
    onFeedback: (RoadFeedItem, String) -> Unit,
) {
    PanelCard(eyebrow = "B · 주변 제보 확인", title = "주변 도로 피드", accent = Amber) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSample) "시뮬레이터 샘플 · 구역 단위 집계" else "구역 단위 집계", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = isEnabled && !loading) { Text("새로고침", color = Mint, fontSize = 24.sp) }
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Mint)
                Text("피드를 불러오는 중", color = Muted, fontSize = 24.sp)
            }
            error != null && items.isEmpty() -> Text("피드를 불러오지 못했습니다: $error", color = Muted, fontSize = 26.sp, lineHeight = 36.sp)
            items.isEmpty() -> Text("현재 표시할 주변 제보가 없습니다.", color = Muted, fontSize = 26.sp)
            else -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items.take(8).forEach { item ->
                    FeedEventCard(item, isEnabled, onFeedback)
                }
            }
        }
        Text("피드에는 개인 제보 ID나 이동 경로가 표시되지 않습니다.", color = Muted, fontSize = 24.sp)
    }
}

@Composable
private fun ConsentPanel(
    shareConsent: Boolean,
    consentGranted: Boolean,
    canToggle: Boolean,
    onConsentChange: (Boolean) -> Unit,
) {
    PanelCard(eyebrow = "B · 동의", title = "피드 의견 공유 설정", accent = Mint) {
        Text(
            "동의한 도로 제보와 확인·해소 의견은 이 설치와 연결해 저장됩니다. 철회하면 이 구성의 관련 기록이 삭제됩니다. 정비 요청은 별도 확인 창에서 승인합니다.",
            color = Muted,
            fontSize = 24.sp,
            lineHeight = 36.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(9.dp),
        ) {
            Text(
                if (shareConsent) "시작 안내의 정보 공유 동의를 사용합니다. 설정에서 해제할 수 있습니다." else "정보 공유가 꺼져 있습니다. 설정에서 변경할 수 있습니다.",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FeedEventCard(item: RoadFeedItem, isEnabled: Boolean, onFeedback: (RoadFeedItem, String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(CardRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(roadKindTitle(item.kind), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("${item.precisionMeters.coerceAtLeast(300)}m 단위 구역${item.direction?.let { " · $it" }.orEmpty()}", color = Muted, fontSize = 24.sp)
            }
            StatusPill(item.status, if (item.status == "active") Mint else Amber)
        }
        Text("${item.reportCount}건 · 참여 ${item.participantCount}곳", color = Muted, fontSize = 22.sp)
        SettingsFinePrint {
            Text("출처: 이용자 ${item.userSourceCount} · 시뮬레이터 ${item.simulatorSourceCount}", style = MaterialTheme.typography.bodySmall)
            if (item.timeWindowStart.isNotBlank()) Text("집계 시작 ${item.timeWindowStart.take(16).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall)
            if (item.expiresAt.isNotBlank()) Text("피드 만료 ${item.expiresAt.take(16).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onFeedback(item, "confirm") }, enabled = isEnabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("같은 상황 확인", fontSize = 24.sp)
            }
            OutlinedButton(onClick = { onFeedback(item, "clear") }, enabled = isEnabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("해소됨", color = Amber, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun KnowledgeAndServicePanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<KnowledgeResult>,
    error: String?,
    loading: Boolean,
    phoneBooking: Boolean,
    onBookingModeChange: (Boolean) -> Unit,
    canChangeBookingMode: Boolean,
    workshopName: String,
    onWorkshopNameChange: (String) -> Unit,
    serviceType: String,
    onServiceTypeChange: (String) -> Unit,
    serviceDate: String,
    onServiceDateChange: (String) -> Unit,
    serviceNotes: String,
    onServiceNotesChange: (String) -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    onSearch: () -> Unit,
    onRequestService: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPanel(title = if (phoneBooking) "AI 전화 요청" else "정비 요청") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = !phoneBooking, enabled = canChangeBookingMode && !isBusy,
                onClick = { onBookingModeChange(false) }, label = { Text("일반 요청") })
            FilterChip(selected = phoneBooking, enabled = canChangeBookingMode && !isBusy,
                onClick = { onBookingModeChange(true) }, label = { Text("AI 전화") })
        }
        if (phoneBooking) {
            Text("전화 연동 대기 중 · 요청만 저장돼요", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = workshopName,
                onValueChange = { if (it.length <= 100 && it.none(Char::isISOControl)) onWorkshopNameChange(it) },
                enabled = enabled && !isBusy,
                label = { Text("정비소 이름 · 지점명") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
            )
        } else {
            Text("요청을 보내도 예약이 자동 확정되지는 않습니다.", color = Muted, fontSize = 24.sp)
        }
        OutlinedTextField(
            value = serviceType,
            onValueChange = onServiceTypeChange,
            enabled = enabled && !isBusy,
            label = { Text("정비 항목") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
        )
        OutlinedTextField(
            value = serviceDate,
            onValueChange = onServiceDateChange,
            enabled = enabled && !isBusy,
            label = { Text("희망 일시") },
            supportingText = { Text(ai.yaho.hyundai.core.formatKoreanDateTime(serviceDate)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
        )
        OutlinedTextField(
            value = serviceNotes,
            onValueChange = onServiceNotesChange,
            enabled = enabled && !isBusy,
            label = { Text("추가 메모 (선택)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(13.dp),
        )
        Button(onClick = onRequestService,
            enabled = enabled && !isBusy && serviceType.isNotBlank() && serviceDate.isNotBlank() && (!phoneBooking || workshopName.isNotBlank()),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (phoneBooking) "AI 전화 요청 검토" else "정비 요청 검토", fontSize = 26.sp)
        }
    }
    if (!phoneBooking) SettingsDetails("점검 지식 검색", "경고등 의미와 점검 방법") {
        SettingsPanel(title = "경고·점검 안내 검색") {
            Text("승인된 지식 서버 검색 · 내 차 상태를 읽은 결과가 아니에요", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled && !isBusy,
                label = { Text("차량 경고나 점검 항목") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
            )
            Button(onClick = onSearch, enabled = enabled && !loading && !isBusy && query.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (loading) "검색 중…" else "근거 안내 검색", fontSize = 26.sp)
            }
            if (error != null) Text(error, color = if (error.contains("등록된")) Muted else Coral, fontSize = 24.sp)
            if (results.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.take(5).forEach { result ->
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(CardRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(result.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                            Text(result.excerpt, color = Ink, fontSize = 24.sp, lineHeight = 36.sp)
                            Text("${result.sourceTitle} · ${result.publisher}", color = Mint, fontSize = 24.sp)
                            if (result.approvedAt.isNotBlank()) Text("승인 근거 · ${result.approvedAt.take(10)}", color = Muted, fontSize = 24.sp)
                            if (result.url.startsWith("https://")) {
                                Text(
                                    result.url,
                                    color = Muted,
                                    fontSize = 24.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = enabled) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallationPanel(
    variant: ExperimentVariant,
    hasInstall: Boolean,
    installationId: String?,
    backendConfigured: Boolean,
    configurationMessage: String,
    canProvision: Boolean,
    connectionMessage: String?,
    onProvision: () -> Unit,
    isBusy: Boolean,
) {
    PanelCard(eyebrow = "보안 연결", title = "${variant.label} 설치 연결", accent = Mint) {
        Text(
            when {
                hasInstall -> "이 구성 전용 설치가 연결되었습니다. A와 B는 별도 동의와 토큰을 사용합니다."
                BuildConfig.DEBUG && canProvision -> "디버그 로컬 서버에 이 구성 전용 설치를 만들 수 있습니다. 토큰은 Android Keystore로 암호화됩니다."
                BuildConfig.DEBUG -> configurationMessage.ifBlank { "로컬 HTTPS 서버에 pairing이 필요합니다." }
                else -> "계정 연결을 준비 중입니다. 현재는 화면과 차량 상태를 확인할 수 있습니다."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (BuildConfig.DEBUG && canProvision && !hasInstall) {
            Button(onClick = onProvision, enabled = !isBusy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("${variant.label} 로컬 테스트 연결", fontSize = 26.sp)
            }
        }
        if (hasInstall && installationId != null && BuildConfig.DEBUG) {
            Text("설치 ID ${installationId.take(8)}… · 토큰은 화면에 표시되지 않습니다.", style = MaterialTheme.typography.labelMedium)
        }
        if (connectionMessage != null) Text(connectionMessage, color = if (connectionMessage.contains("연결되었습니다")) Mint else Coral, fontSize = 24.sp)
    }
}

@Composable
private fun ConfirmationDialog(
    action: ConfirmAction,
    variant: ExperimentVariant,
    kind: String,
    vehicle: ai.yaho.hyundai.vehicle.VehicleContext,
    serviceType: String,
    serviceDate: String,
    serviceNotes: String,
    feedItem: RoadFeedItem?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title: String
    val body: String
    val confirmLabel: String
    when (action) {
        ConfirmAction.SubmitReport -> {
            title = "도로 제보를 전송할까요?"
            body = "제보 종류 ${roadKindTitle(kind)}\n정확한 위치 ${vehicle.latitude}, ${vehicle.longitude}\n발생 시각과 방향 정보도 함께 전송합니다.\n\n${variant.label} · 동의 후 전송됩니다."
            confirmLabel = "확인하고 전송"
        }
        is ConfirmAction.SubmitServiceRequest -> {
            val workshop = action.approval.review.workshopName
            if (workshop != null) {
                title = "AI 전화 요청을 저장할까요?"
                body = "정비소 $workshop\n정비 항목 $serviceType\n희망 일시 ${ai.yaho.hyundai.core.formatKoreanDateTime(serviceDate)}\n메모 ${ai.yaho.hyundai.core.dedupeSentences(serviceNotes).ifBlank { "없음" }}\n\n위치 좌표 없이 요청을 저장합니다. 현재 전화 연동 대기 중입니다."
                confirmLabel = "확인하고 요청 저장"
            } else {
                title = "정비 요청을 보낼까요?"
                body = "$serviceType\n희망 일시 ${ai.yaho.hyundai.core.formatKoreanDateTime(serviceDate)}\n설치 구성 ${variant.label}\n메모 ${ai.yaho.hyundai.core.dedupeSentences(serviceNotes).ifBlank { "없음" }}\n위치 ${vehicle.latitude}, ${vehicle.longitude} · ${if(vehicle.isSample) "샘플 위치" else "위치 관측"}\n\n요청만 전송됩니다. 정비소 제공자가 설정되지 않았거나 응답하기 전에는 예약이 확정되지 않습니다."
                confirmLabel = "요청 보내기"
            }
        }
        is ConfirmAction.EventFeedback -> {
            title = if (action.state == "confirm") "같은 상황을 확인할까요?" else "해소된 상황으로 표시할까요?"
            val sourceMessage = if (vehicle.isSample) {
                "시뮬레이터 표본이므로 이 의견도 시뮬레이터 출처로 전송됩니다. 실제 이용자 관측으로 표시되지 않습니다."
            } else {
                "이 의견은 실제 이용자가 관측한 도로 상황으로 전송됩니다."
            }
            body = "${roadKindTitle(action.item.kind)} · ${action.item.precisionMeters}m 단위 집계 구역\n참여 설치 ${action.item.participantCount}곳 · ${action.item.reportCount}건\n${if (action.state == "confirm") "확인" else "해소"} 의견을 추가합니다.\n\n$sourceMessage\n개별 제보자나 원본 제보 ID는 공유되지 않습니다."
            confirmLabel = "의견 전송"
        }
        is ConfirmAction.OwnerReport -> {
            title = if (action.state == "confirm") "내 제보를 다시 확인할까요?" else "내 제보를 철회할까요?"
            body = "선택한 ${variant.label}의 최근 제보를 ${if (action.state == "confirm") "다시 확인" else "철회"}합니다. 이 작업은 해당 설치 계정으로 전송됩니다."
            confirmLabel = if (action.state == "confirm") "다시 확인" else "제보 철회"
        }
        ConfirmAction.WithdrawConsent -> {
            title = "공유 동의를 철회할까요?"
            body = "${variant.label} 설치에서 동의를 철회합니다. 서버에 저장된 이 구성의 제보와 의견도 삭제되고, 이후 제보·피드 의견은 차단됩니다."
            confirmLabel = "동의 철회"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = Ink,
        textContentColor = Ink,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { ReviewDialogBody(body) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.heightIn(min = 64.dp), shape = RoundedCornerShape(10.dp)) {
                Text(if (busy) "처리 중…" else confirmLabel, fontSize = 26.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 64.dp)) { Text("취소", color = Muted) } },
    )
}

@Composable
private fun PanelCard(eyebrow: String, title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    SettingsPanel(title = title, category = eyebrow, accent = accent, content = content)
}

@Composable
private fun InfoStrip(text: String, tone: Color, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(tone.copy(alpha = 0.12f)).border(1.dp, tone.copy(alpha = 0.25f), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = tone, fontSize = 24.sp, lineHeight = 36.sp, modifier = Modifier.weight(1f))
        if (onDismiss != null) TextButton(onClick = onDismiss) { Text("닫기", color = tone, fontSize = 24.sp) }
    }
}

private fun roadKindTitle(kind: String): String = when (kind) {
    "stalled_vehicle" -> "정차 차량"
    "road_hazard" -> "도로 위험"
    "traffic" -> "교통 정체"
    "slippery_report" -> "미끄러움 제보 · 결빙 미확정"
    "rain_report" -> "강우 제보 · 기상 확정 아님"
    "accident_report" -> "사고 목격 제보"
    "submitted" -> "제출됨"
    "confirm" -> "확인됨"
    "clear" -> "철회됨"
    else -> "기타 도로 상황"
}

private fun defaultServiceDate(): String = runCatching {
    val seoul = ZoneId.of("Asia/Seoul")
    LocalDate.now(seoul).plusDays(1).atTime(9, 0).atZone(seoul).toOffsetDateTime().toString()
}.getOrDefault("")
