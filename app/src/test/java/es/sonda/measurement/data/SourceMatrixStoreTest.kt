package es.sonda.measurement.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceMatrixStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var store: SourceMatrixStore
    private val raw = floatArrayOf(0f, 0.125f, -0.25f, 0.5f)

    @Before fun createStore() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
        }
        store = SourceMatrixStore(context)
    }

    @Test fun restartRetainsTenSlotsSpectrumRawAndEngineTimingWithoutAliasingCallerArrays() {
        assertEquals((1..10).toList(), store.slots().map { it.number })
        assertTrue(store.slots().all { it.measurement == null })
        val source = measurement()
        val metadata = JSONObject().put("firstCaptureFrame", 123456L).put("outputOriginFrame", 345L)
            .put("levels", JSONObject().put("excitationDbFsPeak", -24.0))
        val result = save(4, source, metadata, listOf(raw.reversedArray()))
        source.frequenciesHz[0] = 999.0
        source.magnitudeDb[0] = 99.0
        metadata.put("firstCaptureFrame", -1)
        assertEquals("Fuente 4", result.name)

        val restarted = SourceMatrixStore(context).slots()
        val loaded = restarted[3].measurement!!
        assertEquals(10, restarted.size)
        assertEquals(source.id, loaded.id)
        assertEquals(source.createdAt, loaded.createdAt)
        assertEquals(source.synthetic, loaded.synthetic)
        assertArrayEquals(doubleArrayOf(100.0, 1000.0, 10000.0), loaded.frequenciesHz, 0.0)
        assertArrayEquals(doubleArrayOf(-2.0, 0.0, -6.0), loaded.magnitudeDb, 0.0)
        assertEquals(source.warnings, loaded.warnings)
        assertTrue(restarted.filter { it.number != 4 }.all { it.measurement == null })
        val directory = record(source.id)
        val stored = JSONObject(File(directory, "metadata.json").readText())
        assertEquals(123456L, stored.getJSONObject("engine").getLong("firstCaptureFrame"))
        assertEquals(345L, stored.getJSONObject("engine").getLong("outputOriginFrame"))
        assertEquals(4, stored.getJSONArray("rawFiles").length())
        assertTrue(stored.getJSONArray("rawFiles").getJSONObject(0).getString("sha256").matches(Regex("[0-9a-f]{64}")))
        assertEquals("observed-uncompensated", stored.getJSONObject("timing").getString("kind"))
        assertTrue(stored.getJSONObject("timing").isNull("compensationMs"))
        assertArrayEquals(raw, readFloatWav(File(directory, "capture.wav")), 0f)
        assertArrayEquals(raw.reversedArray(), readFloatWav(File(directory, "capture-repeat-1.wav")), 0f)
        assertEquals("frequency_hz,magnitude_db", File(directory, "spectrum.csv").readLines().first())
    }

    @Test fun overwriteAndClearKeepHistoricalAudioAndReusingUuidCannotReplaceIt() {
        val first = measurement()
        save(1, first)
        val original = File(record(first.id), "capture.wav").readBytes()
        val second = measurement(delay = 8.0)
        save(1, second)
        assertEquals(second.id, SourceMatrixStore(context).slots()[0].measurement!!.id)
        assertThrows(IllegalStateException::class.java) { save(2, first) }
        assertArrayEquals(original, File(record(first.id), "capture.wav").readBytes())
        store.clear(1)
        assertTrue(SourceMatrixStore(context).slots().all { it.measurement == null })
        assertTrue(File(record(first.id), "metadata.json").isFile)
        assertTrue(File(record(second.id), "metadata.json").isFile)
    }

    @Test fun corruptMatrixIsReportedAndNeitherSaveNorClearEraseTheEvidence() {
        val original = measurement()
        save(3, original)
        val matrix = File(context.filesDir, "source-matrix/matrix.json")
        val corrupt = "{\"slots\": [ damaged JSON with recovery evidence"
        matrix.writeText(corrupt)
        val replacement = measurement()
        val failure = assertThrows(IOException::class.java) { SourceMatrixStore(context).slots() }
        assertTrue(failure.message!!.contains("No se ha sustituido ni borrado"))
        assertThrows(IOException::class.java) { save(3, replacement) }
        assertThrows(IOException::class.java) { store.clear(3) }
        assertEquals(corrupt, matrix.readText())
        assertFalse(record(replacement.id).exists())
        assertTrue(File(record(original.id), "capture.wav").isFile)
    }

    @Test fun syntacticallyValidButIncoherentMatrixDoesNotBecomeAnEmptySlot() {
        save(1, measurement())
        val matrix = File(context.filesDir, "source-matrix/matrix.json")
        val json = JSONObject(matrix.readText())
        json.getJSONArray("slots").getJSONObject(0).getJSONObject("measurement").getJSONArray("frequenciesHz").put(1, -100)
        matrix.writeText(json.toString())
        assertThrows(IOException::class.java) { store.slots() }
        assertTrue(matrix.readText().contains("-100"))
    }

    @Test fun invalidArgumentsCannotPublishARecordOrChangeTheMatrix() {
        val source = measurement()
        listOf(0, 11, -1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { save(invalid, source) }
            assertThrows(IllegalArgumentException::class.java) { store.clear(invalid) }
        }
        val invalidSources = listOf(
            source.copy(id = "../escape"), source.copy(createdAt = "not-a-date"),
            source.copy(observedDelayMs = Double.NaN), source.copy(observedDelayMs = -1.0),
            source.copy(sampleRate = 0), source.copy(frequenciesHz = doubleArrayOf(100.0)),
            source.copy(frequenciesHz = doubleArrayOf(100.0, 100.0, 10000.0)),
            source.copy(frequenciesHz = doubleArrayOf(100.0, 1000.0, 50000.0)),
            source.copy(magnitudeDb = doubleArrayOf(0.0, Double.NEGATIVE_INFINITY, 1.0)),
            source.copy(comparisonKey = "")
        )
        invalidSources.forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { save(1, invalid) } }
        assertThrows(IllegalArgumentException::class.java) {
            store.save(1, source, raw, floatArrayOf(Float.NaN), raw, JSONObject())
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save(1, source, raw, raw, raw, JSONObject(), listOf(FloatArray(0)))
        }
        assertTrue(store.slots().all { it.measurement == null })
        assertEquals(0, File(context.filesDir, "source-matrix/records").listFiles()!!.size)
    }

    @Test fun exportContainsAllHistoricalRawAndOnlyPortableRootedPaths() {
        val first = measurement()
        save(1, first, JSONObject().put("note", "../../only-metadata-not-a-path"), listOf(raw, raw))
        val second = measurement()
        save(1, second)
        store.clear(1)
        save(10, measurement(delay = 20.0))
        val archive = store.export()
        assertEquals(File(context.cacheDir, "exports").canonicalFile, archive.parentFile.canonicalFile)
        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(names.size, names.distinct().size)
            assertTrue(names.all { !it.startsWith("/") && !it.contains("..") && !it.contains('\\') })
            assertTrue(names.containsAll(listOf("matrix.json", "manifest.json",
                "records/${first.id}/excitation.wav", "records/${first.id}/capture.wav",
                "records/${first.id}/impulse.wav", "records/${first.id}/capture-repeat-1.wav",
                "records/${first.id}/capture-repeat-2.wav", "records/${first.id}/spectrum.csv",
                "records/${first.id}/metadata.json", "records/${second.id}/capture.wav")))
            val matrix = JSONObject(zip.getInputStream(zip.getEntry("matrix.json")).bufferedReader().readText())
            assertTrue(matrix.getJSONArray("slots").getJSONObject(0).isNull("measurement"))
            assertFalse(matrix.getJSONArray("slots").getJSONObject(9).isNull("measurement"))
            val rawZip = zip.getInputStream(zip.getEntry("records/${first.id}/capture.wav")).readBytes()
            assertArrayEquals(File(record(first.id), "capture.wav").readBytes(), rawZip)
        }
    }

    @Test fun exportRefusesARecordFileSymlinkEscapingItsUuidDirectory() {
        val source = measurement()
        save(1, source)
        val outside = temporary.newFile("private-outside-record.txt").apply { writeText("must-not-be-exported") }
        Files.createSymbolicLink(File(record(source.id), "capture-repeat-99.wav").toPath(), outside.toPath())
        assertThrows(IllegalStateException::class.java) { store.export() }
    }

    @Test fun differencesUseChosenReferenceAndNeverMixRoutesOrRealAndSyntheticMeasurements() {
        val slots = listOf(
            SourceSlot(1, measurement(delay = 10.0)),
            SourceSlot(2, measurement(delay = 15.0)),
            SourceSlot(3, measurement(delay = 8.0, key = "different-route")),
            SourceSlot(4, measurement(delay = 11.0).copy(synthetic = true)),
            SourceSlot(5)
        )
        val first = compareSources(slots, 1)
        assertEquals(3.43, first[0].equivalentMeters!!, 1e-12)
        assertEquals(5.0, first[1].differenceMs!!, 0.0)
        assertEquals(1.715, first[1].differenceMeters!!, 1e-12)
        assertFalse(first[2].comparable)
        assertNull(first[2].differenceMs)
        assertEquals(8.0, first[2].observedMs!!, 0.0)
        assertFalse(first[3].comparable)
        assertNull(first[3].differenceMeters)
        assertNull(first[4].observedMs)
        assertEquals(-5.0, compareSources(slots, 2)[0].differenceMs!!, 0.0)
        assertTrue(compareSources(slots, 5).all { !it.comparable && it.differenceMs == null })
        assertThrows(IllegalArgumentException::class.java) { compareSources(slots, 0) }
        assertThrows(IllegalArgumentException::class.java) { compareSources(slots, 1, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { compareSources(slots + slots.first(), 1) }
    }

    private fun save(slot: Int, source: SourceMeasurement, metadata: JSONObject = JSONObject(), additional: List<FloatArray> = emptyList()) =
        store.save(slot, source, raw, raw, raw, metadata, additional)

    private fun measurement(delay: Double = 10.0, key: String = "same-input-output-config") = SourceMeasurement(
        UUID.randomUUID().toString(), "2026-10-18T12:34:56.123Z", "SWEEP", 48000, delay, key,
        "Micrófono USB", "DAC USB", false, doubleArrayOf(100.0, 1000.0, 10000.0),
        doubleArrayOf(-2.0, 0.0, -6.0), listOf("Retardo observado sin compensación"))

    private fun record(id: String) = File(context.filesDir, "source-matrix/records/$id")

    private fun readFloatWav(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(3, buffer.getShort(20).toInt())
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(48000, buffer.getInt(24))
        assertEquals(32, buffer.getShort(34).toInt())
        assertEquals("data", String(bytes, 48, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 56, buffer.getInt(52))
        return FloatArray((bytes.size - 56) / 4) { buffer.getFloat(56 + it * 4) }
    }
}
