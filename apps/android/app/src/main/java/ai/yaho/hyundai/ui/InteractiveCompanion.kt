package ai.yaho.hyundai.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ai.yaho.hyundai.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin


@Composable
fun InteractiveCompanion(
    modifier: Modifier = Modifier, touchEnabled: Boolean = true, onTalk: (() -> Unit)? = null,

    talking: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) .96f else 1f,
        spring(dampingRatio = .55f, stiffness = 450f),
        label = "fox-touch-press",
    )
    var recentTouches by remember { mutableStateOf(emptyList<Long>()) }
    var reactionId by remember { mutableIntStateOf(0) }
    var repeatedTouch by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var reacting by remember { mutableStateOf(false) }

    LaunchedEffect(reactionId, touchEnabled) {
        if (!touchEnabled) {
            reacting = false
            recentTouches = emptyList()
            reactionId = 0
            return@LaunchedEffect
        }
        if (reactionId == 0) return@LaunchedEffect
        reacting = true
        elapsed = 0f
        val duration = if (repeatedTouch) 1700f else 900f
        val start = withFrameNanos { it }
        while (elapsed < duration) {
            elapsed = withFrameNanos { (it - start) / 1_000_000f }
        }
        reacting = false
    }

    val duration = if (repeatedTouch) 1700f else 900f
    val decay = if (reacting) (1f - elapsed / duration).coerceIn(0f, 1f) else 0f
    val wave = sin(elapsed / 1000f * (if (repeatedTouch) 11f else 7f) * 2f * PI).toFloat()
    val shakeDecay = if (reacting) (1f - elapsed / 450f).coerceIn(0f, 1f) else 0f
    val motionUnit = with(LocalDensity.current) { 8.dp.toPx() }

    val glowIn by animateFloatAsState(if (talking) 1f else 0f, tween(450), label = "fox-talk-glow")
    val breathing = rememberInfiniteTransition(label = "fox-talk-breath")
    val pulse by breathing.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fox-talk-pulse")
    Box(modifier, contentAlignment = Alignment.Center) {
    if (glowIn > 0f) Canvas(Modifier.fillMaxSize()) {
        val base = minOf(size.width, size.height)
        val radius = base * (.44f + .05f * pulse)
        val warm = Color(0xFFFFB066)
        drawCircle(
            Brush.radialGradient(
                0f to warm.copy(alpha = (.75f + .2f * pulse) * glowIn),
                .6f to warm.copy(alpha = (.42f + .15f * pulse) * glowIn),
                1f to Color.Transparent,
                center = center, radius = radius,
            ),
            radius = radius, center = center,
        )
        drawCircle(Color(0xFFFFB066).copy(alpha = (.7f - .35f * pulse) * glowIn), radius = base * (.33f + .04f * pulse),
            center = center, style = Stroke(width = 5.dp.toPx()))
    }
    Image(
        painter = painterResource(R.drawable.yaho_companion_current),
        contentDescription = "야호 얼굴",
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                val wiggle = if (repeatedTouch) wave * shakeDecay else wave * decay
                translationX = motionUnit * wiggle
                translationY = if (repeatedTouch) 0f else -motionUnit * abs(wiggle)
                rotationZ = (if (repeatedTouch) 3f else 2f) * wiggle
                scaleX = pressScale * (1f + (if (repeatedTouch) .04f else .025f) * decay)
                scaleY = pressScale * (1f - .025f * decay)
            }
            .clickable(
                enabled = touchEnabled,
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClickLabel = if (onTalk != null) "야호와 대화" else "야호 쓰다듬기",
            ) {
                val now = SystemClock.uptimeMillis()
                val touches = recentTouches.filter { now - it < 2500L } + now
                repeatedTouch = touches.size >= 5
                recentTouches = if (repeatedTouch) emptyList() else touches
                elapsed = 0f
                reactionId++
                onTalk?.invoke()
            },
    )
    }
}
