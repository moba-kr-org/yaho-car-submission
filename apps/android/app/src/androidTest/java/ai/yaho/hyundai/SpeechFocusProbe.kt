package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import android.os.SystemClock
import ai.yaho.hyundai.voice.LocalSpeech
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


internal fun Instrumentation.runSpeechFocusProbe():JSONObject {
    val result=JSONObject().put("test","speech_focus_v1").put("actualNavigationVoice",false)
        .put("syntheticFocusContender",true).put("audiblyVerified",false)
    if(!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu","goldfish") || Build.PRODUCT.startsWith("crp_avd")))
        return result.put("status","blocked")
    val audio=targetContext.getSystemService(AudioManager::class.java)
    val events=java.util.Collections.synchronizedList(mutableListOf<String>())
    val initialized=CountDownLatch(1);val ready=AtomicBoolean(false)
    var speech:LocalSpeech?=null;var activity:android.app.Activity?=null
    var contender:AudioFocusRequest?=null
    fun awaitEvent(name:String,millis:Long):Boolean {
        val deadline=SystemClock.elapsedRealtime()+millis
        while(name !in events && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(25)
        return name in events
    }
    try {
        activity=startActivitySync(Intent(targetContext,SpeechProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        runOnMainSync { speech=LocalSpeech(targetContext,onEvent={events.add(it)}) {value,_->ready.set(value);initialized.countDown()} }
        check(initialized.await(10,TimeUnit.SECONDS)&&ready.get()) {"initialization_failed"}
        runOnMainSync { check(speech?.speak("시험 안내입니다. 다른 음성 안내가 시작되면 야호의 안내를 멈추는지 확인합니다. 이 문장은 실제 도로 정보가 아닙니다.")==true) {"speech_request_rejected"} }
        check(awaitEvent("start",8000)) {"speech_did_not_start"}
        var granted=false
        runOnMainSync {
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }.build()
            contender=request
            granted=audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        check(granted) {"contender_not_granted"}
        check(awaitEvent("focus_lost",3000)&&awaitEvent("stop",3000)) {"focus_did_not_stop_speech"}
        val before=events.count{it=="requested"}
        val startsBefore=events.count{it=="start"}
        runOnMainSync { contender?.let{audio.abandonAudioFocusRequest(it)};contender=null }
        SystemClock.sleep(700)
        check(events.count{it=="requested"}==before && events.count{it=="start"}==startsBefore) {"unexpected_automatic_replay"}
        runOnMainSync {check(speech?.speak("시험입니다. 새로운 요청으로 음성 연결을 다시 확인합니다.")==true) {"explicit_request_rejected"}}
        check(awaitEvent("done",12000)) {"explicit_request_did_not_complete"}
        result.put("status","passed").put("focusLossStoppedSpeech",true).put("noAutomaticReplay",true)
            .put("newExplicitRequestCompleted",true)
    }catch(error:Exception){
        result.put("status","incomplete").put("errorClass",error.javaClass.simpleName)
            .put("reason",error.message?.takeIf{it in setOf("initialization_failed","speech_request_rejected","speech_did_not_start","contender_not_granted","focus_did_not_stop_speech","unexpected_automatic_replay","explicit_request_rejected","explicit_request_did_not_complete")}?:"unexpected_test_failure")
    }finally{
        result.put("events",JSONArray(synchronized(events){events.toList()}))
        runOnMainSync {contender?.let{audio.abandonAudioFocusRequest(it)};speech?.close();activity?.finish()}
    }
    return result
}
