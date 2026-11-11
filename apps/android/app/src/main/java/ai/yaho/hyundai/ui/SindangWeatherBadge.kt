package ai.yaho.hyundai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ai.yaho.hyundai.data.BackendApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class WeatherCondition(val code: String, val label: String) {
    CLEAR("clear", "맑음"),
    PARTLY_CLOUDY("partly_cloudy", "구름 많음"),
    OVERCAST("overcast", "흐림"),
    RAIN("rain", "비"),
    DRIZZLE("drizzle", "빗방울"),
    SNOW("snow", "눈"),
    SLEET("sleet", "비·눈"),
    PRECIPITATION("precipitation", "강수 관측"),
    DRY("dry", "하늘 상태 미확인"),
    UNKNOWN("unknown", "날씨 미확인"),
}

private data class SindangWeather(
    val condition: WeatherCondition,
    val temperatureC: Double?,
    val forecast: Boolean,
    val observedAt: Instant,
)

@Composable
internal fun SindangWeatherBadge(backend: BackendApi, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var weather by remember(backend) { mutableStateOf<SindangWeather?>(null) }
    LaunchedEffect(backend, lifecycle) {
        if (!backend.isConfigured) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (isActive) {
                    val response = backend.readSindangWeather()
                    val received = if (response?.optString("state") == "available" &&
                        response.optString("provider") == "KMA_ULTRA_SRT_NCST") {
                        val observedAt = runCatching { Instant.parse(response.getString("observedAt")) }.getOrNull()
                        val precipitation = response.opt("precipitation") as? Boolean
                        val condition = WeatherCondition.entries.firstOrNull { it.code == response.optString("condition") }
                            ?: if (precipitation == true) WeatherCondition.PRECIPITATION else WeatherCondition.DRY
                        val temperature = (response.opt("temperatureC") as? Number)?.toDouble()?.takeIf { it in -80.0..65.0 }
                        if (observedAt != null && precipitation != null)
                            SindangWeather(condition, temperature, response.optString("conditionSource") == "forecast", observedAt)
                        else null
                    } else null
                    val ageMs = received?.let { System.currentTimeMillis() - it.observedAt.toEpochMilli() }
                    val maxAgeMs = 90 * 60_000L
                    weather = received.takeIf { ageMs != null && ageMs in 0 until maxAgeMs }
                    delay(if (weather != null && ageMs != null) minOf(60_000L, maxAgeMs - ageMs) else 60_000L)
                }
            } finally {
                weather = null
            }
        }
    }

    val currentWeather = weather ?: return
    val condition = currentWeather.condition
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = .70f),
        contentColor = Color(0xFF40362F),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherGlyph(condition)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("서울 중구 신당동", fontSize = 17.sp, lineHeight = 22.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    currentWeather.temperatureC?.let {
                        Text("${it.roundToInt()}°C", fontSize = 33.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    }
                    val label = if (currentWeather.forecast) "${condition.label} 예보" else condition.label
                    Text(label, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WeatherGlyph(condition: WeatherCondition) {
    val sunlight = Color(0xFFB86413)
    val cloud = Color(0xFF566D7D)
    val wet = Color(0xFF3677A0)
    Box(
        modifier = Modifier.size(54.dp).background(Color(0xFFF5E8D9), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val scale = size.width / 32
            val stroke = 1.9.dp.toPx()
            fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
            fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, point(x1, y1), point(x2, y2), stroke, StrokeCap.Round)
            fun sun(x: Float, y: Float, radius: Float) {
                drawCircle(sunlight, radius * scale, point(x, y), style = Stroke(stroke))
                for (step in 0..7) {
                    val angle = step * Math.PI / 4
                    val dx = kotlin.math.cos(angle).toFloat()
                    val dy = kotlin.math.sin(angle).toFloat()
                    line(sunlight, x + dx * (radius + 2), y + dy * (radius + 2),
                        x + dx * (radius + 4), y + dy * (radius + 4))
                }
            }
            when (condition) {
                WeatherCondition.CLEAR -> sun(16f, 16f, 6f)
                WeatherCondition.UNKNOWN, WeatherCondition.DRY -> line(cloud, 10f, 16f, 22f, 16f)
                else -> {
                    if (condition == WeatherCondition.PARTLY_CLOUDY) sun(10f, 10f, 4f)
                    val outline = Path().apply {
                        moveTo(7 * scale, 23 * scale)
                        cubicTo(1 * scale, 23 * scale, 2 * scale, 14 * scale, 9 * scale, 14 * scale)
                        cubicTo(10 * scale, 7 * scale, 20 * scale, 7 * scale, 23 * scale, 14 * scale)
                        cubicTo(31 * scale, 12 * scale, 34 * scale, 23 * scale, 25 * scale, 23 * scale)
                        close()
                    }
                    drawPath(outline, Color(0xFFF5E8D9))
                    drawPath(outline, cloud, style = Stroke(stroke))
                    when (condition) {
                        WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.PRECIPITATION ->
                            for (x in listOf(10f, 16f, 22f)) line(wet, x + 1, 26f, x - 1, 30f)
                        WeatherCondition.SNOW, WeatherCondition.SLEET ->
                            for (x in listOf(10f, 16f, 22f)) drawCircle(wet, 1.2f * scale, point(x, 28.5f))
                        else -> Unit
                    }
                }
            }
        }
    }
}
