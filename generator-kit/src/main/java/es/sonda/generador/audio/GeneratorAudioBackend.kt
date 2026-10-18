package es.sonda.generador.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Hardware boundary. The production backend opens AudioTrack only, never AudioRecord. */
internal interface GeneratorAudioBackend {
    fun outputs(): List<GeneratorOutputDevice>
    fun observeDevices(onChange: () -> Unit)
    fun requestFocus(onLoss: () -> Unit): Boolean
    fun abandonFocus()
    fun open(outputId: Int, onRouteChange: () -> Unit): GeneratorAudioSink
    fun mediaVolume(): Int
    fun mediaVolumeMax(): Int
    fun close()
}

internal interface GeneratorAudioSink {
    val sampleRate: Int
    val channels: Int
    val bufferFrames: Int
    val underruns: Int
    val routedDeviceId: Int?
    fun play()
    fun write(data: FloatArray, offset: Int, count: Int): Int
    fun pauseAndFlush()
    fun stop()
    fun release()
}

internal class AndroidGeneratorAudioBackend(context: Context) : GeneratorAudioBackend {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var focus: AudioFocusRequest? = null
    private var observer: (() -> Unit)? = null
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { observer?.invoke() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { observer?.invoke() }
    }

    override fun outputs() = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.allowed() }.map {
            GeneratorOutputDevice(it.id, "${it.productName} (#${it.id})", it.usb(), it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        }

    override fun observeDevices(onChange: () -> Unit) {
        observer = onChange
        manager.registerAudioDeviceCallback(callback, main)
    }

    override fun requestFocus(onLoss: () -> Unit): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if (change != AudioManager.AUDIOFOCUS_GAIN) onLoss() }, main).build()
        focus = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandonFocus() {
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun open(outputId: Int, onRouteChange: () -> Unit): GeneratorAudioSink {
        val device = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == outputId && it.allowed() }
            ?: error("La salida seleccionada ya no está disponible")
        val minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0) { "Salida estéreo float32 no disponible" }
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minimum * 2, 2048 * 8)).build()
        val listener = AudioRouting.OnRoutingChangedListener { onRouteChange() }
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Salida no inicializada" }
            check(track.setPreferredDevice(device)) { "Android rechazó la salida solicitada" }
            track.addOnRoutingChangedListener(listener, main)
            return AndroidGeneratorAudioSink(track, listener)
        } catch (failure: Exception) {
            track.release()
            throw failure
        }
    }

    override fun mediaVolume() = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    override fun mediaVolumeMax() = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    override fun close() { observer = null; manager.unregisterAudioDeviceCallback(callback); abandonFocus() }

    private fun AudioDeviceInfo.usb() = type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    private fun AudioDeviceInfo.allowed() = usb() || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}

private class AndroidGeneratorAudioSink(
    private val track: AudioTrack,
    private val listener: AudioRouting.OnRoutingChangedListener,
) : GeneratorAudioSink {
    override val sampleRate get() = track.sampleRate
    override val channels get() = track.channelCount
    override val bufferFrames get() = track.bufferSizeInFrames
    override val underruns get() = track.underrunCount
    override val routedDeviceId get() = track.routedDevice?.id
    override fun play() { track.play() }
    override fun write(data: FloatArray, offset: Int, count: Int) = track.write(data, offset, count, AudioTrack.WRITE_BLOCKING)
    override fun pauseAndFlush() { track.pause(); track.flush() }
    override fun stop() { track.stop() }
    override fun release() { track.removeOnRoutingChangedListener(listener); track.release() }
}
