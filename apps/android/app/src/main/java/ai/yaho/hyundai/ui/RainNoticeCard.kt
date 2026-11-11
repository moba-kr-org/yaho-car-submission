package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RainNoticeCard(text: String, canRead: Boolean, onRead: () -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onRead, enabled = canRead, modifier = Modifier.heightIn(min = 64.dp)) { Text("음성으로 듣기") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 64.dp)) { Text("닫기") }
            }
        }
    }
}
