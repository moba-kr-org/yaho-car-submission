package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.yaho.hyundai.vehicle.VehicleControlAdapter
import ai.yaho.hyundai.vehicle.VehicleControlStatus

internal data class ManualVehicleControls(
    val state: VehicleControlStatus,
    val setWindow: (Int) -> Unit,
    val setWiper: (String) -> Unit,
)

@Composable
internal fun rememberManualVehicleControls(active: Boolean, canControl: Boolean): ManualVehicleControls {
    val context = LocalContext.current.applicationContext
    val consentStore = remember { ai.yaho.hyundai.data.AppConsentStore(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestCanControl by rememberUpdatedState(canControl)
    val consents = rememberAppConsents()
    var state by remember { mutableStateOf(VehicleControlStatus()) }
    var adapter by remember { mutableStateOf<VehicleControlAdapter?>(null) }

    DisposableEffect(lifecycle, consents.manualControl, active) {
        fun stop() {
            adapter?.stop()
            adapter = null
            state = state.copy(connected = false, windowWritable = false, wiperWritable = false)
        }
        fun start() {
            if (adapter != null) return
            if (!consents.manualControl || !active) return

            if (state.outcome == "unknown" || state.busy) return
            adapter = VehicleControlAdapter(context,
                canActuate = { consentStore.read().manualControl && latestCanControl && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
                onStatusChanged = { state = it }).also { it.start() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }

    return ManualVehicleControls(state, { adapter?.setWindowPosition(it) }, { adapter?.setWiperSwitch(it) })
}

@Composable
internal fun CompanionVehicleActions(
    controls: ManualVehicleControls,
    canControl: Boolean,
    blockedReason: String,
    onRequestSpeedPermission: () -> Unit,
    inline: Boolean = false,
) {
    val state = controls.state
    var menu by remember { mutableStateOf<String?>(null) }
    val ready = canControl && state.connected && !state.busy && state.outcome != "unknown"

    if (inline) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            HorizontalDivider()
            VehicleControlOptions(true, controls, ready, canControl, blockedReason,
                onRequestSpeedPermission, compact = true)
            HorizontalDivider()
            VehicleControlOptions(false, controls, ready, canControl, blockedReason,
                onRequestSpeedPermission, compact = true, showSharedStatus = false)
        }
    } else {
        SettingsTiles(listOf(
            SettingsTile("창문", if (state.windowWritable) "운전석 열기 · 닫기" else "지원 · 권한 확인", "window") { menu = "window" },
            SettingsTile("와이퍼", if (state.wiperWritable) "앞 와이퍼 조절" else "지원 · 권한 확인", "wiper") { menu = "wiper" },
        ))
        menu?.let { selected ->
            HomeActionDialog(if (selected == "window") "운전석 창문" else "앞 와이퍼", { menu = null }) {
                SettingsPanel {
                    VehicleControlOptions(selected == "window", controls, ready, canControl, blockedReason,
                        onRequestSpeedPermission, compact = false)
                }
            }
        }
    }
}

@Composable
private fun VehicleControlOptions(
    window: Boolean,
    controls: ManualVehicleControls,
    ready: Boolean,
    canControl: Boolean,
    blockedReason: String,
    onRequestSpeedPermission: () -> Unit,
    compact: Boolean,
    showSharedStatus: Boolean = true,
) {
    val context = LocalContext.current
    val state = controls.state
    val detailStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val windowLabel = when {
            state.windowPosition == null || state.windowMinimum != 0 || (state.windowMaximum ?: 0) <= 0 -> "미확인"
            state.windowPosition == 0 -> "닫힘"
            state.windowPosition == state.windowMaximum -> "열림"
            else -> "일부 열림"
        }
        Text(if (window) "운전석 창문 · $windowLabel" else "앞 와이퍼 · ${wiperLabel(state.wiperSwitch)}",
            style = detailStyle, fontWeight = FontWeight.Medium)
        if (showSharedStatus && !canControl) {
            Text(blockedReason, style = detailStyle)
            if (context.checkSelfPermission("android.car.permission.CAR_SPEED") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                OutlinedButton(onClick = onRequestSpeedPermission) { Text("속도 확인 권한 허용") }
            }
        }
        if (showSharedStatus && (state.outcome in setOf("unknown", "verified") || state.busy)) Text(state.message, style = detailStyle)
        if (window) {
            val mapped = state.windowMinimum == 0 && (state.windowMaximum ?: 0) > 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { state.windowMaximum?.let(controls.setWindow) },
                    enabled = ready && state.windowWritable && mapped, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("창문 열기") }
                OutlinedButton(onClick = { controls.setWindow(0) },
                    enabled = ready && state.windowWritable && mapped, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("창문 닫기") }
            }
            if (canControl && (!state.windowWritable || !mapped)) Text(state.message, style = detailStyle)
        } else {
            if (state.wiperOptions.isEmpty() && canControl) Text(state.message, style = detailStyle)
            state.wiperOptions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { option ->
                        FilterChip(
                            selected = state.wiperSwitch == option,
                            onClick = { controls.setWiper(option) },
                            enabled = ready && state.wiperWritable,
                            label = { Text(wiperLabel(option)) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }
        if (!compact) Text("조작은 버튼을 누르면 실행됩니다.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun wiperLabel(value: String?): String = when (value) {
    "OFF" -> "끄기"
    "AUTO" -> "자동"
    "LOW", "LO" -> "느리게"
    "HIGH", "HI" -> "빠르게"
    "CONTINUOUS_LEVEL_1" -> "느리게"
    "CONTINUOUS_LEVEL_2" -> "중간 속도"
    "CONTINUOUS_LEVEL_3" -> "빠르게"
    "MIST", "INT", "INTERMITTENT" -> "간헐 작동"
    null -> "미확인"
    else -> value
}
