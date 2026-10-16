package es.sonda.dsp

import java.util.concurrent.atomic.AtomicLong

/** One capture producer, one processing consumer. Never waits for the consumer. */
class AudioRing(val blockSize: Int = 1024, val capacity: Int = 128) {
    private val slots = Array(capacity) { FloatArray(blockSize) }
    private val lengths = IntArray(capacity)
    private val frames = LongArray(capacity)
    private val write = AtomicLong()
    private val read = AtomicLong()
    val droppedFrames = AtomicLong()
    var lastFirstFrame: Long = 0
        private set

    init { require(blockSize > 0 && capacity > 0) }

    fun offer(input: FloatArray, count: Int, firstFrame: Long): Boolean {
        require(count in 1..blockSize && input.size >= count)
        val next = write.get()
        if (next - read.get() >= capacity) {
            droppedFrames.addAndGet(count.toLong())
            return false
        }
        val slot = (next % capacity).toInt()
        input.copyInto(slots[slot], endIndex = count)
        lengths[slot] = count
        frames[slot] = firstFrame
        write.lazySet(next + 1)
        return true
    }

    fun poll(output: FloatArray): Int {
        require(output.size >= blockSize)
        val next = read.get()
        if (next >= write.get()) return 0
        val slot = (next % capacity).toInt()
        val count = lengths[slot]
        slots[slot].copyInto(output, endIndex = count)
        lastFirstFrame = frames[slot]
        read.lazySet(next + 1)
        return count
    }
    fun isEmpty() = read.get() >= write.get()
}
