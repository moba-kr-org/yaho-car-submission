package ai.yaho.hyundai

import android.app.Instrumentation
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.bookingStatusDisplay
import ai.yaho.hyundai.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URI


internal fun Instrumentation.runBookingStatusProbe(): JSONObject = runBlocking {
    val uri = URI(BuildConfig.API_BASE_URL)
    check(BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("10.0.2.2","127.0.0.1","localhost"))
    val before = AppConsentStore(targetContext).read()
    val credentials = DebugInstallationCredentialStore(targetContext)
    val backend = BackendApi(credentials,IdempotencyKeyStore(targetContext))
    val variants = ExperimentVariant.entries.filter { credentials.credentialsFor(it) != null }
    check(variants.isNotEmpty())
    val results = variants.map { backend.fetchBookings(it) }
    val fixture = JSONObject().put("bookingId","11111111-1111-4111-8111-111111111111")
        .put("serviceType","fixture").put("requestedAt","2026-11-06T00:00:00Z").put("status","unknown")
        .put("cancellationRequestedAt","2026-11-05T00:00:00Z").put("updatedAt","2026-11-05T00:00:00Z")
    val parsed = parseBookingStatus(fixture)
    val display = bookingStatusDisplay(parsed.status,parsed.cancellationRequested)
    val fixtureId = java.util.UUID.randomUUID().toString()
    val fixtureOperation = "booking_$fixtureId"
    val store = IdempotencyKeyStore(targetContext)
    var pendingBlocked = false
    var pendingRestored = false
    var staleReceiptProtected = false
    try {
        val saved = store.reserve(ExperimentVariant.A,fixtureOperation,"isolated fixture")
        pendingRestored = IdempotencyKeyStore(targetContext).pending(ExperimentVariant.A,fixtureOperation)?.key == saved.key
        val fixtureCredentials = object : InstallationCredentialProvider {
            override fun credentialsFor(variant: ExperimentVariant) = InstallationIdentity(fixtureId,"test-not-a-real-token")
        }
        val guarded = BackendApi(fixtureCredentials,store).requestService(ExperimentVariant.A,
            ai.yaho.hyundai.core.GleoCommand.RequestServiceBooking("fixture","2026-11-06",null),
            ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter(ai.yaho.hyundai.core.DrivingMode.PARKED).readContext(),true)
        pendingBlocked = !guarded.success && guarded.recordId == null && guarded.text.contains("이전 정비 요청")
        store.resolveIfMatches(ExperimentVariant.A,fixtureOperation,saved.key)
        val newer=store.reserve(ExperimentVariant.A,fixtureOperation,"new isolated fixture")
        staleReceiptProtected = !IdempotencyKeyStore(targetContext).resolveIfMatches(ExperimentVariant.A,fixtureOperation,saved.key) &&
            store.pending(ExperimentVariant.A,fixtureOperation)?.key == newer.key
    } finally { store.resolve(ExperimentVariant.A,fixtureOperation) }
    val choicesUnchanged = before == AppConsentStore(targetContext).read()
    JSONObject().put("test","booking_readonly_v1").put("installationsQueried",variants.size)
        .put("httpListsParsed",results.count { it.isSuccess }).put("boundedLists",results.all { it.getOrNull()?.size?.let { size -> size <= 20 } == true })
        .put("fixtureUnknownIntentNotCompleted",display.title == "취소 요청 접수 · 완료 미확인" && !display.canCancel)
        .put("staleReceiptProtected",staleReceiptProtected).put("pendingKeyRestored",pendingRestored).put("pendingReceiptBlocksNewWrite",pendingBlocked)
        .put("choicesUnchanged",choicesUnchanged).put("writeRequests",0).put("privateRecordsIncluded",false)
        .put("result",if (results.all { it.isSuccess } && choicesUnchanged && !display.canCancel && pendingRestored && pendingBlocked && staleReceiptProtected) "passed" else "incomplete")
}
