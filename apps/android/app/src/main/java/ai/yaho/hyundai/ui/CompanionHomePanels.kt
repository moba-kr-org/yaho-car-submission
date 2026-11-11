package ai.yaho.hyundai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.yaho.hyundai.vehicle.VehicleSnapshot
import kotlin.math.roundToInt

internal data class HomeVehicleStatus(val energy: List<String>, val warning: String) {
    val summary: String get() = (energy + warning).joinToString(" · ")
}

@Composable
internal fun rememberHomeVehicleStatus(snapshot: VehicleSnapshot, allowed: Boolean, warningCount: Int, checked: Int): HomeVehicleStatus {
    val elapsed by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val fresh = if (snapshot.active && allowed) snapshot.readings.filter { reading ->
        val stamp = reading.receivedElapsed
        reading.state == "수신" && stamp != null && elapsed - stamp in 0..30_000
    } else emptyList()
    val energy = listOf(
        "EvBattery.getEvBatteryStateOfCharge" to "배터리",
        "Fuel.getFuelPercent" to "연료",
    ).mapNotNull { (key, label) ->
        val value = fresh.firstOrNull { it.key == key }?.value?.toDoubleOrNull()
        if (value != null && value in 0.0..100.0) "$label ${value.roundToInt()}%" else null
    }
    val warning = when {
        !allowed -> "차량 조회 꺼짐"
        warningCount > 0 -> "확인할 경고 ${warningCount}건"
        checked > 0 -> "최근 수신한 경고 없음"
        else -> "차량 신호 미확인"
    }
    return HomeVehicleStatus(energy, warning)
}

@Composable
internal fun CompanionTalkComposer(modifier: Modifier, onDismiss: () -> Unit, onQuestion: (String) -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier,
        color = Color(0xFFFFFBF7),
        contentColor = Color(0xFF30261F),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color(0xFFEAD9CA)),
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VoiceInput(
                prompt = "야호에게 말해 보세요",
                modifier = Modifier.weight(1f),
                autoStart = true,
                onSpeech = { onQuestion(it); null },
            )
            TextButton(onClick = onDismiss) { Text("닫기", fontSize = 22.sp) }
        }
    }
}

@Composable
internal fun VoiceInput(
    prompt: String,
    modifier: Modifier = Modifier,
    autoStart: Boolean = false,
    cancelSignal: Int = 0,
    onSpeech: (String) -> String?,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var voiceState by remember { mutableStateOf<String?>(null) }
    var startPending by remember { mutableStateOf(false) }
    val latestSpeech by rememberUpdatedState(onSpeech)
    fun stop() {
        val current = recognizer
        recognizer = null
        current?.destroy()
    }
    fun listen() {
        if (recognizer != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val session = runCatching {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        if (session == null) {
            voiceState = "다시 눌러 말해 주세요."
            return
        }
        recognizer = session
        voiceState = "음성 인식 시작 중…"
        runCatching {
            session.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (recognizer === session) voiceState = "듣고 있어요" }
                override fun onBeginningOfSpeech() { if (recognizer === session) voiceState = "듣고 있어요" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (recognizer === session) voiceState = "말씀을 확인하고 있어요…" }
                override fun onError(error: Int) {
                    if (recognizer !== session) return
                    stop()
                    voiceState = "다시 눌러 말해 주세요."
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== session) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()?.take(200)
                    stop()
                    voiceState = if (text.isNullOrBlank()) "다시 눌러 말해 주세요." else latestSpeech(text)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            session.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }.onFailure {
            stop()
            voiceState = "다시 눌러 말해 주세요."
        }
    }
    fun startWhenResumed() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen()
        else startPending = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWhenResumed() else voiceState = "마이크 권한을 허용하면 말할 수 있어요."
    }
    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startWhenResumed()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { stop(); voiceState = null }
                Lifecycle.Event.ON_RESUME -> if (startPending) { startPending = false; listen() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(cancelSignal) {
        if (cancelSignal != 0) {
            stop()
            startPending = false
            voiceState = null
        }
    }
    LaunchedEffect(autoStart) { if (autoStart) start() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FilledTonalIconButton(
            onClick = {
                if (recognizer == null) start()
                else { stop(); voiceState = null }
            },
            modifier = Modifier.size(68.dp).semantics {
                contentDescription = if (recognizer == null) "마이크 시작" else "마이크 중지"
            },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFFFFE6CE), contentColor = Color(0xFFB84A08),
            ),
        ) {
            ShortcutIcon("mic", Modifier.size(31.dp))
        }
        Text(voiceState ?: prompt, style = MaterialTheme.typography.labelLarge, color = Color(0xFF30261F))
    }
}
