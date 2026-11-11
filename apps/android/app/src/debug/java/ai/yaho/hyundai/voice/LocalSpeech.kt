package ai.yaho.hyundai.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale


class LocalSpeech(context: Context, private val onEvent: (String) -> Unit = {},
    private val onState: (Boolean, String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus: AudioFocusRequest? = null
    private var engine: TextToSpeech? = null
    private var healthy = true
    private var requestActive = false
    private var stopPending = false
    private var initialized = false
    private var initializationFinished = false
    private var closed = false
    var ready: Boolean = false
        private set
    private fun post(action: () -> Unit) { handler.post { if (!closed) action() } }
    private val listener = object : EventListener {
        override fun onReady() { post { onEvent("ready") } }
        override fun onStart() { post { onEvent("start"); onState(ready, "Pleos 기기 내 음성 재생 시작") } }
        override fun onDone() { post { requestActive = false; releaseFocus(); onEvent("done"); onState(ready, "Pleos 기기 내 음성 재생 완료") } }
        override fun onStop() { post { requestActive = false; stopPending = false; ready = initialized && healthy; releaseFocus(); onEvent("stop"); onState(ready, "음성 안내 중지 · 자동으로 다시 재생하지 않습니다.") } }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ErrorType) { post { fail(errorType.name) } }
    }
    init {
        if (context.checkSelfPermission("pleos.car.permission.TTS_SERVICE") != PackageManager.PERMISSION_GRANTED) {
            post { onState(false, "Pleos 음성 권한 확인 필요 · 화면 안내를 사용합니다."); onEvent("permission_missing") }
        } else try {
            engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            engine?.initialize(object : OnInitializeListener {
                override fun onSuccess() { post {
                    if (initializationFinished) return@post
                    initializationFinished = true
                    try {
                        initialized = true
                        engine?.addEventListener(listener)
                        engine?.setLocale(Locale.KOREA)
                        engine?.setAudioAttributes(attributes)
                        ready = true
                        onState(true, "Pleos 기기 내 한국어 음성 초기화 완료 · 재생 결과는 별도 확인")
                        onEvent("initialized")
                    } catch (_: Exception) { fail("configuration_failed") }
                } }
                override fun onFailure(errorType: ErrorType) { post { fail(errorType.name) } }
            })
            handler.postDelayed({ if (!closed && !initializationFinished) fail("initialization_timeout") }, 8_000)
        } catch (_: Exception) { post { fail("initialization_failed") } }
    }
    private fun fail(reason: String) {
        initializationFinished = true
        healthy = false
        ready = false
        requestActive = false; stopPending = false
        releaseFocus()
        onEvent("error:$reason")
        onState(false, "Pleos 기기 내 음성 미준비 ($reason) · 화면 안내를 사용합니다.")
    }
    fun speak(text: String): Boolean {
        if (closed || !ready || !initialized || requestActive || stopPending) return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS) {
                    onEvent("focus_lost")
                    stop()
                }
            }.build()
        if (audio?.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return try {
            requestActive = true
            engine?.speak(text, true)

            onEvent("requested")
            true
        } catch (_: Exception) { fail("speak_invocation_failed"); false }
    }
    private fun releaseFocus() { focus?.let { audio?.abandonAudioFocusRequest(it) }; focus = null }
    fun stop() {
        if (initialized && requestActive && !stopPending) {
            requestActive = false
            stopPending = true
            ready = false
            if (!closed) onState(false, "이전 음성의 중단 응답을 기다립니다.")
            runCatching { engine?.stop() }.onFailure { if (!closed) fail("stop_invocation_failed") }
        }
        releaseFocus()
    }
    fun close() {
        closed = true; ready = false; stop()
        runCatching { if (initialized) engine?.removeEventListener(listener) }
        runCatching { engine?.release() }
        engine = null; initialized = false; handler.removeCallbacksAndMessages(null)
    }
}
