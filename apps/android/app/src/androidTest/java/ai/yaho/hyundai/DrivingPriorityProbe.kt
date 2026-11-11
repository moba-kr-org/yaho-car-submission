package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.voice.LocalSpeech
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


internal fun Instrumentation.runDrivingPriorityProbe(visibilityBase:Boolean=false):JSONObject {
    val result=JSONObject().put("test",if(visibilityBase)"visibility_priority_v1" else "driving_priority_v1").put("inputsSynthetic",true).put("audiblyVerified",false)
    if(!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu","goldfish") || Build.PRODUCT.startsWith("crp_avd")))return result.put("status","blocked")
    val events=java.util.Collections.synchronizedList(mutableListOf<String>())
    val initialized=CountDownLatch(1)
    var speech:LocalSpeech?=null;var activity:SpeechProbeActivity?=null
    var stamp:GuidanceStamp?=null
    fun awaitCondition(timeout:Long,predicate:()->Boolean):Boolean {
        val end=SystemClock.elapsedRealtime()+timeout
        while(!predicate() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(25)
        return predicate()
    }
    try {
        activity=startActivitySync(Intent(targetContext,SpeechProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SpeechProbeActivity
        runOnMainSync{speech=LocalSpeech(targetContext,onEvent={events.add(it)}) {_,_->initialized.countDown()} }
        check(initialized.await(10,TimeUnit.SECONDS)&&speech?.ready==true){"initialization_failed"}
        val rain=if(visibilityBase) {
            val monitor=VisibilityMonitor()
            val end=System.currentTimeMillis();val received=SystemClock.elapsedRealtime()
            var assessment=VisibilityAssessment("unknown")
            for(age in listOf(40_000L,20_000L,0L)) {
                val rows=listOf(VisibilitySignal(VISIBILITY_WIPER,"수신","ON",end-age,received-age),VisibilitySignal(VISIBILITY_DEFROSTER,"수신","true",end-age,received-age))
                assessment=monitor.update(rows,true,end-age,received-age,true)
            }
            checkNotNull(assessment.notice){"visibility_not_qualified"}
        } else DrivingNotice("rain",20,rainNoticeText(true,"possible_precipitation","unavailable",true)!!)
        val controller=PriorityGuidanceController({stamp},{stamp=it;true},{speech?.stop()})
        runOnMainSync{
            activity?.rainNotice=controller.update(rain,true,System.currentTimeMillis(),SystemClock.elapsedRealtime())
            check(speech?.speak(controller.consumeSpeech(speech?.ready==true)!!)==true){"rain_request_failed"}
        }
        check(awaitCondition(8000){"start" in events}){"rain_not_started"}
        val now=System.currentTimeMillis()
        val warnings=assessVehicleWarnings(listOf(WarningObservation("Safety.hasPowerDownWarningIndicator","수신","true",now,SystemClock.elapsedRealtime())),true,now,SystemClock.elapsedRealtime()).active
        val priority=selectDrivingNotice(warnings,emptyList(),if(visibilityBase)null else rain.text,now,SystemClock.elapsedRealtime(),true,true,null,null,false,if(visibilityBase)rain else null)
        check(priority?.key=="vehicle:power_limited"){"selection_failed"}
        var waitingWithoutConsuming=false
        runOnMainSync{
            activity?.rainNotice=controller.update(priority,true,System.currentTimeMillis(),SystemClock.elapsedRealtime())
            waitingWithoutConsuming=controller.consumeSpeech(speech?.ready==true)==null
        }
        check(waitingWithoutConsuming){"stop_ack_guard_failed"}
        check(awaitCondition(4000){"stop" in events && speech?.ready==true}){"stop_ack_missing"}
        runOnMainSync{
            check(speech?.speak(controller.consumeSpeech(speech?.ready==true)!!)==true){"priority_request_failed"}
            check(controller.consumeSpeech(true)==null){"duplicate_priority_request"}
        }
        check(awaitCondition(12000){events.count{it=="start"}==2&&"done" in events}){"priority_not_completed"}
        var noReplay=false
        runOnMainSync{noReplay=controller.update(rain,true,System.currentTimeMillis(),SystemClock.elapsedRealtime())==null;activity?.rainNotice=controller.banner}
        SystemClock.sleep(500)
        check(noReplay&&events.count{it=="requested"}==2){"lower_notice_replayed"}
        result.put("status","passed").put("higherBypassedCooldown",true).put("lowerSpeechStopped",true)
            .put("waitedForStopAck",true).put("higherSpeechCompleted",true).put("lowerNotReplayed",true)
    }catch(error:Exception){result.put("status","incomplete").put("errorClass",error.javaClass.simpleName)
        .put("reason",error.message?.takeIf{it in setOf("initialization_failed","rain_request_failed","rain_not_started","selection_failed","stop_ack_guard_failed","stop_ack_missing","priority_request_failed","duplicate_priority_request","priority_not_completed","lower_notice_replayed","visibility_not_qualified")}?:"unexpected_test_failure")
    }finally{result.put("events",JSONArray(synchronized(events){events.toList()}));runOnMainSync{speech?.close();activity?.finish()}}
    return result
}
