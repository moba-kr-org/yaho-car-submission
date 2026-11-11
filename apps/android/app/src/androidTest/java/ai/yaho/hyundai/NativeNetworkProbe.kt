package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import ai.yaho.hyundai.core.DrivingMode
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.data.*
import ai.yaho.hyundai.vehicle.SimulatorVehicleContextAdapter
import ai.yaho.hyundai.vehicle.VehicleObservationService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject


internal fun Instrumentation.runNativeNetworkProbe(locationOverride: ai.yaho.hyundai.vehicle.VehicleContext? = null): JSONObject = runBlocking {
    val store = AppConsentStore(targetContext)
    val original = store.read()
    check(original.reviewed && original.vehicleRead && original.network) { "existing_observation_and_network_consent_required" }
    val credentials = DebugInstallationCredentialStore(targetContext)
    check(ExperimentVariant.entries.all { credentials.credentialsFor(it) != null }) { "existing_A_B_installations_required" }
    val backend = BackendApi(credentials, IdempotencyKeyStore(targetContext))
    val initial = ExperimentVariant.entries.associateWith { backend.networkState(it).getOrThrow() }
    val result = JSONObject().put("test", "native_sdk_http_network_v1")
        .put("source", if(locationOverride == null) "live_emulator_sdk_with_explicit_sample_location" else "live_emulator_sdk_with_fused_location")
        .put("rawSignalsIncluded", false).put("credentialsIncluded", false)
    try {
        for (variant in ExperimentVariant.entries) check(backend.setNetworkSharing(variant,true).success) { "server_consent_unavailable" }
        runOnMainSync { targetContext.startForegroundService(Intent(targetContext,VehicleObservationService::class.java)) }
        val deadline = SystemClock.elapsedRealtime()+15_000
        while (SystemClock.elapsedRealtime()<deadline && VehicleObservationService.snapshots.value.readings.none {
            it.key=="wiper.front.active" && it.state=="수신" && it.observedAt?.let { t -> System.currentTimeMillis()-t in 0..10_000 }==true
        }) SystemClock.sleep(100)
        val snapshot=VehicleObservationService.snapshots.value
        check(snapshot.readings.any { it.key=="wiper.front.active" && it.state=="수신" }) { "fresh_wiper_required" }
        val location=locationOverride ?: SimulatorVehicleContextAdapter(DrivingMode.PARKED).readContext()
        val push=backend.pushNetworkSnapshot(ExperimentVariant.A,snapshot,location,canSend={store.read().network})
        check(push.success) { "native_snapshot_rejected" }

        var first=backend.networkContext(ExperimentVariant.B,location).getOrThrow()
        val contextDeadline=SystemClock.elapsedRealtime()+6500
        while(first.getJSONArray("items").length()==0 && SystemClock.elapsedRealtime()<contextDeadline) {
            SystemClock.sleep(500)
            first=backend.networkContext(ExperimentVariant.B,location).getOrThrow()
        }
        check(first.getJSONArray("items").length()>0) { "receiver_aggregate_missing" }
        val item=first.getJSONArray("items").getJSONObject(0)
        check(first.getString("channel")==if(location.isSample) "simulator" else "device_reported")
        check(!item.getBoolean("confirmedWeather"))
        val count=item.getInt("participantCount")
        check(count>=1)
        check(backend.pushNetworkSnapshot(ExperimentVariant.A,snapshot,location,canSend={store.read().network}).success)
        val after=backend.networkContext(ExperimentVariant.B,location).getOrThrow().getJSONArray("items").getJSONObject(0)
        check(after.getInt("participantCount")==count) { "duplicate_installation_count" }
        val blocked=backend.pushNetworkSnapshot(ExperimentVariant.A,snapshot,location,canSend={false})
        check(!blocked.success)
        val publicText=first.toString()
        for(variant in ExperimentVariant.entries) {
            val identity=credentials.credentialsFor(variant)!!
            check(!publicText.contains(identity.accessToken) && !publicText.contains(identity.installationId))
        }

        val offline=BackendApi(credentials,IdempotencyKeyStore(targetContext),"http://10.0.2.2:1")
        val deniedContext=offline.networkContext(ExperimentVariant.B,location,canQuery={false})
        check(deniedContext.exceptionOrNull()?.message == "공유가 꺼져 있어 위치를 전송하지 않았습니다.") { "revoked_query_must_stop_before_socket" }
        var queryChecks=0
        val revokedDuringPreparation=offline.networkContext(ExperimentVariant.B,location,canQuery={ ++queryChecks == 1 })
        check(queryChecks == 2 && revokedDuringPreparation.exceptionOrNull()?.message == "공유가 꺼져 있어 위치를 전송하지 않았습니다.") { "query_must_recheck_before_socket" }
        result.put("preQueryRevocationBlocks",true).put("queryPreparationRevocationBlocks",true)
        val failedContext=offline.networkContext(ExperimentVariant.B,location)
        check(failedContext.isFailure) { "offline_context_must_fail" }
        val display=ai.yaho.hyundai.core.networkDisplayState(ai.yaho.hyundai.core.NetworkLoadState.OFFLINE,true,count)
        check(!display.showAssessment && display.participantLabel == "미확인") { "offline_display_policy_failed" }
        check(backend.networkContext(ExperimentVariant.B,location).isSuccess) { "reconnection_failed" }
        check(backend.setNetworkSharing(ExperimentVariant.A,false).success) { "withdrawal_failed" }
        check(!backend.networkState(ExperimentVariant.A).getOrThrow()) { "withdrawal_not_acknowledged" }
        check(!backend.pushNetworkSnapshot(ExperimentVariant.A,snapshot,location,canSend={true}).success) { "server_withdrawal_must_block" }
        result.put("offlineContextFails",true).put("offlineAssessmentHiddenByPolicy",true)
            .put("reconnectedContextReceived",true).put("serverWithdrawalAcknowledged",true).put("serverBlocksAfterWithdrawal",true)
        result.put("snapshotAccepted",true).put("receiverHasAggregate",true)
            .put("repeatDoesNotIncreaseParticipants",true).put("preSendRevocationBlocks",true)
            .put("privateIdentityExcluded",true).put("participantCount",count)
            .put("interpretation",after.getString("interpretation"))
            .put("weatherCrossCheck",after.getString("weatherCrossCheck"))
            .put("realVehicleFleetValidated",false)
    } finally {
        var restored=true
        for((variant,value) in initial) restored=backend.setNetworkSharing(variant,value).success && restored
        result.put("serverChoicesRestored",restored).put("localChoicesUnchanged",store.read()==original)
        check(restored) { "restore_server_choices_failed" }
    }
    result.put("result","passed")
}
