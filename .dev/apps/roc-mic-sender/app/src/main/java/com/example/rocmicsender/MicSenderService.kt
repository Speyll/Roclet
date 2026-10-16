package com.example.rocmicsender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MicSenderService : Service() {

    private lateinit var notificationManager: NotificationManager
    private var audioThread: Thread? = null
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var rocSender: RocAudioSender? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val isRunning: Boolean
            get() = isRunning
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION")
        val ip = intent?.getStringExtra("DEST_IP") ?: ""

        when (action) {
            "START" -> {
                if (isRunning) return android.app.Service.START_STICKY
                startStreaming(ip)
            }
            "STOP" -> {
                stopStreaming()
            }
        }
        return android.app.Service.START_STICKY
    }

    private fun startStreaming(ip: String) {
        try {
            createNotificationChannel()
            val notification = buildNotification()
            startForeground(1, notification)

            rocSender = RocAudioSender(ip, 10001, 10002)
            rocSender?.init()

            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1024)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            audioRecord?.startRecording()
            isRunning = true

            audioThread = Thread {
                val buffer = ByteArray(minBufferSize)
                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        rocSender?.sendAudio(buffer, read)
                    }
                }
            }
            audioThread?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopStreaming() {
        isRunning = false
        audioThread?.interrupt()
        audioThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        rocSender?.close()
        rocSender = null

        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "roc_sender_channel",
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_text)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MicSenderService::class.java).apply {
            putExtra("ACTION", "STOP")
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "roc_sender_channel")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_record)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_stop, getString(R.string.notification_stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}
