package ai.yaho.hyundai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val GlassFill = Color.White.copy(alpha = .68f)
internal val GlassInset = Color.White.copy(alpha = .43f)
internal val GlassEdge = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha=.96f), Color.White.copy(alpha=.25f), Color.White.copy(alpha=.8f))))


@Composable
internal fun GlassBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(Color(0xFFE9E8E5), Color(0xFFF7F5F1), Color(0xFFE5EBEE))))
        drawRect(Brush.radialGradient(listOf(Color(0xFFFFCDA7).copy(alpha=.5f), Color.Transparent),
            center=Offset(size.width*.12f,size.height*.08f), radius=size.width*.65f))
        drawRect(Brush.radialGradient(listOf(Color(0xFFCCDBE5).copy(alpha=.65f), Color.Transparent),
            center=Offset(size.width*.95f,size.height*.6f), radius=size.width*.6f))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.7f), Color.Transparent),
            center=Offset(size.width*.4f,size.height*.85f), radius=size.width*.55f))
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Card(
        modifier = modifier.shadow(18.dp, shape, ambientColor=Color(0xFF807364).copy(alpha=.12f), spotColor=Color(0xFF807364).copy(alpha=.12f)),
        shape = shape,
        border = GlassEdge,
        colors = CardDefaults.cardColors(containerColor=GlassFill),
        content = content,
    )
}
