package com.questnetshaper.app.model

data class MetricsSnapshot(
    val timestampUtc: Long = System.currentTimeMillis(),
    val elapsedSeconds: Long = 0,
    val flowsActive: Int = 0,
    val bytesUpPerSec: Long = 0,
    val bytesDownPerSec: Long = 0,
    val packetsUpPerSec: Long = 0,
    val packetsDownPerSec: Long = 0,
    val rttMeanMs: Double = Double.NaN,
    val rttP95Ms: Double = Double.NaN,
    val jitterMs: Double = Double.NaN,
    val lossPercent: Double = Double.NaN,
    val wifiRssiDbm: Int? = null,
    val wifiLinkSpeedMbps: Int? = null,
)
