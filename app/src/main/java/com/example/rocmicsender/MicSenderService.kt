package com.example.rocmicsender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class MicSenderService : Service() {

    private val binder = LocalBinder()
    private var isRunning = false
    private var audioThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var rocSender: RocAudioSender? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    inner class LocalBinder : Binder() {
        val isRunning: Boolean
            get() = this@MicSenderService.isRunning
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        val ip = intent?.getStringExtra(EXTRA_DEST_IP) ?: ""

        when (action) {
            ACTION_START -> {
                if (!isRunning && ip.isNotEmpty()) {
                    startStreaming(ip)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(ip: String) {
        try {
            acquireLocks()
            createNotificationChannel()
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufSize * 2
            )
            audioRecord = record

            val sender = RocAudioSender(destIp = ip, sourcePort = 10001, repairPort = 10002, sampleRate = sampleRate)
            sender.start()
            rocSender = sender

            record.startRecording()
            isRunning = true

            audioThread = Thread {
                val shortBuf = ShortArray(minBufSize)
                while (isRunning) {
                    val readSamples = record.read(shortBuf, 0, shortBuf.size)
                    if (readSamples > 0) {
                        val stereoFloats = FloatArray(readSamples * 2)
                        for (i in 0 until readSamples) {
                            val f = shortBuf[i] / 32768.0f
                            stereoFloats[i * 2] = f
                            stereoFloats[i * 2 + 1] = f
                        }
                        sender.sendFloats(stereoFloats)
                    }
                }
            }.apply {
                name = "MicSenderAudioThread"
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            stopStreaming()
        }
    }

    private fun stopStreaming() {
        isRunning = false

        audioThread?.interrupt()
        audioThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null

        try {
            rocSender?.close()
        } catch (_: Throwable) {
        }
        rocSender = null

        releaseLocks()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RocMicSender::WakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RocMicSender::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {
        }
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_text)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MicSenderService::class.java).apply {
            putExtra(EXTRA_ACTION, ACTION_STOP)
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "EXTRA_ACTION"
        const val EXTRA_DEST_IP = "EXTRA_DEST_IP"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "roc_mic_channel"
    }
}
