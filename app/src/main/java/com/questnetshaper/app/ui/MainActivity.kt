package com.questnetshaper.app.ui

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questnetshaper.app.model.PresetProfile
import com.questnetshaper.app.ui.theme.QuestNetShaperTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ConfigViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.startVpn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuestNetShaperTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    uiState = uiState,
                    onToggleShaping = viewModel::toggleShaping,
                    onToggleLogging = viewModel::toggleLogging,
                    onLatencyChanged = viewModel::updateLatency,
                    onJitterChanged = viewModel::updateJitter,
                    onLossChanged = viewModel::updateLoss,
                    onBandwidthUpChanged = { viewModel.updateBandwidth(up = it) },
                    onBandwidthDownChanged = { viewModel.updateBandwidth(down = it) },
                    onNotesChanged = viewModel::updateNotes,
                    onProbeHostChanged = viewModel::updateProbeHost,
                    onTargetPackageChanged = viewModel::updateTargetPackage,
                    onPresetSelected = viewModel::applyPreset,
                    onStart = { requestVpnPermission() },
                    onStop = viewModel::stopVpn,
                )
            }
        }
    }

    private fun requestVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpn()
        }
    }
}

@Composable
fun MainScreen(
    uiState: ConfigViewModel.UiState,
    onToggleShaping: (Boolean) -> Unit,
    onToggleLogging: (Boolean) -> Unit,
    onLatencyChanged: (Int) -> Unit,
    onJitterChanged: (Int) -> Unit,
    onLossChanged: (Float) -> Unit,
    onBandwidthUpChanged: (Int) -> Unit,
    onBandwidthDownChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onProbeHostChanged: (String) -> Unit,
    onTargetPackageChanged: (String) -> Unit,
    onPresetSelected: (PresetProfile) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CaptureSection(
            uiState = uiState,
            onStart = onStart,
            onStop = onStop,
            onToggleShaping = onToggleShaping,
            onToggleLogging = onToggleLogging,
            onTargetPackageChanged = onTargetPackageChanged,
        )
        LatencySection(uiState, onLatencyChanged, onJitterChanged)
        LossSection(uiState, onLossChanged)
        BandwidthSection(uiState, onBandwidthUpChanged, onBandwidthDownChanged)
        PresetSection(uiState, onPresetSelected)
        ProbeSection(uiState, onProbeHostChanged)
        NotesSection(uiState, onNotesChanged)
        MetricsSection(uiState)
    }
}

@Composable
private fun CaptureSection(
    uiState: ConfigViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleShaping: (Boolean) -> Unit,
    onToggleLogging: (Boolean) -> Unit,
    onTargetPackageChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Capture", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart) {
                Text("Start")
            }
            Button(onClick = onStop) {
                Text("Stop")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = uiState.config.shapingEnabled, onCheckedChange = onToggleShaping)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Shaping")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = uiState.config.loggingEnabled, onCheckedChange = onToggleLogging)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Logging")
        }
        OutlinedTextField(
            value = uiState.config.targetPackage,
            onValueChange = onTargetPackageChanged,
            label = { Text("Target Package") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LatencySection(
    uiState: ConfigViewModel.UiState,
    onLatencyChanged: (Int) -> Unit,
    onJitterChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Latency / Jitter", style = MaterialTheme.typography.titleMedium)
        Text("Latency: ${uiState.config.latencyMs} ms")
        Slider(value = uiState.config.latencyMs.toFloat(), onValueChange = { onLatencyChanged(it.toInt()) }, valueRange = 0f..300f)
        Text("Jitter: ${uiState.config.jitterMs} ms")
        Slider(value = uiState.config.jitterMs.toFloat(), onValueChange = { onJitterChanged(it.toInt()) }, valueRange = 0f..100f)
    }
}

@Composable
private fun LossSection(
    uiState: ConfigViewModel.UiState,
    onLossChanged: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Packet Loss", style = MaterialTheme.typography.titleMedium)
        Text("Loss: ${"%.1f".format(uiState.config.packetLossPercent)} %")
        Slider(value = uiState.config.packetLossPercent, onValueChange = onLossChanged, valueRange = 0f..10f)
    }
}

@Composable
private fun BandwidthSection(
    uiState: ConfigViewModel.UiState,
    onUpChanged: (Int) -> Unit,
    onDownChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bandwidth", style = MaterialTheme.typography.titleMedium)
        Text("Upload: ${uiState.config.bandwidthUpKbps} kbps")
        Slider(value = uiState.config.bandwidthUpKbps.toFloat(), onValueChange = { onUpChanged(it.toInt()) }, valueRange = 128f..10_000f)
        Text("Download: ${uiState.config.bandwidthDownKbps} kbps")
        Slider(value = uiState.config.bandwidthDownKbps.toFloat(), onValueChange = { onDownChanged(it.toInt()) }, valueRange = 128f..10_000f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSection(
    uiState: ConfigViewModel.UiState,
    onPresetSelected: (PresetProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        var expanded by remember { mutableStateOf(false) }
        val presets = PresetProfile.entries
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = PresetProfile.displayName(uiState.config.preset),
                onValueChange = {},
                readOnly = true,
                label = { Text("Preset") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(text = { Text(PresetProfile.displayName(preset)) }, onClick = {
                        onPresetSelected(preset)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun ProbeSection(
    uiState: ConfigViewModel.UiState,
    onProbeHostChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Probe Settings", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.config.probeHost,
            onValueChange = onProbeHostChanged,
            label = { Text("Host/IP") },
        )
    }
}

@Composable
private fun NotesSection(
    uiState: ConfigViewModel.UiState,
    onNotesChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Notes", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.config.notes,
            onValueChange = onNotesChanged,
            placeholder = { Text("Optional session notes") },
        )
    }
}

@Composable
private fun MetricsSection(uiState: ConfigViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live Metrics", style = MaterialTheme.typography.titleMedium)
        Text("Bytes Up/s: ${uiState.metrics.bytesUpPerSec}")
        Text("Bytes Down/s: ${uiState.metrics.bytesDownPerSec}")
        Text("RTT mean: ${formatMetric(uiState.metrics.rttMeanMs)} ms")
        Text("Loss: ${formatMetric(uiState.metrics.lossPercent)} %")
    }
}

private fun formatMetric(value: Double): String =
    if (value.isNaN()) "--" else "%.1f".format(value)
