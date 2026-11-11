package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp


@Composable
internal fun ReviewDialogBody(text: String) {
    val maximumHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    Text(
        text,
        modifier = Modifier.heightIn(max = maximumHeight).verticalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodyLarge,
    )
}
