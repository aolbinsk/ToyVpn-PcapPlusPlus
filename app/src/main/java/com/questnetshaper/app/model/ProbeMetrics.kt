package com.questnetshaper.app.model

data class ProbeMetrics(
    val rttMeanMs: Double = Double.NaN,
    val rttP95Ms: Double = Double.NaN,
    val jitterMs: Double = Double.NaN,
    val lossPercent: Double = Double.NaN,
)
