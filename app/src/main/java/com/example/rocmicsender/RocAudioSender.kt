package com.example.rocmicsender

import org.rocstreaming.roctoolkit.ChannelSet
import org.rocstreaming.roctoolkit.Endpoint
import org.rocstreaming.roctoolkit.FrameEncoding
import org.rocstreaming.roctoolkit.Interface
import org.rocstreaming.roctoolkit.Protocol
import org.rocstreaming.roctoolkit.RocContext
import org.rocstreaming.roctoolkit.RocSender
import org.rocstreaming.roctoolkit.RocSenderConfig
import org.rocstreaming.roctoolkit.Slot

class RocAudioSender(
    private val destIp: String,
    private val sourcePort: Int = 10001,
    private val repairPort: Int = 10002,
    private val sampleRate: Int = 44100
) : AutoCloseable {

    private var context: RocContext? = null
    private var sender: RocSender? = null

    fun start() {
        check(sender == null) { "ROC sender has already been started" }

        val ctx = RocContext()
        var openedSender: RocSender? = null
        try {
            val config = RocSenderConfig.builder()
                .frameSampleRate(sampleRate)
                .frameChannels(ChannelSet.STEREO)
                .frameEncoding(FrameEncoding.PCM_FLOAT)
                .build()

            val snd = RocSender(ctx, config)
            openedSender = snd
            val sourceEndpoint = Endpoint.builder()
                .protocol(Protocol.RTP_RS8M_SOURCE)
                .host(destIp)
                .port(sourcePort)
                .build()

            val repairEndpoint = Endpoint.builder()
                .protocol(Protocol.RS8M_REPAIR)
                .host(destIp)
                .port(repairPort)
                .build()

            snd.connect(Slot.DEFAULT, Interface.AUDIO_SOURCE, sourceEndpoint)
            snd.connect(Slot.DEFAULT, Interface.AUDIO_REPAIR, repairEndpoint)

            context = ctx
            sender = snd
        } catch (t: Throwable) {
            try {
                openedSender?.close()
            } catch (_: Throwable) {
            }
            try {
                ctx.close()
            } catch (_: Throwable) {
            }
            throw t
        }
    }

    fun sendFloats(samples: FloatArray) {
        sender?.write(samples)
    }

    override fun close() {
        try {
            sender?.close()
        } catch (_: Throwable) {
        }
        sender = null

        try {
            context?.close()
        } catch (_: Throwable) {
        }
        context = null
    }
}
