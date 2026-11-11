package ai.yaho.hyundai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class CompanionShortcut(
    val title: String,
    val detail: String,
    val icon: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun CompanionQuickRail(
    actions: List<CompanionShortcut>,
    modifier: Modifier = Modifier,
    expandedContent: (@Composable () -> Unit)? = null,
) {
    val panelShape = RoundedCornerShape(30.dp)
    Column(modifier.clip(panelShape)
        .background(Color.White.copy(alpha = .55f))
        .border(1.dp, Color.White.copy(alpha = .85f), panelShape)
        .padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEachIndexed { index, action ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (pressed) Color(0xFFFFDEC3).copy(alpha = .65f) else Color.Transparent)
                    .clickable(enabled = action.enabled, interactionSource = interaction, indication = null,
                        role = androidx.compose.ui.semantics.Role.Button, onClick = action.onClick)
                    .alpha(if (action.enabled) 1f else .62f)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFFFE2C9).copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    ShortcutIcon(action.icon, Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(action.title, color = Color(0xFF40362F), fontSize = 26.sp,
                        lineHeight = 34.sp, fontWeight = FontWeight.Medium)
                    Text(action.detail, color = Color(0xFF67594D), fontSize = 20.sp, lineHeight = 26.sp)
                }
            }
            if (index < actions.lastIndex) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Color(0xFF9B8A79).copy(alpha = .12f)))
        }
        expandedContent?.invoke()
    }
}

@Composable
internal fun ShortcutIcon(kind: String, modifier: Modifier, ink: Color = Color(0xFFB84A08)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(ink,
            Offset(w*x1,h*y1), Offset(w*x2,h*y2), stroke, StrokeCap.Round)
        when (kind) {
            "mic" -> {
                drawRoundRect(ink, Offset(w*.35f,h*.09f), Size(w*.3f,h*.53f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.15f), style=Stroke(stroke))
                drawArc(ink, 0f, 180f, false, Offset(w*.23f,h*.36f), Size(w*.54f,h*.43f), style=Stroke(stroke))
                line(.5f,.79f,.5f,.91f); line(.35f,.91f,.65f,.91f)
            }
            "car" -> {
                line(.12f,.53f,.28f,.30f); line(.28f,.30f,.72f,.30f); line(.72f,.30f,.88f,.53f)
                line(.12f,.53f,.12f,.75f); line(.12f,.75f,.88f,.75f); line(.88f,.75f,.88f,.53f)
                line(.12f,.53f,.88f,.53f); line(.2f,.75f,.2f,.86f); line(.8f,.75f,.8f,.86f)
                line(.24f,.64f,.33f,.64f); line(.67f,.64f,.76f,.64f)
            }
            "navigation" -> {
                line(.5f,.13f,.19f,.82f); line(.19f,.82f,.5f,.66f); line(.5f,.66f,.81f,.82f); line(.81f,.82f,.5f,.13f)
            }
            "lock" -> {
                drawRoundRect(ink, Offset(w*.22f,h*.46f), Size(w*.56f,h*.40f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.08f), style=Stroke(stroke))
                drawArc(ink, 180f, 180f, false, Offset(w*.32f,h*.16f), Size(w*.36f,h*.37f), style=Stroke(stroke))
                line(.32f,.345f,.32f,.46f); line(.68f,.345f,.68f,.46f); line(.5f,.61f,.5f,.70f)
            }
            "person" -> {
                drawCircle(ink, w*.18f, Offset(w*.5f,h*.32f), style=Stroke(stroke))
                drawArc(ink, 180f, 180f, false, Offset(w*.16f,h*.57f), Size(w*.68f,h*.44f), style=Stroke(stroke))
            }
            "phone" -> {
                line(.23f,.16f,.36f,.11f); line(.36f,.11f,.48f,.34f)
                line(.48f,.34f,.36f,.44f); line(.36f,.44f,.57f,.65f)
                line(.57f,.65f,.68f,.53f); line(.68f,.53f,.89f,.66f)
                line(.89f,.66f,.84f,.81f); line(.84f,.81f,.66f,.87f)
                line(.66f,.87f,.38f,.72f); line(.38f,.72f,.17f,.47f)
                line(.17f,.47f,.14f,.25f); line(.14f,.25f,.23f,.16f)
            }
            "calendar" -> {
                drawRoundRect(ink, Offset(w*.13f,h*.23f), Size(w*.74f,h*.63f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.09f), style=Stroke(stroke))
                line(.13f,.43f,.87f,.43f); line(.34f,.13f,.34f,.29f); line(.66f,.13f,.66f,.29f)
                line(.35f,.63f,.46f,.73f); line(.46f,.73f,.66f,.54f)
            }
            "window" -> {
                drawRoundRect(ink, Offset(w*.12f,h*.14f), Size(w*.76f,h*.7f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.09f), style=Stroke(stroke))
                line(.13f,.63f,.87f,.63f)
                line(.52f,.3f,.52f,.55f)
                line(.4f,.43f,.52f,.55f); line(.64f,.43f,.52f,.55f)
            }
            "wiper" -> {
                drawArc(ink, 205f, 130f, false, Offset(w*.08f,h*.08f), Size(w*.84f,h*.84f), style=Stroke(stroke, cap=StrokeCap.Round))
                line(.5f,.82f,.25f,.3f); line(.16f,.35f,.61f,.14f)
                line(.1f,.84f,.9f,.84f)
            }
            "road" -> {
                line(.3f,.12f,.12f,.9f); line(.7f,.12f,.88f,.9f)
                line(.5f,.15f,.5f,.32f); line(.5f,.46f,.5f,.61f); line(.5f,.76f,.5f,.9f)
            }
            else -> {
                drawRoundRect(ink, Offset(w*.1f,h*.16f), Size(w*.8f,h*.67f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.12f), style=Stroke(stroke))
                line(.23f,.51f,.38f,.51f); line(.38f,.51f,.46f,.34f)
                line(.46f,.34f,.57f,.67f); line(.57f,.67f,.66f,.51f); line(.66f,.51f,.78f,.51f)
            }
        }
    }
}
