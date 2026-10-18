package es.sonda.measurement.data

import android.content.Context
import android.util.AtomicFile
import es.sonda.dsp.FloatWavWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SourceMeasurement(
    val id: String,
    val createdAt: String,
    val signal: String,
    val sampleRate: Int,
    val observedDelayMs: Double,
    val comparisonKey: String,
    val inputName: String,
    val outputName: String,
    val bluetooth: Boolean,
    val frequenciesHz: DoubleArray,
    val magnitudeDb: DoubleArray,
    val warnings: List<String>,
    val synthetic: Boolean = false
)

data class SourceSlot(val number: Int, val measurement: SourceMeasurement? = null) {
    val name get() = "Fuente $number"
}

data class SourceComparison(
    val number: Int,
    val observedMs: Double?,
    val equivalentMeters: Double?,
    val differenceMs: Double?,
    val differenceMeters: Double?,
    val comparable: Boolean,
    val reason: String?
)

/** Equivalent acoustic lengths, without removing the latency of the input/output route. */
fun compareSources(
    slots: List<SourceSlot>,
    reference: Int,
    speedMetersPerSecond: Double = 343.0
): List<SourceComparison> {
    require(reference in 1..10) { "La referencia debe estar entre 1 y 10" }
    require(speedMetersPerSecond.isFinite() && speedMetersPerSecond > 0.0) { "Velocidad del sonido no válida" }
    require(slots.all { it.number in 1..10 } && slots.map { it.number }.distinct().size == slots.size) {
        "Los números de fuente deben ser únicos y estar entre 1 y 10"
    }
    val anchor = slots.firstOrNull { it.number == reference }?.measurement
    return slots.map { slot ->
        val source = slot.measurement
        require(source == null || source.observedDelayMs.isFinite() && source.observedDelayMs >= 0.0) {
            "El retardo observado no es válido"
        }
        val reason = when {
            source == null -> "Fuente sin medida"
            anchor == null -> "La fuente de referencia no tiene medida"
            source.synthetic != anchor.synthetic -> "No se comparan medidas reales y sintéticas"
            source.comparisonKey != anchor.comparisonKey -> "Las rutas o configuraciones de medida son diferentes"
            else -> null
        }
        val difference = if (reason == null) source!!.observedDelayMs - anchor!!.observedDelayMs else null
        SourceComparison(slot.number, source?.observedDelayMs,
            source?.observedDelayMs?.let { it * speedMetersPerSecond / 1000.0 },
            difference, difference?.let { it * speedMetersPerSecond / 1000.0 }, reason == null, reason)
    }
}

/** Disk-worker API. Audio callbacks must never call this store. Historical recordings are immutable. */
class SourceMatrixStore(context: Context) {
    private val root = File(context.filesDir, "source-matrix").apply { ensureDirectory(this) }
    private val records = File(root, "records").apply { ensureDirectory(this) }
    private val exports = File(context.cacheDir, "exports")
    private val matrix = AtomicFile(File(root, "matrix.json"))
    private val lock = locks.computeIfAbsent(root.canonicalPath) { Any() }

    fun slots(): List<SourceSlot> = synchronized(lock) { readSlots() }

    fun save(
        slot: Int,
        measurement: SourceMeasurement,
        excitation: FloatArray,
        captured: FloatArray,
        impulse: FloatArray,
        metadata: JSONObject,
        additionalCaptures: List<FloatArray> = emptyList()
    ): SourceSlot = synchronized(lock) {
        requireSlot(slot)
        val source = measurement.copy(frequenciesHz = measurement.frequenciesHz.copyOf(),
            magnitudeDb = measurement.magnitudeDb.copyOf(), warnings = measurement.warnings.toList())
        validate(source)
        validateMetadata(metadata)
        val engineMetadata = JSONObject(metadata.toString())
        val raw = linkedMapOf("excitation.wav" to excitation.copyOf(), "capture.wav" to captured.copyOf(),
            "impulse.wav" to impulse.copyOf())
        additionalCaptures.forEachIndexed { index, samples -> raw["capture-repeat-${index + 1}.wav"] = samples.copyOf() }
        raw.values.forEach { samples ->
            require(samples.isNotEmpty() && samples.all { it.isFinite() }) { "Los brutos deben contener muestras finitas" }
            require(samples.size.toLong() * 4 <= 0xffffffffL - 48) { "El bruto excede el tamaño RIFF" }
        }
        // Read before writing anything: a corrupt matrix must never silently become an empty matrix.
        val current = readSlots().toMutableList()
        val destination = File(records, source.id)
        check(!destination.exists()) { "Este identificador ya tiene brutos guardados; use un UUID nuevo" }
        val bytes = raw.values.sumOf { it.size.toLong() * 4 + 56 } + source.frequenciesHz.size * 80L +
            engineMetadata.toString().toByteArray(Charsets.UTF_8).size * 2L + 65536
        requireSpace(root, bytes)
        val staging = File(records, ".pending-${UUID.randomUUID()}")
        check(staging.mkdir()) { "No se puede crear el registro de medida" }
        var committed = false
        try {
            val rawDescription = JSONArray()
            raw.forEach { (name, samples) ->
                val file = File(staging, name)
                FloatWavWriter(file, source.sampleRate).use { it.write(samples, samples.size) }
                rawDescription.put(JSONObject().put("file", name).put("frames", samples.size)
                    .put("sampleRate", source.sampleRate).put("channels", 1)
                    .put("format", "IEEE_FLOAT_32_LE").put("sha256", sha256(file)))
            }
            File(staging, "spectrum.csv").bufferedWriter(Charsets.UTF_8).use { out ->
                out.write("frequency_hz,magnitude_db\n")
                source.frequenciesHz.indices.forEach { out.write("${source.frequenciesHz[it]},${source.magnitudeDb[it]}\n") }
            }
            val record = JSONObject().put("schemaVersion", SCHEMA).put("type", "source-measurement")
                .put("source", sourceJson(source)).put("engine", engineMetadata).put("rawFiles", rawDescription)
                .put("spectrum", JSONObject().put("file", "spectrum.csv").put("frequencyUnits", "Hz")
                    .put("magnitudeUnits", "dB").put("reference", "See engine metadata; no microphone calibration is implied"))
                .put("timing", JSONObject().put("units", "ms").put("kind", "observed-uncompensated")
                    .put("compensationMs", JSONObject.NULL).put("equivalentMetersArePhysicalDistance", false))
            File(staging, "metadata.json").writeText(record.toString(2), Charsets.UTF_8)
            check(staging.renameTo(destination)) { "No se pueden finalizar los brutos de la medida" }
            committed = true
            val saved = SourceSlot(slot, source)
            current[slot - 1] = saved
            writeSlots(current)
            saved
        } finally {
            // Only an unpublished, incomplete staging directory may be removed. Finished UUID records remain.
            if (!committed) staging.deleteRecursively()
        }
    }

    /** Clears the slot reference; recordings remain in records/ and in exports. */
    fun clear(slot: Int): Unit = synchronized(lock) {
        requireSlot(slot)
        val current = readSlots().toMutableList()
        current[slot - 1] = SourceSlot(slot)
        writeSlots(current)
    }

    /** Includes every finished historical UUID record, including records no longer assigned to a slot. */
    fun export(): File = synchronized(lock) {
        val snapshot = matrixJson(readSlots()).toString(2).toByteArray(Charsets.UTF_8)
        val files = historicalFiles()
        ensureDirectory(exports)
        requireSpace(exports, files.sumOf { it.second.length() } + snapshot.size + 65536)
        val id = UUID.randomUUID().toString()
        val temporary = File(exports, ".sonda-fuentes-$id.tmp")
        val destination = File(exports, "sonda-fuentes-$id.zip")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("matrix.json")); zip.write(snapshot); zip.closeEntry()
                files.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("schemaVersion", SCHEMA).put("type", "source-matrix-export")
                    .put("exportedAt", Instant.now().toString()).put("slots", 10)
                    .put("containsEnvironmentalAudio", true).put("includesUnassignedHistoricalRecords", true)
                    .put("timing", "observed-uncompensated; metres are equivalent acoustic lengths")
                    .toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            check(temporary.renameTo(destination)) { "No se puede finalizar la exportación" }
            destination
        } finally { temporary.delete() }
    }

    private fun readSlots(): List<SourceSlot> {
        if (!matrix.baseFile.exists() && !File(root, "matrix.json.bak").exists()) return (1..10).map(::SourceSlot)
        return try {
            val json = JSONObject(String(matrix.readFully(), Charsets.UTF_8))
            check(integer(json, "schemaVersion") == SCHEMA) { "Versión de matriz desconocida" }
            val entries = json.getJSONArray("slots")
            check(entries.length() == 10) { "La matriz debe contener diez fuentes" }
            (0 until 10).map { index ->
                val entry = entries.getJSONObject(index)
                check(integer(entry, "number") == index + 1) { "Número de fuente incoherente" }
                check(entry.has("measurement")) { "Falta el contenido de una fuente" }
                val measurement = if (entry.isNull("measurement")) null else parseSource(entry.getJSONObject("measurement"))
                if (measurement != null) {
                    val record = File(records, measurement.id)
                    check(record.canonicalFile.parentFile == records.canonicalFile &&
                        listOf("metadata.json", "excitation.wav", "capture.wav", "impulse.wav", "spectrum.csv").all { name ->
                            val file = File(record, name)
                            file.isFile && file.canonicalFile.parentFile == record.canonicalFile
                        }) { "Faltan los brutos de una fuente o contienen rutas no válidas" }
                }
                SourceSlot(index + 1, measurement)
            }
        } catch (failure: Exception) {
            throw IOException("La matriz de fuentes está dañada o incompleta. No se ha sustituido ni borrado; conserve los archivos para recuperarla.", failure)
        }
    }

    private fun writeSlots(slots: List<SourceSlot>) {
        val data = matrixJson(slots).toString(2).toByteArray(Charsets.UTF_8)
        val output = matrix.startWrite()
        try { output.write(data); matrix.finishWrite(output) }
        catch (failure: Throwable) { matrix.failWrite(output); throw failure }
    }

    private fun matrixJson(slots: List<SourceSlot>) = JSONObject().put("schemaVersion", SCHEMA)
        .put("slots", JSONArray().apply { slots.forEach { slot ->
            put(JSONObject().put("number", slot.number).put("measurement", slot.measurement?.let(::sourceJson) ?: JSONObject.NULL))
        } })

    private fun historicalFiles(): List<Pair<String, File>> = buildList {
        records.listFiles().orEmpty().sortedBy { it.name }.forEach { directory ->
            if (directory.name.startsWith(".pending-")) return@forEach
            check(directory.isDirectory && validId(directory.name) && directory.canonicalFile.parentFile == records.canonicalFile) {
                "Registro histórico no válido; no se exportarán rutas ajenas a la matriz"
            }
            val metadata = File(directory, "metadata.json")
            check(metadata.isFile) { "Registro histórico incompleto: ${directory.name}" }
            directory.listFiles().orEmpty().sortedBy { it.name }.forEach { file ->
                check(file.isFile && recordFilename.matches(file.name) && file.canonicalFile.parentFile == directory.canonicalFile) {
                    "Archivo histórico no válido; no se exportarán rutas ajenas a la medida"
                }
                add("records/${directory.name}/${file.name}" to file)
            }
        }
    }

    private fun sourceJson(source: SourceMeasurement) = JSONObject().apply {
        put("id", source.id); put("createdAt", source.createdAt); put("signal", source.signal)
        put("sampleRate", source.sampleRate); put("observedDelayMs", source.observedDelayMs)
        put("comparisonKey", source.comparisonKey); put("inputName", source.inputName); put("outputName", source.outputName)
        put("bluetooth", source.bluetooth); put("synthetic", source.synthetic)
        put("frequenciesHz", JSONArray().apply { source.frequenciesHz.forEach { put(it) } })
        put("magnitudeDb", JSONArray().apply { source.magnitudeDb.forEach { put(it) } })
        put("warnings", JSONArray(source.warnings))
    }

    private fun parseSource(json: JSONObject): SourceMeasurement {
        fun numbers(name: String): DoubleArray {
            val array = json.getJSONArray(name)
            return DoubleArray(array.length()) { index -> (array.get(index) as? Number)?.toDouble()
                ?: error("Valor no numérico en $name") }
        }
        val warnings = json.getJSONArray("warnings")
        return SourceMeasurement(string(json, "id"), string(json, "createdAt"), string(json, "signal"),
            integer(json, "sampleRate"), (json.get("observedDelayMs") as Number).toDouble(),
            string(json, "comparisonKey"), string(json, "inputName"), string(json, "outputName"),
            json.get("bluetooth") as Boolean, numbers("frequenciesHz"), numbers("magnitudeDb"),
            (0 until warnings.length()).map { warnings.get(it) as String }, json.get("synthetic") as Boolean).also(::validate)
    }

    private fun validate(source: SourceMeasurement) {
        require(validId(source.id)) { "El identificador de medida debe ser un UUID canónico" }
        require(runCatching { Instant.parse(source.createdAt) }.isSuccess) { "Fecha de medida no válida" }
        require(source.signal.isNotBlank() && source.signal.length <= 128) { "Señal no válida" }
        require(source.sampleRate > 0) { "Frecuencia de muestreo no válida" }
        require(source.observedDelayMs.isFinite() && source.observedDelayMs >= 0.0) { "Retardo observado no válido" }
        require(source.comparisonKey.isNotBlank() && source.comparisonKey.length <= 4096) { "Configuración de comparación no válida" }
        require(source.inputName.isNotBlank() && source.outputName.isNotBlank() && source.inputName.length <= 1024 && source.outputName.length <= 1024) { "Ruta de audio no válida" }
        require(source.frequenciesHz.isNotEmpty() && source.frequenciesHz.size == source.magnitudeDb.size) { "El espectro debe tener frecuencias y magnitudes del mismo tamaño" }
        source.frequenciesHz.forEachIndexed { index, frequency ->
            require(frequency.isFinite() && frequency >= 0.0 && frequency <= source.sampleRate / 2.0 &&
                (index == 0 || frequency > source.frequenciesHz[index - 1])) { "Frecuencias del espectro incoherentes" }
        }
        require(source.magnitudeDb.all { it.isFinite() }) { "Magnitudes del espectro no válidas" }
        require(source.warnings.size <= 256 && source.warnings.all { it.length <= 16384 }) { "Advertencias no válidas" }
    }

    private fun validateMetadata(metadata: JSONObject) {
        fun visit(value: Any?, depth: Int) {
            require(depth <= 32) { "Metadatos demasiado anidados" }
            when (value) {
                null, JSONObject.NULL -> Unit
                is JSONObject -> value.keys().forEach { key -> visit(value.get(key), depth + 1) }
                is JSONArray -> (0 until value.length()).forEach { visit(value.get(it), depth + 1) }
                is Number -> require(value.toDouble().isFinite()) { "Metadatos con valores no finitos" }
                is String, is Boolean -> Unit
                else -> error("Tipo de metadato no admitido")
            }
        }
        visit(metadata, 0)
        require(metadata.toString().toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) { "Metadatos demasiado grandes" }
    }

    companion object {
        private const val SCHEMA = 1
        private const val RESERVED_BYTES = 50L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Any>()
        private val recordFilename = Regex("(?:metadata\\.json|spectrum\\.csv|excitation\\.wav|capture\\.wav|impulse\\.wav|capture-repeat-[1-9][0-9]*\\.wav)")
        private fun requireSlot(slot: Int) { require(slot in 1..10) { "La fuente debe estar entre 1 y 10" } }
        private fun validId(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
        private fun ensureDirectory(directory: File) { check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) { "No se puede crear ${directory.name}" } }
        private fun requireSpace(directory: File, bytes: Long) {
            check(bytes >= 0 && directory.usableSpace > bytes + RESERVED_BYTES) { "No hay espacio suficiente; se necesitan los brutos y 50 MB de reserva. No se ha borrado ningún registro." }
        }
        private fun integer(json: JSONObject, key: String): Int {
            val value = json.get(key) as? Number ?: error("Valor no entero en $key")
            val number = value.toDouble()
            check(number.isFinite() && number == value.toInt().toDouble()) { "Valor no entero en $key" }
            return value.toInt()
        }
        private fun string(json: JSONObject, key: String) = json.get(key) as? String ?: error("Texto no válido en $key")
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
