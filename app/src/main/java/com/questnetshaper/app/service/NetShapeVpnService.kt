package com.questnetshaper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.questnetshaper.app.data.ConfigStore
import com.questnetshaper.app.metrics.ActiveProbe
import com.questnetshaper.app.metrics.MetricsAggregator
import com.questnetshaper.app.model.ProbeMetrics
import com.questnetshaper.app.model.ShapingConfig
import com.questnetshaper.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.net.InetAddress

class NetShapeVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packetShaper = PacketShaper()
    private lateinit var metricsAggregator: MetricsAggregator
    private lateinit var activeProbe: ActiveProbe

    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentConfig: ShapingConfig = ShapingConfig()
    private var readerJob: Job? = null
    private var configJob: Job? = null
    private var probeHost: String = ""

    override fun onCreate() {
        super.onCreate()
        metricsAggregator = MetricsAggregator(this, serviceScope)
        metricsAggregator.startLogging(ConfigStore.config)
        activeProbe = ActiveProbe(serviceScope) { mean, p95, jitter, loss ->
            metricsAggregator.updateProbeMetrics(ProbeMetrics(mean, p95, jitter, loss))
        }
        startForeground(NOTIFICATION_ID, createNotification())
        observeConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
            else -> {
                startVpn()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        metricsAggregator.stopLogging()
        activeProbe.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun observeConfig() {
        configJob?.cancel()
        configJob = serviceScope.launch {
            ConfigStore.config.collectLatest { config ->
                currentConfig = config
                packetShaper.updateConfig(config)
                updateProbe(config)
                updateNotification(config)
            }
        }
    }

    private fun updateProbe(config: ShapingConfig) {
        val host = config.probeHost.ifBlank { ShapingConfig.DEFAULT_PROBE_HOST }
        if (host != probeHost) {
            probeHost = host
            activeProbe.stop()
            activeProbe.start(host)
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        val builder = Builder()
            .setSession("QuestNetShaper")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
        val targetPackage = currentConfig.targetPackage.ifBlank { ShapingConfig.DEFAULT_TARGET_PACKAGE }
        if (targetPackage.isNotBlank()) {
            try {
                packageManager.getApplicationInfo(targetPackage, 0)
                builder.addAllowedApplication(targetPackage)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Target package $targetPackage not installed; aborting VPN start")
                return
            }
        }
        try {
            builder.addDnsServer(InetAddress.getByName("1.1.1.1"))
        } catch (_: Exception) {
            // DNS optional
        }
        vpnInterface = builder.establish()
        readerJob?.cancel()
        readerJob = serviceScope.launch { runPacketLoop() }
    }

    private suspend fun runPacketLoop() = withContext(Dispatchers.IO) {
        val descriptor = vpnInterface ?: return@withContext
        val input = FileInputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (true) {
            val length = try {
                input.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length <= 0) continue
            val packet = buffer.copyOf(length)
            metricsAggregator.recordPacket(true, length)
            packetShaper.shape(packet, length, PacketDirection.UPSTREAM, currentConfig) { _, _ ->
                // Forwarding to the network stack is outside the scope of this sample implementation.
            }
        }
    }

    private fun stopVpn() {
        readerJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun createNotification(): Notification {
        val channelId = ensureChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Quest Network Shaper")
            .setContentText("VPN active")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(config: ShapingConfig) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ensureChannel()
        val text = buildString {
            append(if (config.shapingEnabled) "Shaping on" else "Shaping off")
            append(" • ")
            append(if (config.loggingEnabled) "Logging" else "No logging")
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Quest Network Shaper")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(): String {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "questnetshaper"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Quest Network Shaper",
                    NotificationManager.IMPORTANCE_LOW,
                )
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    companion object {
        private const val MAX_PACKET_SIZE = 16 * 1024
        const val ACTION_STOP = "com.questnetshaper.app.action.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "NetShapeVpnService"
    }
}
