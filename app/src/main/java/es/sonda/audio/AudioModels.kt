package es.sonda.audio

import android.media.AudioDeviceInfo
import android.os.Build
import es.sonda.dsp.Spectrum
import es.sonda.dsp.GeneratorSettings
import es.sonda.dsp.SignalKind

data class AudioDevice(val id: Int, val name: String, val kind: Int, val input: Boolean, val capabilities: String)
fun AudioDeviceInfo.describe() = AudioDevice(
    id, productName.toString(), type, isSource,
    "Hz declarados: ${sampleRates.joinToString().ifEmpty { "dinámicos" }} · canales: ${channelCounts.joinToString().ifEmpty { "dinámicos" }}",
)
fun AudioDeviceInfo.isUsb() = type in setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
val BluetoothOutputTypes: Set<Int> = buildSet {
    add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
    if (Build.VERSION.SDK_INT >= 31) {
        add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
    }
}
fun AudioDeviceInfo.isBluetoothOutput() = type in BluetoothOutputTypes
fun AudioDeviceInfo.routeName() = "${productName} (#$id, tipo $type)"

data class AudioState(
    val running: Boolean = false,
    val preparing: Boolean = false,
    val simulation: Boolean = false,
    val emitting: Boolean = false,
    val recording: Boolean = false,
    val message: String = "Listo. Conecta un DAC para emitir o inicia solo el análisis.",
    val requestedInput: String = "Micrófono integrado",
    val requestedOutput: String = "Sin salida",
    val activeInput: String = "Sin abrir",
    val activeOutput: String = "Sin abrir",
    val inputId: Int? = null,
    val outputId: Int? = null,
    val inputRate: Int = 0,
    val outputRate: Int = 0,
    val inputChannels: Int = 0,
    val outputChannels: Int = 0,
    val inputBufferFrames: Int = 0,
    val outputBufferFrames: Int = 0,
    val routingVerified: Boolean = false,
    val unprocessedDeclared: Boolean = false,
    val source: String = "Sin abrir",
    val effects: String = "Sin comprobar",
    val captureFrames: Long = 0,
    val outputFramesWritten: Long = 0,
    val droppedFrames: Long = 0,
    val discontinuities: Long = 0,
    val clippedSamples: Long = 0,
    val underruns: Int = 0,
    val inputTimestamp: String = "No disponible",
    val outputTimestamp: String = "No disponible",
    val inputTimestampFrame: Long? = null,
    val inputTimestampNs: Long? = null,
    val outputTimestampFrame: Long? = null,
    val outputTimestampNs: Long? = null,
    val frequencyHz: Double = 1000.0,
    val levelDbFs: Double = -40.0,
    val signalKind: SignalKind = SignalKind.SINE,
    val octaveDivision: Int = 3,
    val noiseLowerHz: Double? = null,
    val noiseUpperHz: Double? = null,
    val noiseSettlingSeconds: Double? = null,
    val generatorClippedSamples: Long = 0,
    val mediaVolume: Int = 0,
    val mediaVolumeMax: Int = 0,
    val events: List<AudioEvent> = emptyList(),
) {
    val generatorSettings get() = GeneratorSettings(signalKind, frequencyHz, levelDbFs, octaveDivision)
}
data class AudioEvent(val monotonicNs: Long, val description: String)
data class AnalysisFrame(val spectrum: Spectrum, val firstCaptureFrame: Long, val monotonicNs: Long)
