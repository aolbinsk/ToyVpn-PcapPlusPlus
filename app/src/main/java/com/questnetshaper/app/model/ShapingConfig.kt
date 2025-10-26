package com.questnetshaper.app.model

import kotlinx.serialization.Serializable

@Serializable
data class ShapingConfig(
    val shapingEnabled: Boolean = false,
    val loggingEnabled: Boolean = true,
    val latencyMs: Int = 0,
    val jitterMs: Int = 0,
    val packetLossPercent: Float = 0f,
    val bandwidthUpKbps: Int = DEFAULT_BANDWIDTH_KBPS,
    val bandwidthDownKbps: Int = DEFAULT_BANDWIDTH_KBPS,
    val probeHost: String = DEFAULT_PROBE_HOST,
    val targetPackage: String = DEFAULT_TARGET_PACKAGE,
    val notes: String = "",
    val preset: PresetProfile = PresetProfile.NONE,
) {
    companion object {
        const val DEFAULT_BANDWIDTH_KBPS = 10_000
        const val DEFAULT_PROBE_HOST = "8.8.8.8"
        const val DEFAULT_TARGET_PACKAGE = "quest.eleven.forfunlabs"
    }
}
