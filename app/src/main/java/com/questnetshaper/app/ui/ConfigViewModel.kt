package com.questnetshaper.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.questnetshaper.app.data.ConfigRepository
import com.questnetshaper.app.data.MetricsStore
import com.questnetshaper.app.model.MetricsSnapshot
import com.questnetshaper.app.model.PresetProfile
import com.questnetshaper.app.model.ShapingConfig
import com.questnetshaper.app.service.NetShapeVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository()

    val config: StateFlow<ShapingConfig> = repository.config

    private val mutableVpnRunning = MutableStateFlow(false)
    val vpnRunning: StateFlow<Boolean> = mutableVpnRunning.asStateFlow()

    val metrics: StateFlow<MetricsSnapshot> = MetricsStore.snapshot

    val uiState: StateFlow<UiState> = combine(config, vpnRunning, metrics) { config, running, metrics ->
        UiState(config, running, metrics)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), UiState())

    fun updateLatency(value: Int) = update { it.copy(latencyMs = value) }

    fun updateJitter(value: Int) = update { it.copy(jitterMs = value) }

    fun updateLoss(value: Float) = update { it.copy(packetLossPercent = value) }

    fun updateBandwidth(up: Int? = null, down: Int? = null) = update {
        it.copy(
            bandwidthUpKbps = up ?: it.bandwidthUpKbps,
            bandwidthDownKbps = down ?: it.bandwidthDownKbps,
        )
    }

    fun updateNotes(notes: String) = update { it.copy(notes = notes) }

    fun toggleShaping(enabled: Boolean) = update { it.copy(shapingEnabled = enabled) }

    fun toggleLogging(enabled: Boolean) = update { it.copy(loggingEnabled = enabled) }

    fun updateProbeHost(host: String) = update { it.copy(probeHost = host) }

    fun updateTargetPackage(packageName: String) = update {
        it.copy(targetPackage = packageName.trim())
    }

    fun applyPreset(preset: PresetProfile) {
        repository.applyPreset(preset)
    }

    fun startVpn() {
        val context = getApplication<Application>()
        context.startForegroundService(Intent(context, NetShapeVpnService::class.java))
        mutableVpnRunning.value = true
    }

    fun stopVpn() {
        val context = getApplication<Application>()
        context.startService(Intent(context, NetShapeVpnService::class.java).apply { action = NetShapeVpnService.ACTION_STOP })
        mutableVpnRunning.value = false
    }

    private fun update(block: (ShapingConfig) -> ShapingConfig) {
        viewModelScope.launch {
            repository.update(block)
        }
    }

    data class UiState(
        val config: ShapingConfig = ShapingConfig(),
        val vpnRunning: Boolean = false,
        val metrics: MetricsSnapshot = MetricsSnapshot(),
    )
}
