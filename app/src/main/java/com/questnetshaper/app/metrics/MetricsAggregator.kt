package com.questnetshaper.app.metrics

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.questnetshaper.app.model.MetricsSnapshot
import com.questnetshaper.app.data.MetricsStore
import com.questnetshaper.app.model.ProbeMetrics
import com.questnetshaper.app.model.ShapingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MetricsAggregator(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val bytesUp = AtomicLong(0)
    private val bytesDown = AtomicLong(0)
    private val pktsUp = AtomicLong(0)
    private val pktsDown = AtomicLong(0)
    private val startElapsed = SystemClock.elapsedRealtime()

    private val mutableMetrics = MutableStateFlow(MetricsSnapshot())
    val metrics: StateFlow<MetricsSnapshot> = mutableMetrics.asStateFlow()

    private var logJob: Job? = null
    private var csvWriter: CsvWriter? = null
    private var lastConfig: ShapingConfig = ShapingConfig()
    private var probeMetrics: ProbeMetrics = ProbeMetrics()

    fun startLogging(configFlow: StateFlow<ShapingConfig>) {
        logJob?.cancel()
        logJob = scope.launch(Dispatchers.IO) {
            configFlow.collect { config ->
                lastConfig = config
                if (config.loggingEnabled && csvWriter == null) {
                    csvWriter = CsvWriter(openLogFile())
                    csvWriter?.writeHeader()
                } else if (!config.loggingEnabled) {
                    csvWriter?.close()
                    csvWriter = null
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1_000)
                emitSnapshot()
            }
        }
    }

    fun stopLogging() {
        logJob?.cancel()
        logJob = null
        csvWriter?.close()
        csvWriter = null
    }

    fun recordPacket(isUpstream: Boolean, length: Int) {
        if (isUpstream) {
            bytesUp.addAndGet(length.toLong())
            pktsUp.incrementAndGet()
        } else {
            bytesDown.addAndGet(length.toLong())
            pktsDown.incrementAndGet()
        }
    }

    fun updateProbeMetrics(metrics: ProbeMetrics) {
        probeMetrics = metrics
    }

    private suspend fun emitSnapshot() {
        val snapshot = MetricsSnapshot(
            timestampUtc = System.currentTimeMillis(),
            elapsedSeconds = (SystemClock.elapsedRealtime() - startElapsed) / 1000,
            flowsActive = 0,
            bytesUpPerSec = bytesUp.getAndSet(0),
            bytesDownPerSec = bytesDown.getAndSet(0),
            packetsUpPerSec = pktsUp.getAndSet(0),
            packetsDownPerSec = pktsDown.getAndSet(0),
            wifiRssiDbm = readWifiRssi(),
            wifiLinkSpeedMbps = readLinkSpeed(),
            rttMeanMs = probeMetrics.rttMeanMs,
            rttP95Ms = probeMetrics.rttP95Ms,
            jitterMs = probeMetrics.jitterMs,
            lossPercent = probeMetrics.lossPercent,
        )
        mutableMetrics.emit(snapshot)
        MetricsStore.update(snapshot)
        csvWriter?.append(snapshot, lastConfig)
    }

    private fun readWifiRssi(): Int? {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo?.rssi
        } catch (e: SecurityException) {
            null
        }
    }

    private fun readLinkSpeed(): Int? {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo?.linkSpeed
        } catch (e: SecurityException) {
            null
        }
    }

    private fun openLogFile(): File {
        val dir = context.getExternalFilesDir("logs") ?: context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        rotateLogs(dir)
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val name = "netlog_${formatter.format(Date())}.csv"
        return File(dir, name)
    }

    private fun rotateLogs(dir: File) {
        val files = dir.listFiles { file -> file.name.startsWith("netlog_") && file.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        val totalSize = files.sumOf { it.length() }
        if (files.size <= MAX_LOG_FILES && totalSize <= MAX_TOTAL_SIZE_BYTES) {
            return
        }
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
        var size = files.take(MAX_LOG_FILES).sumOf { it.length() }
        if (size > MAX_TOTAL_SIZE_BYTES) {
            files.takeLast(files.size - MAX_LOG_FILES).forEach {
                if (it.delete()) {
                    size -= it.length()
                }
            }
        }
    }

    private class CsvWriter(private val file: File) {
        private val writer = FileWriter(file, true)

        fun writeHeader() {
            if (fileIsEmpty()) {
                writer.appendLine(HEADER)
                writer.flush()
            }
        }

        fun append(snapshot: MetricsSnapshot, config: ShapingConfig) {
            try {
                writer.appendLine(
                    listOf(
                        snapshot.timestampUtc,
                        snapshot.elapsedSeconds,
                        snapshot.flowsActive,
                        snapshot.bytesUpPerSec,
                        snapshot.bytesDownPerSec,
                        snapshot.packetsUpPerSec,
                        snapshot.packetsDownPerSec,
                        snapshot.rttMeanMs,
                        snapshot.rttP95Ms,
                        snapshot.jitterMs,
                        snapshot.lossPercent,
                        config.latencyMs,
                        config.jitterMs,
                        config.packetLossPercent,
                        config.bandwidthUpKbps,
                        config.bandwidthDownKbps,
                        config.targetPackage.replace(",", " "),
                        config.notes.replace(",", " "),
                        if (config.shapingEnabled) 1 else 0,
                        if (config.loggingEnabled) 1 else 0,
                        snapshot.wifiRssiDbm ?: "",
                        snapshot.wifiLinkSpeedMbps ?: "",
                    ).joinToString(separator = ","),
                )
                writer.flush()
            } catch (ioe: IOException) {
                // We intentionally swallow logging errors to avoid breaking the VPN session.
            }
        }

        private fun fileIsEmpty(): Boolean = file.length() == 0L

        fun close() {
            writer.close()
        }

        companion object {
            private const val HEADER =
                "ts_utc,elapsed_s,flows_active,bytes_up_s,bytes_down_s,pkts_up_s,pkts_down_s," +
                    "rtt_ms_mean,rtt_ms_p95,jitter_ms,loss_pct,lat_ms,jit_ms,loss_pct_cfg," +
                    "bw_up_kbps,bw_down_kbps,target_pkg,notes,shaping_enabled,logging_enabled,wifi_rssi_dbm,link_speed_mbps"
        }
    }

    companion object {
        private const val MAX_LOG_FILES = 10
        private const val MAX_TOTAL_SIZE_BYTES = 100L * 1024 * 1024
    }
}
