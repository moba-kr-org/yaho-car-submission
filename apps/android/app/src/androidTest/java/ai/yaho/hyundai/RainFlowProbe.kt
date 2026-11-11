package ai.yaho.hyundai

import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.AppConsentStore
import ai.yaho.hyundai.voice.LocalSpeech
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runRainFlowProbe(): JSONObject {
    val result=JSONObject().put("test","synthetic_rain_guidance_flow_v1").put("realFleet",false).put("audiblyVerified",false)
    if(!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu","goldfish") || Build.PRODUCT.startsWith("crp_avd")))
        return result.put("status","blocked")
    val choices=AppConsentStore(targetContext).read()
    val prefs=targetContext.getSharedPreferences("yaho_rain_guidance",android.content.Context.MODE_PRIVATE)
    val prefsBefore=prefs.all.toMap()
    val events=java.util.Collections.synchronizedList(mutableListOf<String>())
    val initialized=CountDownLatch(1)
    val ready=AtomicReference(false)
    var speech:LocalSpeech?=null
    var activity:SpeechProbeActivity?=null
    var persistedAt:Long?=null
    fun fetchAssessment():JSONObject {
        val connection=URL("http://10.0.2.2:4189/assessment").openConnection() as HttpURLConnection
        connection.connectTimeout=3000;connection.readTimeout=3000;connection.instanceFollowRedirects=false
        try { check(connection.responseCode==200);return JSONObject(connection.inputStream.bufferedReader().use{it.readText()}) }
        finally {connection.disconnect()}
    }
    try {
        activity=startActivitySync(Intent(targetContext,SpeechProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SpeechProbeActivity
        runOnMainSync {speech=LocalSpeech(targetContext,onEvent={events.add(it)}) {supported,_->ready.set(supported);initialized.countDown()}}
        check(initialized.await(10,TimeUnit.SECONDS)&&ready.get()) {"speech_not_initialized"}
        val response=fetchAssessment()
        check(response.getString("channel")=="simulator")
        val assessment=response.getJSONArray("items").getJSONObject(0)
        check(assessment.getInt("wiperKnownCount")==10 && assessment.getInt("sustainedWiperCount")==8)
        val expiry=java.time.Instant.parse(assessment.getString("expiresAt")).toEpochMilli()
        val received=SystemClock.elapsedRealtime()
        fun currentNotice():String? = rainNoticeText(
            isNetworkAssessmentFresh(expiry,System.currentTimeMillis(),received,SystemClock.elapsedRealtime()),
            assessment.getString("interpretation"),assessment.getString("weatherCrossCheck"),true)
        val controller=RainGuidanceController({persistedAt},{persistedAt=it;true},{speech?.stop()})
        var bannerShown=false;var speechRequested=false;var hidden=false
        runOnMainSync {
            val banner=controller.update(currentNotice(),true,System.currentTimeMillis(),SystemClock.elapsedRealtime())
            check(banner?.startsWith("시험 안내입니다.")==true) {"fresh_candidate_required"}
            activity?.rainNotice=banner;bannerShown=banner!=null
            controller.consumeSpeech(ready.get())?.let{speechRequested=speech?.speak(it)==true}
            check(controller.consumeSpeech(ready.get())==null) {"duplicate_speech_request"}
        }
        sendStatus(1,Bundle().apply{putString("phase","banner_visible")})
        val deadline=SystemClock.elapsedRealtime()+8000
        while(SystemClock.elapsedRealtime()<deadline) {
            runOnMainSync {
                val banner=controller.update(currentNotice(),true,System.currentTimeMillis(),SystemClock.elapsedRealtime())
                activity?.rainNotice=banner;hidden=banner==null
            }
            if(hidden)break
            SystemClock.sleep(50)
        }
        val stopDeadline=SystemClock.elapsedRealtime()+3000
        while("stop" !in events && SystemClock.elapsedRealtime()<stopDeadline)SystemClock.sleep(25)
        var after=fetchAssessment()
        val serverDeadline=SystemClock.elapsedRealtime()+6500
        while(after.getJSONArray("items").length()!=0 && SystemClock.elapsedRealtime()<serverDeadline) {
            SystemClock.sleep(300)
            after=fetchAssessment()
        }
        val seen=synchronized(events){events.toList()}
        result.put("events",JSONArray(seen)).put("bannerShown",bannerShown).put("speechRequested",speechRequested)
            .put("hidden",hidden).put("serverItemsAfter",after.getJSONArray("items").length())
        check(bannerShown&&speechRequested&&hidden&&"start" in seen&&"stop" in seen&&"done" !in seen) {"expiry_flow_incomplete"}
        check(after.getJSONArray("items").length()==0) {"server_assessment_not_expired"}
        check(prefsBefore==prefs.all && choices==AppConsentStore(targetContext).read()) {"user_settings_changed"}
        result.put("status","passed").put("httpCandidate8of10",true).put("simulationLabelled",true)
            .put("shippingCardPresented",bannerShown).put("oneSpeechRequest",speechRequested)
            .put("expiredCardHidden",hidden).put("expiredSpeechStopped",true).put("serverAssessmentExpired",true)
            .put("events",JSONArray(seen)).put("preferencesChanged",false)
    }catch(error:Exception){result.put("status","incomplete").put("errorClass",error.javaClass.simpleName)
        val code=error.message?.takeIf{it in setOf("speech_not_initialized","fresh_candidate_required","duplicate_speech_request","expiry_flow_incomplete","server_assessment_not_expired","user_settings_changed")}
        result.put("reason",code?:"unexpected_test_failure").put("events",JSONArray(synchronized(events){events.toList()}))
    }finally{
        runOnMainSync{speech?.close();activity?.finish()}
        runCatching {
            val connection=URL("http://10.0.2.2:4189/done").openConnection() as HttpURLConnection
            connection.requestMethod="POST";connection.connectTimeout=1000;connection.readTimeout=1000
            try{connection.responseCode}finally{connection.disconnect()}
        }
    }
    return result
}
