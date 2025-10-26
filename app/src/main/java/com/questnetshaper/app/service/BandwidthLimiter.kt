package com.questnetshaper.app.service

import kotlin.math.min

internal class BandwidthLimiter(
    private val kbps: Int,
) {
    private var availableTokens: Double = capacityBytes
    private var lastUpdateNs: Long = System.nanoTime()

    private val capacityBytes: Double
        get() = if (kbps == Int.MAX_VALUE) Double.MAX_VALUE else kbps / 8.0 * BURST_SECONDS

    private val refillRateBytesPerNs: Double
        get() = if (kbps == Int.MAX_VALUE) Double.MAX_VALUE else (kbps / 8.0) / 1_000_000_000.0

    @Synchronized
    fun allow(bytes: Int): Long {
        if (kbps == Int.MAX_VALUE) return 0
        val now = System.nanoTime()
        val elapsedNs = now - lastUpdateNs
        lastUpdateNs = now
        availableTokens = min(capacityBytes, availableTokens + elapsedNs * refillRateBytesPerNs)
        return if (availableTokens >= bytes) {
            availableTokens -= bytes
            0
        } else {
            val deficit = bytes - availableTokens
            val waitNs = deficit / refillRateBytesPerNs
            availableTokens = 0.0
            (waitNs / 1_000_000).toLong().coerceAtLeast(0)
        }
    }

    companion object {
        private const val BURST_SECONDS = 1.5
    }
}
