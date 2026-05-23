package be.tarsos.dsp.io.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream

object AudioDispatcherFactory {
    @SuppressLint("MissingPermission")
    fun fromDefaultMicrophone(sampleRate: Int, audioBufferSize: Int, bufferOverlap: Int): AudioDispatcher {
        val minAudioBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val minAudioBufferSizeInSamples = minAudioBufferSize / 2
        
        // Use the larger of the requested buffer size and the minimum required by the hardware
        val actualBufferSize = if (minAudioBufferSizeInSamples <= audioBufferSize) audioBufferSize else minAudioBufferSizeInSamples
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize * 2
        )
        
        val format = TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val stream = AndroidAudioInputStream(audioRecord, format)
        audioRecord.startRecording()
        return AudioDispatcher(stream, audioBufferSize, bufferOverlap)
    }
}

class AndroidAudioInputStream(private val audioRecord: AudioRecord, private val format: TarsosDSPAudioFormat) : TarsosDSPAudioInputStream {
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return audioRecord.read(b, off, len)
    }

    override fun skip(n: Long): Long {
        return 0
    }

    override fun close() {
        audioRecord.stop()
        audioRecord.release()
    }

    override fun getFormat(): TarsosDSPAudioFormat {
        return format
    }

    override fun getFrameLength(): Long {
        return -1
    }
}
