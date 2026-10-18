package es.sonda.measurement

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import es.sonda.measurement.data.SourceMatrixStore
import es.sonda.measurement.data.compareSources
import es.sonda.ui.MeasurementUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile

/** End-to-end synthetic orchestration only; Robolectric is not an audio measurement device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeasurementControllerTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var app: Application
    private lateinit var context: Context
    private val controllers = mutableListOf<MeasurementController>()
    private val pulse = MeasurementConfig(sampleRate = 8000, kind = MeasurementSignal.PULSE,
        lowHz = 80.0, highHz = 3200.0, durationSeconds = .08,
        preSilenceSeconds = .05, tailSeconds = .2)

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val manager = shadowOf(app.getSystemService(AudioManager::class.java))
        manager.setInputDevices(emptyList())
        manager.setOutputDevices(emptyList())
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        context = object : ContextWrapper(app) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
        }
    }

    @After fun tearDown() { controllers.forEach { it.close() } }

    @Test fun pulseDemoPersistsFiveRawCapturesAndRestartsWithAnHonestSelfContainedExport() {
        val controller = controller()
        controller.demo(pulse) { stopped -> stopped() }
        val completed = awaitState(controller) { !it.busy && it.result != null }
        assertTrue(completed.result!!.warnings.toString(), completed.canSave)
        assertTrue(completed.syntheticResult)
        assertEquals(5, completed.repeatDelaysMs.size)
        assertEquals(11.0, completed.result.delayMs, .1)
        assertEquals(0.0, completed.spreadMs!!, 0.0)
        assertIdleWithoutPhysicalAudio(controller)

        controller.save(3)
        val saved = awaitState(controller) { it.slots[2].measurement != null }.slots[2].measurement!!
        assertTrue(saved.synthetic)
        assertEquals(completed.result.delayMs, saved.observedDelayMs, 0.0)
        assertArrayEquals(completed.result.frequenciesHz, saved.frequenciesHz, 0.0)
        controller.close()

        val restarted = controller()
        val loaded = awaitState(restarted) { it.slots[2].measurement != null }.slots[2].measurement!!
        assertEquals(saved.id, loaded.id)
        assertEquals(saved.comparisonKey, loaded.comparisonKey)
        val archive = exportedBy(restarted)
        ZipFile(archive).use { zip ->
            val prefix = "records/${saved.id}/"
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(entries.containsAll(listOf("matrix.json", "manifest.json", "${prefix}excitation.wav",
                "${prefix}capture.wav", "${prefix}impulse.wav", "${prefix}spectrum.csv", "${prefix}metadata.json")))
            assertEquals(4, entries.count { it.startsWith("${prefix}capture-repeat-") && it.endsWith(".wav") })
            val record = JSONObject(zip.getInputStream(zip.getEntry("${prefix}metadata.json")).bufferedReader().readText())
            assertTrue(record.getJSONObject("source").getBoolean("synthetic"))
            val engine = record.getJSONObject("engine")
            assertTrue(engine.getBoolean("synthetic"))
            assertFalse(engine.getBoolean("absoluteDelayCalibrationValidated"))
            assertTrue(engine.isNull("compensationMs"))
            assertTrue(engine.isNull("compensatedDelayMs"))
            val repetitions = engine.getJSONArray("repetitions")
            assertEquals(5, repetitions.length())
            for (i in 0 until repetitions.length()) {
                val repetition = repetitions.getJSONObject(i)
                assertTrue(repetition.getBoolean("synthetic"))
                assertTrue(repetition.getBoolean("valid"))
                assertEquals(saved.observedDelayMs, repetition.getDouble("observedDelayMs"), 0.0)
                assertEquals((pulse.preSilenceSeconds * pulse.sampleRate).toInt(),
                    repetition.getJSONObject("signal").getInt("excitationStartFrame"))
                assertEquals(0, repetition.getInt("impulseStartCaptureFrame"))
                assertNotNull(zip.getEntry(prefix + repetition.getString("captureFile")))
            }
            val rawFiles = record.getJSONArray("rawFiles")
            assertEquals(7, rawFiles.length())
            for (i in 0 until rawFiles.length()) {
                val descriptor = rawFiles.getJSONObject(i)
                val bytes = zip.getInputStream(zip.getEntry(prefix + descriptor.getString("file"))).readBytes()
                val wav = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(8000, wav.getInt(24))
                assertEquals(descriptor.getInt("frames") * 4, wav.getInt(52))
                assertEquals(descriptor.getString("sha256"), sha256(bytes))
            }
        }
        assertIdleWithoutPhysicalAudio(restarted)
    }

    @Test fun sameProfileIsComparableWithinSessionButProfileChangeAndRestartNeedANewReference() {
        val first = controller()
        demoAndSave(first, pulse, 1)
        demoAndSave(first, pulse, 2)
        val sameSession = compareSources(first.state.value.slots, reference = 1)
        assertTrue(sameSession[1].comparable)
        assertEquals(1.0, sameSession[1].differenceMs!!, .05)
        assertEquals(.343, sameSession[1].differenceMeters!!, .02)

        demoAndSave(first, pulse.copy(levelDbFs = -36.0), 3)
        assertFalse(compareSources(first.state.value.slots, 1)[2].comparable)
        assertNull(compareSources(first.state.value.slots, 1)[2].differenceMs)
        first.close()

        val restarted = controller()
        awaitState(restarted) { it.slots[2].measurement != null }
        demoAndSave(restarted, pulse, 4)
        val comparisons = compareSources(restarted.state.value.slots, 1)
        assertTrue(comparisons[1].comparable)
        assertFalse(comparisons[3].comparable)
        assertNull(comparisons[3].differenceMeters)
        assertEquals(4, SourceMatrixStore(context).slots().count { it.measurement != null })
        assertIdleWithoutPhysicalAudio(restarted)
    }

    @Test fun stopWhileReleasingAnalyzerRejectsItsLateRealRequestCallbackAndCannotSaveOrEmit() {
        val controller = controller()
        val entered = CountDownLatch(1)
        val callback = AtomicReference<() -> Unit>()
        controller.request(pulse, input = null, output = 987654) { stopped ->
            callback.set(stopped)
            entered.countDown()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(controller.state.value.busy)
        controller.stop("Cancelada antes de abrir audio")
        callback.get().invoke()
        controller.save(1)
        drainAudio(controller)
        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.canSave)
        assertNull(controller.state.value.result)
        assertEquals("Cancelada antes de abrir audio", controller.state.value.message)
        assertTrue(SourceMatrixStore(context).slots().all { it.measurement == null })
        assertIdleWithoutPhysicalAudio(controller)
    }

    @Test fun aNewDemoOwnsTheResultEvenWhenAnEarlierAnalyzerCallbackArrivesAfterIt() {
        val controller = controller()
        val entered = CountDownLatch(1)
        val previousCallback = AtomicReference<() -> Unit>()
        controller.demo(pulse) { stopped -> previousCallback.set(stopped); entered.countDown() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        controller.demo(pulse.copy(levelDbFs = -36.0)) { stopped -> stopped() }
        val current = awaitState(controller) { !it.busy && it.result != null }
        assertTrue(current.result!!.warnings.toString(), current.canSave)
        previousCallback.get().invoke()
        controller.save(2)
        val saved = awaitState(controller) { it.slots[1].measurement != null }.slots[1].measurement!!
        assertEquals(current.result.delayMs, saved.observedDelayMs, 0.0)
        val metadata = JSONObject(File(context.filesDir,
            "source-matrix/records/${saved.id}/metadata.json").readText())
        assertEquals(-36.0, metadata.getJSONObject("engine").getJSONObject("signal").getDouble("levelDbFsPeak"), 0.0)
        assertEquals(1, SourceMatrixStore(context).slots().count { it.measurement != null })
        assertIdleWithoutPhysicalAudio(controller)
    }

    @Test fun lostAnalyzerReleaseCallbackExpiresWithoutLeavingBusyOrAcceptingItsLateCompletion() {
        val controller = MeasurementController(context, releaseTimeoutMs = 50).also { controllers += it }
        val entered = CountDownLatch(1)
        val callback = AtomicReference<() -> Unit>()
        controller.request(pulse, input = null, output = 987654) { stopped ->
            callback.set(stopped)
            entered.countDown()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val failed = awaitState(controller) { !it.busy && it.message.contains("No se pudo liberar el analizador") }
        assertFalse(failed.canSave)
        assertNull(failed.result)
        callback.get().invoke()
        controller.save(1)
        drainAudio(controller)
        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.canSave)
        assertNull(controller.state.value.result)
        assertEquals(failed.message, controller.state.value.message)
        assertTrue(SourceMatrixStore(context).slots().all { it.measurement == null })
        assertIdleWithoutPhysicalAudio(controller)
    }

    private fun controller() = MeasurementController(context).also { controllers += it }

    private fun demoAndSave(controller: MeasurementController, config: MeasurementConfig, slot: Int) {
        controller.demo(config) { stopped -> stopped() }
        val state = awaitState(controller) { !it.busy && it.result != null }
        assertTrue(state.result!!.warnings.toString(), state.canSave)
        controller.save(slot)
        awaitState(controller) { it.slots[slot - 1].measurement != null }
    }

    private fun awaitState(controller: MeasurementController, predicate: (MeasurementUiState) -> Boolean): MeasurementUiState =
        runBlocking { withTimeout(10000) { controller.state.first { predicate(it) } } }

    private fun exportedBy(controller: MeasurementController): File {
        val ready = CountDownLatch(1)
        val output = AtomicReference<File>()
        controller.export { output.set(it); ready.countDown() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (ready.count > 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            ready.await(20, TimeUnit.MILLISECONDS)
        }
        assertEquals("${controller.state.value.message}", 0L, ready.count)
        return output.get()
    }

    private fun drainAudio(controller: MeasurementController) {
        val drained = CountDownLatch(1)
        controller.audio.afterStopped { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
    }

    private fun assertIdleWithoutPhysicalAudio(controller: MeasurementController) {
        assertFalse(controller.audio.state.value.preparing)
        assertFalse(controller.audio.state.value.running)
        assertFalse(controller.audio.state.value.emitting)
        assertFalse(controller.audio.state.value.routingVerified)
        assertNull(shadowOf(app.getSystemService(AudioManager::class.java)).lastAudioFocusRequest)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
