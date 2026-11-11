package ai.yaho.hyundai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.yaho.hyundai.R

internal enum class SettingsSection(val label: String, val icon: String) {
    VEHICLE("차량", "car"),
    ROAD("도로", "road"),
    NAVIGATION("내비게이션", "navigation"),
    DIAGNOSTICS("고장코드", "status"),
    SERVICE("정비", "calendar"),
    PERMISSIONS("권한", "lock"),
    ACCOUNT("계정", "person"),
}

private val SettingsInk = Color(0xFF30261F)
internal val SettingsMuted = Color(0xFF6F6258)
private val SettingsLine = Color(0xFFEBDFD4)
private val SettingsAccent = Color(0xFFB84A08)
private val SettingsPaper = Color(0xFFFFFBF7)
private val SettingsRail = Color(0xFFF8EFE6)
private val SettingsPage = Color(0xFFF7F1EB)
internal val LocalNestedPanel = androidx.compose.runtime.staticCompositionLocalOf { false }
private val SettingsCard = Color(0xFFFFFFFF)





@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.withSettingsPalette(),
        typography = typography.copy(
            titleLarge = typography.titleLarge.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            titleMedium = typography.titleMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            bodyLarge = typography.bodyLarge.copy(fontSize = 26.sp, lineHeight = 36.sp),
            bodyMedium = typography.bodyMedium.copy(fontSize = 24.sp, lineHeight = 34.sp),
            bodySmall = typography.bodySmall.copy(fontSize = 21.sp, lineHeight = 30.sp, color = SettingsMuted),
            labelLarge = typography.labelLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
            labelMedium = typography.labelMedium.copy(fontSize = 22.sp, lineHeight = 30.sp, color = SettingsMuted),
            labelSmall = typography.labelSmall.copy(fontSize = 20.sp, lineHeight = 28.sp, color = SettingsMuted),
        ),
        content = content,
    )
}


@Composable
internal fun SettingsStatusLine(text: String, on: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(when (on) {
            true -> Color(0xFF3D7A5A); false -> Color(0xFFB9AEA5); null -> Color(0xFFC98A1E)
        }))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
internal fun SettingsFinePrint(label: String = "자세히", content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(if (open) "$label 닫기  ⌃" else "$label  ⌄", color = SettingsAccent, fontSize = 21.sp,
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "펼침" else "접힘" }.padding(vertical = 10.dp))
    if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun SettingsLayout(
    section: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(SettingsPaper)) {
        val railWidth = (maxWidth * .24f).coerceIn(184.dp, 400.dp)
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(railWidth).fillMaxHeight().background(SettingsRail)
                    .padding(bottom = 120.dp).verticalScroll(rememberScrollState()).selectableGroup(),
            ) {
                SettingsSection.entries.forEach { item ->
                    val selected = item == section
                    val ink = if (selected) Color.White else SettingsInk
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 96.dp)
                            .background(if (selected) SettingsAccent else Color.Transparent)
                            .selectable(selected, role = Role.Tab, onClick = { onSelect(item) })
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShortcutIcon(item.icon, Modifier.size(34.dp), ink)
                        Text(item.label, color = ink, fontSize = 29.sp, lineHeight = 36.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
            VerticalDivider(color = SettingsLine)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(section.label, modifier = Modifier.padding(horizontal = 40.dp, vertical = 23.dp),
                    color = SettingsInk, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = SettingsLine)
                SettingsTheme {
                    key(section) {
                        Column(
                            Modifier.fillMaxSize().background(SettingsPage).verticalScroll(rememberScrollState())
                                .padding(top = 12.dp, bottom = 132.dp),
                            content = content,
                        )
                    }
                }
            }
        }
        SettingsCornerButton(isOpen = true, onClick = onClose)
    }
}

@Composable
internal fun BoxScope.SettingsCornerButton(isOpen: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.align(Alignment.BottomStart).padding(28.dp).size(76.dp)
            .semantics { contentDescription = if (isOpen) "닫기" else "설정" },
        colors = ButtonDefaults.buttonColors(containerColor = SettingsAccent, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (isOpen) {
            Canvas(Modifier.size(28.dp)) {
                val inset = size.width * .22f
                val start = Offset(inset, inset)
                val end = Offset(size.width - inset, size.height - inset)
                drawLine(Color.White, start, end, 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(end.x, start.y), Offset(start.x, end.y), 2.5.dp.toPx(), StrokeCap.Round)
            }
        } else {
            Image(painter = painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun SettingsPanel(
    title: String? = null,
    category: String? = null,
    accent: Color = SettingsMuted,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nested = LocalNestedPanel.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (nested) 0.dp else 28.dp, vertical = if (nested) 0.dp else 8.dp),
        shape = RoundedCornerShape(if (nested) 0.dp else 22.dp), color = SettingsCard, contentColor = SettingsInk,
        border = if (nested) null else androidx.compose.foundation.BorderStroke(1.dp, SettingsLine),
    ) {
        if (nested) HorizontalDivider(color = SettingsLine, modifier = Modifier.padding(horizontal = 30.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = if (nested) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            category?.let { Text(it, color = accent, style = MaterialTheme.typography.labelSmall) }
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable
internal fun SettingsDetails(title: String, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp), color = SettingsCard, contentColor = SettingsInk,
        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsLine),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 92.dp)
                    .semantics { stateDescription = if (expanded) "펼침" else "접힘" }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 30.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(if (expanded) "⌃" else "⌄", color = SettingsAccent, fontSize = 28.sp)
            }

            if (expanded) CompositionLocalProvider(LocalNestedPanel provides true) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), content = content)
            }
        }
    }
}

internal data class SettingsTile(val title: String, val detail: String, val icon: String, val onClick: (() -> Unit)?)

@Composable
internal fun SettingsTiles(tiles: List<SettingsTile>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Column(Modifier.padding(horizontal = 28.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { tile ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 150.dp)
                                .background(SettingsCard, RoundedCornerShape(22.dp))
                                .border(1.dp, SettingsLine, RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(enabled = tile.onClick != null, role = Role.Button) { tile.onClick?.invoke() }
                                .padding(horizontal = 28.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                ShortcutIcon(tile.icon, Modifier.size(40.dp), SettingsInk)
                                Text("›", color = SettingsMuted, fontSize = 30.sp)
                            }
                            Column {
                                Text(tile.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(tile.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun ColorScheme.withSettingsPalette() = copy(
    primary = SettingsAccent,
    secondaryContainer = Color(0xFFFFE6CE),
    onSecondaryContainer = Color(0xFF743507),
    surface = SettingsPaper,
    onSurface = SettingsInk,
    outlineVariant = SettingsLine,
)

@Composable
internal fun HomeActionDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SettingsTheme {
            Surface(
                modifier = Modifier.padding(28.dp).widthIn(max = 860.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SettingsPage,
                contentColor = SettingsInk,
                shadowElevation = 20.dp,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 94.dp).padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, Modifier.weight(1f), fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
                        androidx.compose.material3.TextButton(onClick = onDismiss) { Text("닫기") }
                    }
                    HorizontalDivider(color = SettingsLine)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), content = content)
                }
            }
        }
    }
}
