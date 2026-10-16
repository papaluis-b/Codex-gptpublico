package es.sonda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import es.sonda.audio.AudioEngine
import es.sonda.data.CaptureStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile
import es.sonda.dsp.PinkNoise
import es.sonda.dsp.SignalKind
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioWorkflowTest {
    private lateinit var context: Context
    private lateinit var store: CaptureStore
    private lateinit var engine: AudioEngine

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "captures").deleteRecursively()
        store = CaptureStore(context)
        engine = AudioEngine(context, store)
    }
    @After fun finish() { engine.close() }

    @Test fun simulationCapturePersistsAndExportsExactAudioWithExplicitSyntheticFlag() {
        engine.start(null, true)
        await { engine.state.value.running && engine.analysis.value != null }
        assertTrue(engine.state.value.simulation)
        assertFalse(engine.state.value.routingVerified)
        assertEquals(48000, engine.analysis.value!!.spectrum.sampleRate)
        assertEquals(-23.0103, es.sonda.dsp.Spectrum.db(engine.analysis.value!!.spectrum.rms), 0.1)
        engine.toggleRecording()
        await { engine.state.value.recording }
        Thread.sleep(350)
        engine.toggleRecording()
        await { store.list().isNotEmpty() && !engine.state.value.recording }
        val item = CaptureStore(context).list().single()
        assertTrue(item.synthetic)
        assertTrue(item.frames >= 1024)
        ZipFile(store.export(item.id)).use { zip ->
            val json = JSONObject(zip.getInputStream(zip.getEntry("measurement.json")).reader().readText())
            assertTrue(json.getBoolean("synthetic"))
            assertEquals("SINTÉTICO", json.getString("qualityStatus"))
            assertTrue(json.isNull("compensationMs"))
            assertFalse(json.getJSONObject("audioAtStart").getBoolean("processingVerifiedAbsent"))
            val wav = zip.getInputStream(zip.getEntry("capture.wav")).readBytes()
            val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(item.frames * 4, buffer.getInt(52).toLong())
            assertEquals(48000, buffer.getInt(24))
            val digest = MessageDigest.getInstance("SHA-256").digest(wav).joinToString("") { "%02x".format(it) }
            assertEquals(digest, json.getJSONObject("capture").getString("sha256"))
            assertNotNull(zip.getEntry("spectrum.csv"))
            assertNotNull(zip.getEntry("manifest.json"))
        }
        engine.stop()
        await { !engine.state.value.running }
        assertFalse(engine.state.value.emitting)
    }

    @Test fun simulationCannotEmitAndStopCannotAutomaticallyRearm() {
        engine.start(null, true)
        await { engine.state.value.running }
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
        engine.stop("Prueba de interrupción")
        Thread.sleep(250)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        engine.emit(true)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun missingHardwareDoesNotSilentlyFallBackToAnotherMicrophoneOrOutput() {
        engine.start(987654, false)
        await { !engine.state.value.preparing && engine.state.value.message.startsWith("No se pudo iniciar") }
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.emitting)
        assertFalse(engine.state.value.routingVerified)
        assertNull(engine.analysis.value)
    }

    @Test fun cancellingQueuedStartsNeverLeavesAStreamRunning() {
        repeat(20) { engine.start(null, true); engine.stop() }
        Thread.sleep(500)
        assertFalse(engine.state.value.running)
        assertFalse(engine.state.value.preparing)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun pinkBandSettingsSurviveOpeningAndAreExportedWithRmsReference() {
        engine.setOctaveDivision(48)
        engine.setSignalKind(SignalKind.PINK_NOISE)
        engine.setTone(1000.0, -32.0)
        engine.start(null, true)
        await { engine.state.value.running && engine.analysis.value != null }
        assertEquals(SignalKind.PINK_NOISE, engine.state.value.signalKind)
        assertEquals(48, engine.state.value.octaveDivision)
        assertEquals(-32.0, engine.state.value.levelDbFs, 0.0)
        assertFalse(engine.state.value.emitting)
        engine.toggleRecording()
        await { engine.state.value.recording }
        Thread.sleep(350)
        engine.toggleRecording()
        await { store.list().isNotEmpty() && !engine.state.value.recording }
        ZipFile(store.export(store.list().single().id)).use { zip ->
            val json = JSONObject(zip.getInputStream(zip.getEntry("measurement.json")).reader().readText())
            val audio = json.getJSONObject("audioAtStart")
            val generator = audio.getJSONObject("generator")
            assertTrue(audio.isNull("generatorLevelDbFsPeak"))
            assertEquals(-32.0, audio.getDouble("generatorLevelDbFsRms"), 0.0)
            assertEquals("PINK_NOISE", generator.getString("kind"))
            assertEquals(48, generator.getInt("octaveDivision"))
            assertEquals(1000.0 / 2.0.pow(1.0 / 96), generator.getDouble("bandLowerHz"), 1e-8)
            assertEquals(1000.0 * 2.0.pow(1.0 / 96), generator.getDouble("bandUpperHz"), 1e-8)
            assertEquals(PinkNoise.DEFAULT_SEED, generator.getInt("seed"))
            assertEquals(PinkNoise.ALGORITHM, generator.getString("algorithm"))
            assertFalse(audio.getBoolean("generatorEmitting"))
        }
        engine.stop()
        engine.start(null, true)
        await { engine.state.value.running }
        assertEquals(SignalKind.PINK_NOISE, engine.state.value.signalKind)
        assertEquals(48, engine.state.value.octaveDivision)
        assertFalse(engine.state.value.emitting)
    }

    @Test fun stepControlsUseSelectedFractionAndPinkLevelHasItsOwnCap() {
        engine.setOctaveDivision(32)
        repeat(32) { engine.stepFrequency(1) }
        assertEquals(2000.0, engine.state.value.frequencyHz, 1e-8)
        repeat(32) { engine.stepFrequency(-1) }
        assertEquals(1000.0, engine.state.value.frequencyHz, 1e-8)
        engine.setTone(1000.0, -12.0)
        engine.setSignalKind(SignalKind.PINK_NOISE)
        assertEquals(-24.0, engine.state.value.levelDbFs, 0.0)
        assertFalse(engine.state.value.emitting)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Operation timed out; state=${engine.state.value}", condition())
    }
}
