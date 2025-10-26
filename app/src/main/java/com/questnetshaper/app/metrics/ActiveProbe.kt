package com.questnetshaper.app.metrics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import kotlin.math.max

class ActiveProbe(
    private val scope: CoroutineScope,
    private val onMetrics: (rttMean: Double, rttP95: Double, jitter: Double, lossPercent: Double) -> Unit,
) {
    private var job: Job? = null

    fun start(targetHost: String) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val address = InetAddress.getByName(targetHost)
            val window = ArrayDeque<Double>()
            var sent = 0
            var received = 0
            while (isActive) {
                val start = System.nanoTime()
                val reachable = try {
                    address.isReachable(null, 0, PROBE_TIMEOUT_MS)
                } catch (_: Exception) {
                    false
                }
                sent++
                val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
                if (reachable) {
                    window.addLast(elapsedMs)
                    received++
                    if (window.size > MAX_SAMPLE_SIZE) {
                        window.removeFirst()
                    }
                    val mean = window.average()
                    val sorted = window.sorted()
                    val p95Index = max(sorted.size - 1, (sorted.size * 0.95).toInt())
                    val p95 = if (sorted.isNotEmpty()) sorted[p95Index] else Double.NaN
                    val jitter = if (sorted.size >= 2) {
                        sorted.zip(sorted.drop(1)).map { (a, b) -> kotlin.math.abs(b - a) }.average()
                    } else {
                        Double.NaN
                    }
                    val lossPercent = if (sent == 0) 0.0 else (1 - received.toDouble() / sent) * 100
                    onMetrics(mean, p95, jitter, lossPercent)
                } else {
                    val lossPercent = if (sent == 0) 0.0 else (1 - received.toDouble() / sent) * 100
                    onMetrics(Double.NaN, Double.NaN, Double.NaN, lossPercent)
                }
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun Iterable<Double>.average(): Double {
        var total = 0.0
        var count = 0
        for (value in this) {
            total += value
            count++
        }
        return if (count == 0) Double.NaN else total / count
    }

    companion object {
        private const val PROBE_INTERVAL_MS = 500L
        private const val PROBE_TIMEOUT_MS = 1_000
        private const val MAX_SAMPLE_SIZE = 20
    }
}
