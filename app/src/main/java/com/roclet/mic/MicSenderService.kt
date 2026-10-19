package com.roclet.mic

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
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class MicSenderService : Service() {

    /** Lifecycle state of the streaming service, published to bound UI listeners. */
    enum class State { STOPPED, STARTING, STREAMING, STOPPING, ERROR }

    /** Callback delivered on the main thread with the current state and last error. */
    fun interface StateListener {
        fun onStateChanged(state: State, error: String?)
    }

    private val binder = LocalBinder()

    @Volatile
    private var activeRun: StreamingRun? = null

    @Volatile
    private var state: State = State.STOPPED

    @Volatile
    private var lastError: String? = null

    private val stateListeners = CopyOnWriteArrayList<StateListener>()

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    inner class LocalBinder : Binder() {
        val isRunning: Boolean
            get() = activeRun != null

        val state: State
            get() = this@MicSenderService.state

        val lastError: String?
            get() = this@MicSenderService.lastError

        fun addStateListener(listener: StateListener) {
            if (!stateListeners.contains(listener)) {
                stateListeners.add(listener)
            }
            listener.onStateChanged(this@MicSenderService.state, this@MicSenderService.lastError)
        }

        fun removeStateListener(listener: StateListener) {
            stateListeners.remove(listener)
        }
    }

    private fun setState(newState: State, error: String? = null) {
        state = newState
        lastError = error
        for (listener in stateListeners) {
            listener.onStateChanged(newState, error)
        }
    }

    /**
     * Owns the resources of a single streaming run. The audio worker is the only
     * component allowed to release the recorder or close the ROC sender, and it does
     * so in its own `finally` path after the recording loop has exited.
     */
    private class StreamingRun(
        val record: AudioRecord,
        val sender: RocAudioSender,
        val bufferSamples: Int
    ) {
        @Volatile
        var cancelled = false
            private set

        /** Human-readable reason set by the worker when capture fails; null on a normal stop. */
        @Volatile
        var failureMessage: String? = null

        var thread: Thread? = null

        private val released = AtomicBoolean(false)

        /** Signals this run to stop and unblocks a blocking [AudioRecord.read]. */
        fun requestStop() {
            cancelled = true
            try {
                record.stop()
            } catch (_: Throwable) {
            }
        }

        /** Releases native resources. Safe to call more than once; only the first call acts. */
        fun releaseResources() {
            if (!released.compareAndSet(false, true)) return
            try {
                record.stop()
            } catch (_: Throwable) {
            }
            try {
                record.release()
            } catch (_: Throwable) {
            }
            try {
                sender.close()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        val ip = intent?.getStringExtra(EXTRA_DEST_IP) ?: ""
        val sourcePort = intent?.getIntExtra(EXTRA_SOURCE_PORT, RocAudioSender.DEFAULT_SOURCE_PORT)
            ?: RocAudioSender.DEFAULT_SOURCE_PORT
        val repairPort = intent?.getIntExtra(EXTRA_REPAIR_PORT, RocAudioSender.DEFAULT_REPAIR_PORT)
            ?: RocAudioSender.DEFAULT_REPAIR_PORT

        when (action) {
            ACTION_START -> {
                if (activeRun == null && ip.isNotEmpty()) {
                    startStreaming(ip, sourcePort, repairPort)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(ip: String, sourcePort: Int, repairPort: Int) {
        if (activeRun != null) return

        setState(State.STARTING)

        var record: AudioRecord? = null
        var sender: RocAudioSender? = null
        var run: StreamingRun? = null
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

            val createdRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufSize * 2
            )
            record = createdRecord

            val createdSender = RocAudioSender(destIp = ip, sourcePort = sourcePort, repairPort = repairPort, sampleRate = sampleRate)
            sender = createdSender
            createdSender.start()

            createdRecord.startRecording()

            val startedRun = StreamingRun(createdRecord, createdSender, minBufSize)
            run = startedRun
            startedRun.thread = Thread { runWorker(startedRun) }.apply {
                name = "MicSenderAudioThread"
                priority = Thread.MAX_PRIORITY
            }

            activeRun = startedRun
            startedRun.thread?.start()
            setState(State.STREAMING)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start streaming", t)
            val partiallyStartedRun = run
            if (partiallyStartedRun != null) {
                activeRun = null
                partiallyStartedRun.releaseResources()
            } else {
                try {
                    record?.release()
                } catch (_: Throwable) {
                }
                try {
                    sender?.close()
                } catch (_: Throwable) {
                }
            }
            releaseLocks()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) {
            }
            setState(State.ERROR, getString(R.string.error_start_failed))
            stopSelf()
        }
    }

    private fun runWorker(run: StreamingRun) {
        val record = run.record
        val sender = run.sender
        try {
            val shortBuf = ShortArray(run.bufferSamples)
            val stereoBuf = FloatArray(run.bufferSamples * 2)
            while (!run.cancelled) {
                val readSamples = record.read(shortBuf, 0, shortBuf.size)
                when {
                    readSamples > 0 -> {
                        for (i in 0 until readSamples) {
                            val f = shortBuf[i] / 32768.0f
                            stereoBuf[i * 2] = f
                            stereoBuf[i * 2 + 1] = f
                        }
                        val samples = if (readSamples == run.bufferSamples) {
                            stereoBuf
                        } else {
                            stereoBuf.copyOf(readSamples * 2)
                        }
                        sender.sendFloats(samples)
                    }
                    readSamples < 0 -> {
                        if (!run.cancelled) {
                            Log.e(TAG, "AudioRecord.read() failed with error $readSamples")
                            run.failureMessage = getString(R.string.error_capture_failed)
                        }
                        break
                    }
                    else -> {
                        try {
                            Thread.sleep(ZERO_READ_DELAY_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio worker failed", e)
            if (!run.cancelled) {
                run.failureMessage = getString(R.string.error_stream_failed)
            }
        } finally {
            run.releaseResources()
            mainExecutor.execute { finishRun(run) }
        }
    }

    private fun finishRun(run: StreamingRun) {
        if (activeRun !== run) return
        activeRun = null
        releaseLocks()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        val failure = run.failureMessage
        if (failure != null) {
            setState(State.ERROR, failure)
        } else {
            setState(State.STOPPED)
        }
        stopSelf()
    }

    private fun stopStreaming() {
        val run = activeRun
        if (run != null) {
            setState(State.STOPPING)
            run.requestStop()
        } else {
            releaseLocks()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) {
            }
            setState(State.STOPPED)
            stopSelf()
        }
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
        const val EXTRA_SOURCE_PORT = "EXTRA_SOURCE_PORT"
        const val EXTRA_REPAIR_PORT = "EXTRA_REPAIR_PORT"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "roc_mic_channel"
        private const val TAG = "MicSenderService"
        private const val ZERO_READ_DELAY_MS = 5L
    }
}
