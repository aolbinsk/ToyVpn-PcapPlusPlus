package com.questnetshaper.app.service

import com.questnetshaper.app.model.ShapingConfig
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

internal class PacketShaper {
    private val random = Random(System.currentTimeMillis())
    private var upstreamLimiter = BandwidthLimiter(Int.MAX_VALUE)
    private var downstreamLimiter = BandwidthLimiter(Int.MAX_VALUE)

    fun updateConfig(config: ShapingConfig) {
        upstreamLimiter = BandwidthLimiter(config.bandwidthUpKbps)
        downstreamLimiter = BandwidthLimiter(config.bandwidthDownKbps)
    }

    suspend fun shape(
        packet: ByteArray,
        length: Int,
        direction: PacketDirection,
        config: ShapingConfig,
        forward: suspend (ByteArray, Int) -> Unit,
    ) {
        if (!config.shapingEnabled) {
            forward(packet, length)
            return
        }

        if (config.packetLossPercent > 0 && random.nextDouble(0.0, 100.0) < config.packetLossPercent) {
            return
        }

        val delayMs = computeDelay(config)
        if (delayMs > 0) {
            delay(delayMs)
        }

        val waitMs = when (direction) {
            PacketDirection.UPSTREAM -> upstreamLimiter.allow(length)
            PacketDirection.DOWNSTREAM -> downstreamLimiter.allow(length)
        }
        if (waitMs > 0) {
            delay(waitMs)
        }

        forward(packet, length)
    }

    private fun computeDelay(config: ShapingConfig): Long {
        val base = config.latencyMs
        val jitter = if (config.jitterMs <= 0) 0 else random.nextInt(-abs(config.jitterMs), abs(config.jitterMs) + 1)
        return (base + jitter).coerceAtLeast(0).toLong()
    }
}
