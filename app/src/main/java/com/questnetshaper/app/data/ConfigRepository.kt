package com.questnetshaper.app.data

import com.questnetshaper.app.model.PresetProfile
import com.questnetshaper.app.model.ShapingConfig
import kotlinx.coroutines.flow.StateFlow

class ConfigRepository {
    val config: StateFlow<ShapingConfig> = ConfigStore.config

    fun update(transform: (ShapingConfig) -> ShapingConfig) {
        ConfigStore.update(transform)
    }

    fun applyPreset(preset: PresetProfile) {
        ConfigStore.update { current ->
            current.copy(
                latencyMs = preset.latencyMs,
                jitterMs = preset.jitterMs,
                packetLossPercent = preset.lossPercent,
                bandwidthUpKbps = preset.bandwidthUpKbps,
                bandwidthDownKbps = preset.bandwidthDownKbps,
                preset = preset,
                shapingEnabled = preset != PresetProfile.NONE || current.shapingEnabled,
            )
        }
    }
}
