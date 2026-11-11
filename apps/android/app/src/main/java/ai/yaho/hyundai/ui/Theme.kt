package ai.yaho.hyundai.ui

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ai.yaho.hyundai.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val CarColors = lightColorScheme(
    primary = Color(0xFFF97316),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFBE85),
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF).copy(alpha = .76f),
    surfaceVariant = Color(0xFFF6F4F0).copy(alpha = .65f),
    outline = Color(0xFFA49B91).copy(alpha = .55f),
    outlineVariant = Color.White.copy(alpha = .85f),
    primaryContainer = Color(0xFFFFDFCA).copy(alpha = .8f),
    onPrimaryContainer = Color(0xFF713310),
    onSurface = Color(0xFF241F1A),
    onBackground = Color(0xFF241F1A),
    error = Color(0xFFB6342B),
)

private val YahoFont = FontFamily(Font(R.font.pretendard_regular))
private val BaseTypography = Typography()
private val YahoTypography = Typography(
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = YahoFont, fontSize = 40.sp, lineHeight = 52.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = YahoFont, fontSize = 40.sp, lineHeight = 52.sp),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = YahoFont, fontSize = 32.sp, lineHeight = 44.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = YahoFont, fontSize = 28.sp, lineHeight = 40.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = YahoFont, fontSize = 24.sp, lineHeight = 34.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = YahoFont, fontSize = 36.sp, lineHeight = 48.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = YahoFont, fontSize = 28.sp, lineHeight = 38.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = YahoFont, fontSize = 26.sp, lineHeight = 36.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = YahoFont, fontSize = 24.sp, lineHeight = 34.sp),
)

@Composable
fun YahoHyundaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarColors, typography = YahoTypography, shapes = Shapes(small=RoundedCornerShape(18.dp), medium=RoundedCornerShape(24.dp), large=RoundedCornerShape(30.dp)), content = content)
}
