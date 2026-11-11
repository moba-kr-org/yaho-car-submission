package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.*
import ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


internal fun Instrumentation.runWarningBookingFlowProbe():JSONObject=runBlocking {
    val result=JSONObject().put("test","warning_booking_flow_v1").put("providerEnabled",false).put("inputsSynthetic",true)
    if(!BuildConfig.DEBUG||!(Build.HARDWARE in setOf("ranchu","goldfish")||Build.PRODUCT.startsWith("crp_avd")))return@runBlocking result.put("status","blocked")
    val choices=AppConsentStore(targetContext).read()
    val prefix="warning_booking_test_${java.util.UUID.randomUUID()}_"
    val names=mutableSetOf<String>()
    val sandbox=object:ContextWrapper(targetContext){
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            val scoped=prefix+name;names.add(scoped);return targetContext.getSharedPreferences(scoped,mode)
        }
    }
    fun fixture(path:String,post:Boolean=false):JSONObject {
        val c=URL("http://10.0.2.2:4190/fixture/$path").openConnection() as HttpURLConnection
        c.connectTimeout=5000;c.readTimeout=5000;c.instanceFollowRedirects=false
        if(post)c.requestMethod="POST"
        try{check(c.responseCode==200){"fixture_unavailable"};val text=c.inputStream.bufferedReader().use{it.readText()};return if(post)JSONObject() else JSONObject(text)}finally{c.disconnect()}
    }
    try {
        val identities=fixture("bootstrap").getJSONArray("identities")
        val provider=object:InstallationCredentialProvider{
            override fun credentialsFor(variant:ExperimentVariant):InstallationIdentity {
                val row=identities.getJSONObject(if(variant==ExperimentVariant.A)0 else 1)
                return InstallationIdentity(row.getString("installationId"),row.getString("accessToken"))
            }
        }
        fun client()=BackendApi(provider,IdempotencyKeyStore(sandbox),"http://10.0.2.2:4190")
        val api=client();val variant=ExperimentVariant.A
        val now=System.currentTimeMillis();val elapsed=android.os.SystemClock.elapsedRealtime()
        val active=assessVehicleWarnings(listOf(WarningObservation("Safety.hasPowerDownWarningIndicator","수신","true",now,elapsed)),true,now,elapsed)
        val draft=checkNotNull(prepareWarningServiceDraft(active.active.single(),active,true,true,true)){"draft_not_created"}
        val notes=warningServiceNotes(draft.notes,draft.origin,active,true)
        check(notes.length<=500){"notes_over_limit"}
        val date=java.time.Instant.ofEpochMilli(now+86_400_000).toString()
        val location=SimulatorVehicleContextAdapter(DrivingMode.PARKED).readContext()
        val review=ServiceRequestReview(variant,provider.credentialsFor(variant).installationId,draft.serviceType,date,date,notes,location.latitude,location.longitude,true,draft.origin)
        check(!isCurrentServiceRequestReview(review,review.copy(notes="changed"))){"changed_approval_allowed"}
        val off=assessVehicleWarnings(listOf(WarningObservation(draft.origin.key,"수신","false",now,elapsed)),true,now,elapsed)
        check(!isCurrentServiceRequestReview(review,review.copy(notes=warningServiceNotes(draft.notes,draft.origin,off,true)))){"changed_warning_allowed"}
        val command=GleoCommand.RequestServiceBooking(draft.serviceType,date,notes)
        check(!api.requestService(variant,command,location,false).success){"unapproved_sent"}
        check(fixture("outcome").getInt("creates")==0){"unapproved_http_write"}
        var approvalChecks = 0
        check(!api.requestService(variant,command,location,true, approvalStillCurrent = { ++approvalChecks == 1 }).success){"changed_after_review_sent"}
        check(approvalChecks == 2 && !api.hasPendingBooking(variant)){"review_preparation_left_booking_pending"}
        check(fixture("outcome").getInt("creates")==0){"changed_after_review_http_write"}
        val lost=api.requestService(variant,command,location,true)
        check(!lost.success&&api.hasPendingBooking(variant)){"lost_receipt_not_pending"}
        val restarted=client()
        check(restarted.hasPendingBooking(variant)){"restart_lost_pending"}
        check(!restarted.requestService(variant,command,location,true).success){"pending_resent"}
        check(fixture("outcome").getInt("creates")==1){"duplicate_http_write"}
        val history=BookingHistoryController(object:BookingHistorySource{
            override suspend fun list()=restarted.fetchBookings(variant)
            override fun hasPending()=restarted.hasPendingBooking(variant)
            override suspend fun recover()=restarted.recoverPendingBooking(variant)
            override suspend fun cancel(id:String)=restarted.cancelBooking(variant,id,true)
        },android.os.SystemClock::elapsedRealtime)
        history.refresh()
        val booking=history.state.value.items.single()
        check(booking.status=="needs_configuration"&&!history.state.value.pending){"recovery_failed"}
        check(bookingStatusDisplay(booking.status,false).title=="제공자 연동 전"){"receipt_claimed_confirmed"}
        check(!restarted.cancelBooking(variant,booking.bookingId,false).isSuccess){"cancel_without_approval"}
        check(fixture("outcome").getInt("cancels")==0){"unapproved_cancel_http_write"}
        check(restarted.fetchBookings(ExperimentVariant.B).getOrThrow().isEmpty()){"other_installation_list_leaked"}
        check(restarted.cancelBooking(ExperimentVariant.B,booking.bookingId,true).isFailure){"other_installation_cancelled"}
        check(restarted.fetchBookings(variant).getOrThrow().single().status=="needs_configuration"){"foreign_cancel_changed_state"}
        history.cancel(booking.bookingId)
        check(history.state.value.items.single().status=="cancelled"){"cancel_failed"}
        check(!bookingStatusDisplay("cancelled",false).canCancel){"repeat_cancel_enabled"}
        val stats=fixture("outcome")
        check(stats.getInt("jobs")==1&&stats.getInt("cancelled")==1&&stats.getInt("external_attempts")==0){"server_outcome_mismatch"}
        check(choices==AppConsentStore(targetContext).read()){"user_consent_changed"}
        result.put("status","passed").put("approvalChangesBlocked",true).put("changedDuringServerReviewBlocked",true).put("unapprovedWritesBlocked",true)
            .put("lostReceiptRecoveredAfterRestart",true).put("duplicateCreateBlocked",true)
            .put("providerUnconfiguredNotConfirmed",true).put("crossInstallationAccessBlocked",true)
            .put("syntheticCancellationCompleted",true).put("externalAttempts",0).put("credentialsOrNotesIncluded",false)
    }catch(error:Exception){result.put("status","incomplete").put("errorClass",error.javaClass.simpleName)
        .put("reason",error.message?.takeIf{it in setOf("fixture_unavailable","draft_not_created","notes_over_limit","changed_after_review_sent","review_preparation_left_booking_pending","changed_after_review_http_write","changed_approval_allowed","changed_warning_allowed","unapproved_sent","unapproved_http_write","lost_receipt_not_pending","restart_lost_pending","pending_resent","duplicate_http_write","recovery_failed","receipt_claimed_confirmed","cancel_without_approval","unapproved_cancel_http_write","other_installation_list_leaked","other_installation_cancelled","foreign_cancel_changed_state","cancel_failed","repeat_cancel_enabled","server_outcome_mismatch","user_consent_changed")}?:"unexpected_test_failure")
    }finally{
        names.forEach{targetContext.deleteSharedPreferences(it)}
        runCatching{fixture("done",true)}
    }
    result
}
