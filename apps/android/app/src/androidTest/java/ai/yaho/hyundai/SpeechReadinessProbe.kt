package ai.yaho.hyundai

import android.app.Instrumentation
import android.os.Build
import ai.yaho.hyundai.voice.LocalSpeech
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


internal fun Instrumentation.runSpeechReadinessProbe(): JSONObject {
    if (!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("crp_avd")))
        return JSONObject().put("status","blocked")
    val latch=CountDownLatch(1)
    val readiness=AtomicReference<Boolean?>(null)
    var speech: LocalSpeech? = null
    try {
        runOnMainSync { speech=LocalSpeech(targetContext) { ready, _ -> readiness.set(ready); latch.countDown() } }
        latch.await(8,TimeUnit.SECONDS)
        return JSONObject().put("test","speech_readiness_v1")
            .put("status",if(readiness.get()==true) "ready" else if(readiness.get()==false) "unavailable" else "timeout")
            .put("spoken",false).put("preferencesChanged",false).put("networkVoiceSelected",false)
    } finally { runOnMainSync { speech?.close() } }
}



internal fun Instrumentation.runPleosPlaybackProbe(stopAfterStart: Boolean = false): JSONObject {
    if (!BuildConfig.DEBUG || !(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("crp_avd")))
        return JSONObject().put("status","blocked")
    val events = java.util.Collections.synchronizedList(mutableListOf<String>())
    val initialized = CountDownLatch(1)
    val ended = CountDownLatch(1)
    val started = CountDownLatch(1)
    val ready = AtomicReference(false)
    var speech: LocalSpeech? = null
    var activity: android.app.Activity? = null
    var requested = false
    try {
        activity = startActivitySync(android.content.Intent(targetContext, ai.yaho.hyundai.SpeechProbeActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        runOnMainSync {
            speech = LocalSpeech(targetContext, onEvent = { event ->
                events.add(event)
                if (event == "start") started.countDown()
                if (event == "done" || event == "stop" || event.startsWith("error:")) ended.countDown()
            }) { supported, _ -> ready.set(supported); initialized.countDown() }
        }
        initialized.await(10, TimeUnit.SECONDS)
        val text = if (stopAfterStart) "시험 안내입니다. 음성 중단 기능을 확인합니다. 이 문장은 실제 도로 상황 안내가 아닙니다. 음성 안내가 필요하지 않으면 즉시 중단할 수 있어야 합니다."
            else "야호 음성 연결 시험입니다."
        if (ready.get()) runOnMainSync { requested = speech?.speak(text) == true }
        if (requested && stopAfterStart && started.await(8, TimeUnit.SECONDS)) runOnMainSync { speech?.stop() }
        if (requested) ended.await(15, TimeUnit.SECONDS)
        val seen = synchronized(events) { events.toList() }
        return JSONObject().put("test",if(stopAfterStart) "pleos_ondevice_stop_v1" else "pleos_ondevice_playback_v1")
            .put("mode","ON_DEVICE").put("locale","ko-KR")
            .put("status", if(stopAfterStart && "start" in seen && "stop" in seen && "done" !in seen) "sdk_playback_stopped" else if(!stopAfterStart && "done" in seen && "start" in seen) "sdk_playback_completed" else "incomplete")
            .put("initialized",ready.get()).put("requested",requested)
            .put("started","start" in seen).put("completed","done" in seen).put("stopped","stop" in seen)
            .put("events",org.json.JSONArray(seen)).put("audiblyVerified",false)
            .put("preferencesChanged",false).put("serverRegistrationCalled",false)
    } finally {
        runOnMainSync { speech?.close(); activity?.finish() }
    }
}
