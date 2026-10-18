package es.sonda.generador.audio

import es.sonda.dsp.GeneratorSettings

data class GeneratorOutputDevice(
    val id: Int,
    val name: String,
    val isUsb: Boolean,
    val isSpeaker: Boolean,
)

data class GeneratorOutputState(
    val settings: GeneratorSettings = GeneratorSettings(),
    val preparing: Boolean = false,
    val running: Boolean = false,
    val emitting: Boolean = false,
    val routingVerified: Boolean = false,
    val requestedOutput: String = "Sin seleccionar",
    val activeOutput: String = "Sin abrir",
    val outputId: Int? = null,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bufferFrames: Int = 0,
    val underruns: Int = 0,
    val clippedSamples: Long = 0,
    val mediaVolume: Int = 0,
    val mediaVolumeMax: Int = 0,
    val noiseLowerHz: Double? = null,
    val noiseUpperHz: Double? = null,
    val noiseSettlingSeconds: Double? = null,
    val message: String = "Selecciona una salida para preparar el generador.",
)
