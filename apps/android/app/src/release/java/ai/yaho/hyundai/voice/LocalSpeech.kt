package ai.yaho.hyundai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper


class LocalSpeech(
    @Suppress("UNUSED_PARAMETER") context: Context,
    onEvent: (String) -> Unit = {},
    onState: (Boolean, String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    val ready: Boolean = false

    init {
        handler.post {
            if (!closed) {
                onEvent("unavailable")
                onState(false, "음성 안내 연동 전 · 화면 안내를 사용합니다.")
            }
        }
    }

    fun speak(@Suppress("UNUSED_PARAMETER") text: String): Boolean = false
    fun stop() = Unit
    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
    }
}
