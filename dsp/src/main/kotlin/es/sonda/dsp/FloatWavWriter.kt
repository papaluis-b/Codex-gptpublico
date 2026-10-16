package es.sonda.dsp

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** RIFF IEEE float32, original normalized samples. Called only by a disk worker. */
class FloatWavWriter(file: File, val sampleRate: Int, val channels: Int = 1) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private val bytes = ByteArray(1024 * channels * 4)
    var sampleCount = 0L
        private set
    private var closed = false
    init {
        require(sampleRate > 0 && channels in 1..2)
        output.setLength(0)
        output.write(ByteArray(56))
    }
    fun write(samples: FloatArray, count: Int) {
        check(!closed)
        require(count in 0..samples.size && count % channels == 0)
        require((sampleCount + count) * 4 <= 0xffffffffL - 48)
        var offset = 0
        while (offset < count) {
            val length = minOf(count - offset, bytes.size / 4)
            for (i in 0 until length) {
                val bits = samples[offset + i].toRawBits()
                for (b in 0..3) bytes[i * 4 + b] = (bits ushr (8 * b)).toByte()
            }
            output.write(bytes, 0, length * 4)
            offset += length
        }
        sampleCount += count
    }
    override fun close() {
        if (closed) return
        closed = true
        try {
            val dataBytes = sampleCount * 4
            output.seek(0)
            output.writeBytes("RIFF"); le32(dataBytes + 48); output.writeBytes("WAVE")
            output.writeBytes("fmt "); le32(16); le16(3); le16(channels)
            le32(sampleRate.toLong()); le32(sampleRate.toLong() * channels * 4)
            le16(channels * 4); le16(32)
            output.writeBytes("fact"); le32(4); le32(sampleCount / channels)
            output.writeBytes("data"); le32(dataBytes)
        } finally { output.close() }
    }
    private fun le16(value: Int) { output.write(value and 255); output.write((value ushr 8) and 255) }
    private fun le32(value: Long) { repeat(4) { output.write(((value ushr (it * 8)) and 255).toInt()) } }
}
