package com.questnetshaper.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class PresetProfile(
    val latencyMs: Int,
    val jitterMs: Int,
    val lossPercent: Float,
    val bandwidthUpKbps: Int,
    val bandwidthDownKbps: Int,
) {
    NONE(0, 0, 0f, ShapingConfig.DEFAULT_BANDWIDTH_KBPS, ShapingConfig.DEFAULT_BANDWIDTH_KBPS),
    HIGH_PING(120, 20, 0f, ShapingConfig.DEFAULT_BANDWIDTH_KBPS, ShapingConfig.DEFAULT_BANDWIDTH_KBPS),
    JITTERY_WIFI(40, 60, 1f, ShapingConfig.DEFAULT_BANDWIDTH_KBPS, ShapingConfig.DEFAULT_BANDWIDTH_KBPS),
    PACKET_LOSS(40, 10, 3f, ShapingConfig.DEFAULT_BANDWIDTH_KBPS, ShapingConfig.DEFAULT_BANDWIDTH_KBPS),
    LOW_BANDWIDTH(20, 10, 0f, 512, 1024),
    ;

    companion object {
        fun fromDisplayName(name: String): PresetProfile = when (name) {
            "High Ping" -> HIGH_PING
            "Jittery Wi-Fi" -> JITTERY_WIFI
            "Packet Loss" -> PACKET_LOSS
            "Low BW" -> LOW_BANDWIDTH
            else -> NONE
        }

        fun displayName(profile: PresetProfile): String = when (profile) {
            NONE -> "None"
            HIGH_PING -> "High Ping"
            JITTERY_WIFI -> "Jittery Wi-Fi"
            PACKET_LOSS -> "Packet Loss"
            LOW_BANDWIDTH -> "Low BW"
        }
    }
}
