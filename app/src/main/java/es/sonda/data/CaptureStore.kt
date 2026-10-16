package es.sonda.data

import android.content.Context
import android.os.Build
import es.sonda.audio.AnalysisFrame
import es.sonda.audio.AudioState
import es.sonda.dsp.FloatWavWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SavedCapture(val id: String, val title: String, val synthetic: Boolean, val frames: Long, val quality: String)

class CaptureStore(private val context: Context) {
    private val root = File(context.filesDir, "captures").apply { mkdirs() }

    inner class Recording(val directory: File, val startFrame: Long, private val initial: AudioState) {
        private val writer = FloatWavWriter(File(directory, "capture.wav"), initial.inputRate)
        private val createdAt = Instant.now().toString()
        val frames get() = writer.sampleCount
        fun write(samples: FloatArray, count: Int) = writer.write(samples, count)
        fun finish(final: AudioState, analysis: AnalysisFrame?, note: String) {
            writer.close()
            val gaps = final.droppedFrames - initial.droppedFrames
            val discontinuities = final.discontinuities - initial.discontinuities
            val clipped = final.clippedSamples - initial.clippedSamples
            val quality = when {
                initial.simulation -> "SINTÉTICO"
                note.isNotBlank() || gaps > 0 || discontinuities > 0 || clipped > 0 || final.underruns > initial.underruns -> "REVISAR"
                else -> "SIN INCIDENCIAS DETECTADAS · hardware sin validar"
            }
            val json = JSONObject().apply {
                put("schemaVersion", 1); put("id", directory.name); put("type", "audio-capture")
                put("synthetic", initial.simulation); put("createdAt", createdAt)
                put("timezone", ZoneId.systemDefault().id); put("appVersion", "0.1.0")
                put("algorithmVersion", "hann-psd-v1"); put("session", "Sesión local")
                put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                    .put("androidVersion", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT).put("oneUiVersion", JSONObject.NULL))
                put("capture", JSONObject().put("file", "capture.wav").put("sha256", sha256(File(directory, "capture.wav")))
                    .put("format", "IEEE_FLOAT_32_LE").put("frames", frames).put("startCaptureFrame", startFrame)
                    .put("sampleRate", initial.inputRate).put("channels", 1)
                    .put("droppedFramesDuringRecording", gaps).put("gapsFilled", false))
                put("audioAtStart", audioJson(initial)); put("audioAtEnd", audioJson(final))
                put("qualityStatus", quality); put("note", note)
                put("clippedSamplesDuringRecording", clipped); put("discontinuitiesDuringRecording", discontinuities)
                put("timingMethod", if (initial.simulation) "synthetic-frame-clock" else "platform-monotonic-uncompensated")
                put("compensationMs", JSONObject.NULL); put("compensatedDelayMs", JSONObject.NULL)
                put("events", JSONArray().apply {
                    final.events.forEach { put(JSONObject().put("monotonicNs", it.monotonicNs).put("description", it.description)) }
                })
            }
            analysis?.takeIf { it.firstCaptureFrame >= startFrame }?.let {
                File(directory, "spectrum.csv").bufferedWriter().use { out ->
                    out.write("frequency_hz,psd_fs_squared_per_hz,tone_amplitude_fs\n")
                    val s = it.spectrum
                    for (k in s.psd.indices) out.write("${k * s.binHz},${s.psd[k]},${s.toneAmplitude[k]}\n")
                }
                json.put("spectrum", JSONObject().put("file", "spectrum.csv").put("window", "periodic-Hann")
                    .put("fftSize", it.spectrum.size).put("firstCaptureFrame", it.firstCaptureFrame)
                    .put("monotonicNs", it.monotonicNs).put("units", "FS^2/Hz").put("smoothing", "none"))
            }
            val temporary = File(directory, "measurement.json.tmp")
            temporary.writeText(json.toString(2))
            check(temporary.renameTo(File(directory, "measurement.json")))
        }
    }

    fun begin(state: AudioState, startFrame: Long): Recording {
        check(root.usableSpace > 50L * 1024 * 1024) { "Menos de 50 MB libres para grabar" }
        val dir = File(root, UUID.randomUUID().toString())
        check(dir.mkdir())
        return Recording(dir, startFrame, state)
    }

    fun list(): List<SavedCapture> = root.listFiles().orEmpty().filter { File(it, "measurement.json").isFile }
        .sortedByDescending { File(it, "measurement.json").lastModified() }.mapNotNull { dir ->
            runCatching {
                val j = JSONObject(File(dir, "measurement.json").readText())
                SavedCapture(dir.name, j.getString("createdAt"), j.getBoolean("synthetic"),
                    j.getJSONObject("capture").getLong("frames"), j.getString("qualityStatus"))
            }.getOrNull()
        }

    fun export(id: String): File {
        require(UUID.fromString(id).toString() == id)
        val dir = File(root, id)
        check(File(dir, "measurement.json").exists()) { "Captura no disponible" }
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        // Bounded cache; originals remain in private storage.
        exports.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - 86400000 }.forEach { it.delete() }
        val output = File(exports, "sonda-$id.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            for (name in listOf("measurement.json", "capture.wav", "spectrum.csv")) {
                val file = File(dir, name)
                if (!file.isFile) continue
                zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("schemaVersion", 1).put("app", "SONDA 0.1.0")
                .put("measurement", "measurement.json").put("containsEnvironmentalAudio", true).toString(2).toByteArray())
            zip.closeEntry()
        }
        return output
    }

    private fun audioJson(state: AudioState) = JSONObject().apply {
        put("requestedInput", state.requestedInput); put("requestedOutput", state.requestedOutput)
        put("activeInput", state.activeInput); put("activeOutput", state.activeOutput)
        put("routingVerified", state.routingVerified); put("inputRate", state.inputRate); put("outputRate", state.outputRate)
        put("inputChannels", state.inputChannels); put("outputChannels", state.outputChannels)
        put("inputBufferFrames", state.inputBufferFrames); put("outputBufferFrames", state.outputBufferFrames)
        put("format", "PCM_FLOAT"); put("source", state.source); put("unprocessedSupportDeclared", state.unprocessedDeclared)
        put("processingVerifiedAbsent", false); put("effects", state.effects)
        put("inputTimestampStatus", state.inputTimestamp); put("outputTimestampStatus", state.outputTimestamp)
        put("inputTimestampFrame", state.inputTimestampFrame ?: JSONObject.NULL); put("inputTimestampNs", state.inputTimestampNs ?: JSONObject.NULL)
        put("outputTimestampFrame", state.outputTimestampFrame ?: JSONObject.NULL); put("outputTimestampNs", state.outputTimestampNs ?: JSONObject.NULL)
        put("inputFramesRead", state.captureFrames); put("outputFramesWritten", state.outputFramesWritten)
        put("outputUnderruns", state.underruns); put("mediaVolume", state.mediaVolume); put("mediaVolumeMax", state.mediaVolumeMax)
        put("generatorFrequencyHz", state.frequencyHz); put("generatorLevelDbFsPeak", state.levelDbFs)
        put("generatorEmitting", state.emitting); put("digitalClippedSamples", state.clippedSamples)
        put("platformInputLossDetection", "partial: timestamp checks; Android does not expose all capture overruns")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
