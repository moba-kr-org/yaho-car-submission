package ai.yaho.hyundai.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.yaho.hyundai.core.PriorityGuidanceController
import ai.yaho.hyundai.core.DrivingNotice
import ai.yaho.hyundai.core.GuidanceStamp
import ai.yaho.hyundai.voice.LocalSpeech
import kotlinx.coroutines.delay

internal data class RainGuidanceState(val banner: String?, val voiceReady: Boolean, val voiceStatus: String,
    val automaticVoice: Boolean, val onAutomaticVoice: (Boolean) -> Unit, val onRead: () -> Unit, val onDismiss: () -> Unit, val canRead: Boolean, val speakReply: (String) -> Boolean)


@Composable
internal fun rememberRainGuidance(selection: DrivingNotice?): RainGuidanceState {
    val context = LocalContext.current.applicationContext
    val prefs = remember { context.getSharedPreferences("yaho_rain_guidance", Context.MODE_PRIVATE) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var automatic by remember { mutableStateOf(prefs.getBoolean("priorityVoice", false)) }
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("기기 내 한국어 음성 확인 중") }
    var canRead by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }

    val speech = remember { LocalSpeech(context) { supported, text -> ready = supported; status = text } }
    val controller = remember(speech) { PriorityGuidanceController(
        { when {
            prefs.contains("priorityAt") -> GuidanceStamp(prefs.getLong("priorityAt",0),prefs.getString("priorityKey","").orEmpty(),prefs.getInt("priorityRank",0))
            prefs.contains("lastPresentedAt") -> GuidanceStamp(prefs.getLong("lastPresentedAt",0),"rain",20)
            else -> null
        } },
        { stamp -> prefs.edit().putLong("priorityAt",stamp.at).putString("priorityKey",stamp.key).putInt("priorityRank",stamp.priority).commit() }, speech::stop) }
    val latestNotice by rememberUpdatedState(selection)
    DisposableEffect(lifecycle, speech) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) { controller.dismiss(); banner = null; canRead = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech.close() }
    }
    LaunchedEffect(selection, foreground, ready) {
        do {
            banner = controller.update(selection, foreground, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
            canRead = ready && controller.canRead
            if (!foreground) break
            delay(250)
        } while (true)
    }
    fun readCurrent() {
        banner = controller.update(latestNotice, foreground, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
        controller.consumeSpeech(ready)?.let { text ->
            if (!speech.speak(text)) status = "음성 요청 실패 · 화면 안내를 사용합니다."
        }
        canRead = ready && controller.canRead
    }
    LaunchedEffect(banner, ready, automatic, foreground) { if (automatic) readCurrent() else speech.stop() }
    return RainGuidanceState(banner.takeIf { it == selection?.text && foreground }, ready, status, automatic,
        { enabled ->
            if ((!enabled || ready) && prefs.edit().putBoolean("priorityVoice", enabled).commit()) {
                automatic = enabled
                if (!enabled) speech.stop()
            }
        }, { readCurrent() }, { controller.dismiss(); banner = null; canRead = false }, canRead, speech::speak)
}
