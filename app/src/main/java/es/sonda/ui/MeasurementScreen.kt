package es.sonda.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sonda.audio.AudioDevice
import es.sonda.audio.BluetoothOutputTypes
import es.sonda.measurement.MeasurementConfig
import es.sonda.measurement.MeasurementResult
import es.sonda.measurement.MeasurementSignal
import es.sonda.measurement.audio.MeasurementAudioState
import es.sonda.measurement.data.SourceSlot
import es.sonda.measurement.data.compareSources
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

data class MeasurementUiState(
    val busy: Boolean = false,
    val message: String = "Selecciona la señal y las rutas para medir",
    val result: MeasurementResult? = null,
    val canSave: Boolean = false,
    val syntheticResult: Boolean = false,
    val slots: List<SourceSlot> = (1..10).map { SourceSlot(it) },
    val reference: Int = 1,
    val speedMetersPerSecond: Double = 343.0,
    val repeatDelaysMs: List<Double> = emptyList(),
    val spreadMs: Double? = null,
)

private val MeasureMuted = Color(0xFFA4B0A7)
private val MeasureAccent = Color(0xFFB4F16E)
private val MeasureAmber = Color(0xFFFFCE76)
private val MeasureBackground = Color(0xFF101411)
private fun measurementNumber(value: Double, decimals: Int = 2) =
    if (value.isFinite()) String.format(Locale.getDefault(), "%.${decimals}f", value) else "—"

/** Stateless measurement actions; selection never opens a stream or emits audio. */
@Composable
fun MeasurementScreen(
    state: MeasurementUiState,
    audio: MeasurementAudioState,
    devices: List<AudioDevice>,
    onMeasure: (MeasurementConfig, Int?, Int) -> Unit,
    onStop: () -> Unit,
    onDemo: (MeasurementConfig) -> Unit,
    onSave: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onExport: () -> Unit,
    onReference: (Int) -> Unit,
    onSpeed: (Double) -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf(MeasurementSignal.SWEEP.name) }
    val kind = MeasurementSignal.valueOf(kindName)
    var duration by rememberSaveable { mutableIntStateOf(5) }
    var lowText by rememberSaveable { mutableStateOf("40") }
    var highText by rememberSaveable { mutableStateOf("16000") }
    var level by rememberSaveable { mutableFloatStateOf(-30f) }
    var inputId by rememberSaveable { mutableStateOf<Int?>(null) }
    var outputId by rememberSaveable { mutableStateOf<Int?>(null) }
    var outputChosen by rememberSaveable { mutableStateOf(false) }
    var targetSlot by rememberSaveable { mutableIntStateOf(1) }
    var replacement by remember { mutableStateOf<Int?>(null) }
    var deletion by remember { mutableStateOf<Int?>(null) }
    var differences by rememberSaveable { mutableStateOf(false) }
    var curveSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var overlay by rememberSaveable { mutableStateOf(false) }
    var speedText by rememberSaveable(state.speedMetersPerSecond) {
        mutableStateOf(measurementNumber(state.speedMetersPerSecond, 1))
    }

    val usbKinds = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
    val bluetoothKinds = BluetoothOutputTypes
    val outputs = devices.filter { !it.input && (it.kind in usbKinds || it.kind in bluetoothKinds || it.kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
    val inputs = devices.filter { it.input && it.kind in usbKinds }
    LaunchedEffect(outputs, inputs) {
        if (!outputChosen) outputId = outputs.firstOrNull { it.kind in usbKinds }?.id
        if (outputId != null && outputs.none { it.id == outputId }) outputId = null
    }
    val selectedOutput = outputs.firstOrNull { it.id == outputId }
    val selectedInput = inputs.firstOrNull { it.id == inputId }
    val inputMissing = inputId != null && selectedInput == null
    val bluetooth = selectedOutput?.kind in bluetoothKinds
    val low = lowText.replace(',', '.').toDoubleOrNull()
    val high = highText.replace(',', '.').toDoubleOrNull()
    val rangeValid = low != null && high != null && low.isFinite() && high.isFinite() && low >= 20.0 && high <= 20000.0 && low < high
    val config = if (rangeValid) MeasurementConfig(
        sampleRate = 48000,
        kind = kind,
        lowHz = low!!,
        highHz = high!!,
        durationSeconds = if (kind == MeasurementSignal.PULSE) 0.08 else duration.toDouble(),
        levelDbFs = level.toDouble(),
        preSilenceSeconds = 0.3,
        tailSeconds = 2.0,
    ) else null
    val busy = state.busy || audio.preparing || audio.running
    val referenceName = "Fuente ${state.reference}"
    val comparisons = remember(state.slots, state.reference, state.speedMetersPerSecond) {
        compareSources(state.slots, state.reference, state.speedMetersPerSecond)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Medir altavoces", style = MaterialTheme.typography.headlineSmall)
        Text("Respuesta en frecuencia y retardo observado hasta el micrófono.", color = MeasureMuted, fontSize = 13.sp)
        MeasurementPanel("SEÑAL CONOCIDA") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == MeasurementSignal.SWEEP, onClick = { kindName = MeasurementSignal.SWEEP.name }, enabled = !busy,
                    label = { Text("Barrido log") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                FilterChip(selected = kind == MeasurementSignal.PULSE, onClick = { kindName = MeasurementSignal.PULSE.name }, enabled = !busy,
                    label = { Text("Pulso") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
            if (kind == MeasurementSignal.SWEEP) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10).forEach { seconds ->
                        FilterChip(selected = duration == seconds, onClick = { duration = seconds }, enabled = !busy,
                            label = { Text("$seconds s") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            } else Text("Pulso pasa-banda · 80 ms · 5 repeticiones", color = MeasureMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = lowText, onValueChange = { lowText = it }, label = { Text("Desde · Hz") }, enabled = !busy,
                    singleLine = true, isError = !rangeValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = highText, onValueChange = { highText = it }, label = { Text("Hasta · Hz") }, enabled = !busy,
                    singleLine = true, isError = !rangeValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            if (!rangeValid) Text("Introduce un rango creciente entre 20 y 20 000 Hz.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            TextButton(onClick = { lowText = "40"; highText = "16000" }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Restablecer 40–16 000 Hz") }
            Text("Nivel digital: ${measurementNumber(level.toDouble(), 0)} dBFS", fontFamily = FontFamily.Monospace)
            Slider(value = level, onValueChange = { level = it }, valueRange = -60f..-12f, steps = 47, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Nivel de la señal de medida en dBFS" })
            Text("El volumen multimedia también modifica la salida. Empieza bajo y comprueba el sistema.", color = MeasureMuted, fontSize = 12.sp)
        }
        MeasurementPanel("ENTRADA Y SALIDA") {
            MeasurementMenu(
                label = if (inputMissing) "Entrada USB desconectada · selecciona una entrada" else "Entrada: ${selectedInput?.let { "USB · ${it.name}" } ?: "micrófono interno"}",
                enabled = !busy,
                choices = listOf(null to "Micrófono interno") + inputs.map { it.id to "USB · ${it.name}" },
                onSelect = { inputId = it },
            )
            if (inputMissing) Text("La entrada USB seleccionada ya no está disponible. Confirma otra entrada para medir.", color = MeasureAmber, fontSize = 12.sp)
            MeasurementMenu(
                label = selectedOutput?.let { "Salida: ${measurementOutputLabel(it, bluetoothKinds)}" } ?: "Seleccionar salida para emitir",
                enabled = !busy,
                choices = listOf(null to "Sin salida seleccionada") + outputs.map { it.id to measurementOutputLabel(it, bluetoothKinds) },
                onSelect = { outputId = it; outputChosen = true },
            )
            if (outputs.none { it.kind in usbKinds }) Text("DAC USB no conectado. Selecciona explícitamente otra salida para una prueba.", color = MeasureMuted, fontSize = 12.sp)
            if (bluetooth) Text("BLUETOOTH · EXPLORACIÓN. Su latencia puede variar entre medidas. El retardo incluye Bluetooth y el resto de la cadena; no es el delay puro del altavoz.", color = MeasureAmber, fontSize = 12.sp)
            Text("${if (kind == MeasurementSignal.SWEEP) "Barrido de $duration s" else "5 pulsos de 80 ms"} · ${lowText}–${highText} Hz · ${measurementNumber(level.toDouble(), 0)} dBFS\n${if (inputMissing) "Entrada USB desconectada" else selectedInput?.name ?: "Micrófono interno"} → ${selectedOutput?.name ?: "selecciona salida"}", fontSize = 12.sp)
            Button(onClick = { config?.let { measurement -> selectedOutput?.let { outputChosen = true; onMeasure(measurement, inputId, it.id) } } },
                enabled = !busy && !inputMissing && config != null && selectedOutput != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Emitir y medir") }
            TextButton(onClick = { config?.let(onDemo) }, enabled = !busy && config != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Explorar SIMULACIÓN · sin emisión") }
        }
        MeasurementPanel(if (busy) "MEDICIÓN EN CURSO" else "ESTADO") {
            Text(state.message, fontSize = 13.sp)
            if (busy) {
                LinearProgressIndicator(progress = { audio.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(audio.message, color = MeasureMuted, fontSize = 12.sp)
                Text("Entrada: ${audio.activeInput}\nSalida: ${audio.activeOutput}", color = MeasureMuted, fontSize = 12.sp)
                if (audio.routingVerified) Text("Ruta confirmada · ${audio.inputRate} / ${audio.outputRate} Hz", color = MeasureAccent, fontSize = 12.sp)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Detener y silenciar") }
            }
        }
        state.result?.let { result ->
            MeasurementPanel(if (state.syntheticResult) "SIMULACIÓN · DATOS SINTÉTICOS" else "ÚLTIMO RESULTADO") {
                Text(if (result.valid) "Retardo observado: ${measurementNumber(result.delayMs)} ms" else "MEDIDA NO VÁLIDA · revisa los motivos",
                    color = if (result.valid) MeasureAccent else MaterialTheme.colorScheme.error, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (result.valid) Text("Equivalente temporal: ${measurementNumber(result.delayMs * state.speedMetersPerSecond / 1000.0)} m", fontSize = 13.sp)
                Text("Incluye propagación acústica y latencias de reproducción, captura y procesado. Sin compensación validada, no representa el delay puro del altavoz.", color = MeasureMuted, fontSize = 12.sp)
                Text("Pico / ruido: ${measurementNumber(result.peakToNoiseDb, 1)} dB · correlación: ${measurementNumber(result.normalizedCorrelation, 3)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (result.polarityInverted) Text("Llegada con polaridad invertida", color = MeasureAmber, fontSize = 12.sp)
                if (result.candidateDelaysMs.size > 1) Text("Candidatos: ${result.candidateDelaysMs.take(6).joinToString(" / ") { measurementNumber(it) }} ms · posibles reflexiones", color = MeasureAmber, fontSize = 12.sp)
                if (state.repeatDelaysMs.isNotEmpty() && result.valid) Text("Repeticiones: ${state.repeatDelaysMs.joinToString(" / ") { measurementNumber(it) }} ms", color = MeasureMuted, fontSize = 12.sp)
                else if (state.repeatDelaysMs.isNotEmpty()) Text("Serie descartada: revisa los controles de calidad y repetibilidad.", color = MeasureAmber, fontSize = 12.sp)
                state.spreadMs?.let { Text("Dispersión entre repeticiones: ${measurementNumber(it)} ms", color = MeasureMuted, fontSize = 12.sp) }
                result.warnings.forEach { Text("• $it", color = MeasureAmber, fontSize = 12.sp) }
                MeasurementMenu(label = "Guardar en Fuente $targetSlot", enabled = !busy,
                    choices = state.slots.map { it.number to "${it.name}${if (it.measurement == null) " · libre" else " · ocupada"}" },
                    onSelect = { if (it != null) targetSlot = it })
                val occupied = state.slots.firstOrNull { it.number == targetSlot }?.measurement != null
                OutlinedButton(onClick = { if (occupied) replacement = targetSlot else onSave(targetSlot) },
                    enabled = state.canSave && result.valid && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (occupied) "Reemplazar Fuente $targetSlot con este resultado" else "Guardar resultado en Fuente $targetSlot")
                }
            }
        }
        MeasurementPanel("MATRIZ · 10 FUENTES") {
            Text("Guarda cada altavoz sin mover el micrófono. Los metros son equivalentes al tiempo, no una distancia geométrica medida.", color = MeasureMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !differences, onClick = { differences = false }, label = { Text("Tiempos") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = differences, onClick = { differences = true }, label = { Text("Diferencias") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            MeasurementMenu(label = "Referencia: $referenceName", enabled = !busy,
                choices = state.slots.map { it.number to "${it.name}${if (it.measurement == null) " · sin registro" else ""}" },
                onSelect = { if (it != null) onReference(it) })
            if (differences) Text("Δ = fuente − $referenceName. Positivo: llega después. Se comparan solo registros con condiciones compatibles.", color = MeasureMuted, fontSize = 12.sp)
            if (state.slots.any { it.measurement?.bluetooth == true }) Text("Los registros Bluetooth incluyen una latencia variable. Sus diferencias temporales son exploratorias.", color = MeasureAmber, fontSize = 12.sp)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Text("Fuente / estado", color = MeasureMuted, fontSize = 12.sp, modifier = Modifier.width(162.dp))
                    Text(if (differences) "Δ ms" else "ms observados", color = MeasureMuted, fontSize = 12.sp, modifier = Modifier.width(116.dp))
                    Text(if (differences) "Δ m equivalentes" else "m equivalentes", color = MeasureMuted, fontSize = 12.sp, modifier = Modifier.width(125.dp))
                }
                state.slots.forEach { slot ->
                    val comparison = comparisons.firstOrNull { it.number == slot.number }
                    val time = if (differences) comparison?.differenceMs else comparison?.observedMs
                    val meters = if (differences) comparison?.differenceMeters else comparison?.equivalentMeters
                    val selected = curveSlot == slot.number
                    HorizontalDivider(color = MeasureMuted.copy(alpha = 0.18f))
                    Row(Modifier.background(if (selected) MeasureAccent.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(enabled = slot.measurement != null) { curveSlot = slot.number }
                        .heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(162.dp)) {
                            Text(slot.name + if (slot.number == state.reference) " · REF" else "", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(when { slot.measurement == null -> "Sin registro"; slot.measurement.synthetic -> "SIMULACIÓN"; slot.measurement.bluetooth -> "Bluetooth · exploración"; else -> "Registro real" },
                                color = if (slot.measurement?.synthetic == true || slot.measurement?.bluetooth == true) MeasureAmber else MeasureMuted, fontSize = 11.sp)
                        }
                        Text(time?.let { measurementNumber(it) } ?: "—", fontFamily = FontFamily.Monospace, modifier = Modifier.width(116.dp))
                        Text(meters?.let { measurementNumber(it) } ?: "—", fontFamily = FontFamily.Monospace, modifier = Modifier.width(125.dp))
                    }
                    if (differences && slot.measurement != null && comparison?.comparable != true) {
                        Text("${slot.name}: ${comparison?.reason ?: "sin referencia comparable"}", color = MeasureAmber, fontSize = 11.sp, modifier = Modifier.width(403.dp).padding(bottom = 8.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = speedText, onValueChange = { speedText = it }, label = { Text("Velocidad · m/s") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                val speed = speedText.replace(',', '.').toDoubleOrNull()
                OutlinedButton(onClick = { speed?.let(onSpeed) }, enabled = speed != null && speed.isFinite() && speed in 300.0..380.0,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Aplicar") }
            }
            Text("Conversión actual: ${measurementNumber(state.speedMetersPerSecond, 1)} m/s. Ajustable entre 300 y 380 m/s.", color = MeasureMuted, fontSize = 11.sp)
            OutlinedButton(onClick = onExport, enabled = !busy && state.slots.any { it.measurement != null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Exportar matriz y espectros") }
        }
        val selectedRecord = state.slots.firstOrNull { it.number == curveSlot }?.measurement
        val referenceRecord = state.slots.firstOrNull { it.number == state.reference }?.measurement
        val curveFrequency = selectedRecord?.frequenciesHz ?: state.result?.frequenciesHz
        val curveMagnitude = selectedRecord?.magnitudeDb ?: state.result?.magnitudeDb
        MeasurementPanel("RESPUESTA EN FRECUENCIA · MAGNITUD RELATIVA") {
            MeasurementMenu(label = if (selectedRecord != null) "Ver curva: Fuente $curveSlot" else "Ver curva: último resultado", enabled = !busy,
                choices = listOf(null to "Último resultado") + state.slots.filter { it.measurement != null }.map { it.number to "${it.name}${if (it.measurement?.synthetic == true) " · SIMULACIÓN" else ""}" },
                onSelect = { curveSlot = it })
            val comparison = comparisons.firstOrNull { it.number == curveSlot }
            val canOverlay = selectedRecord != null && referenceRecord != null && curveSlot != state.reference && comparison?.comparable == true
            if (selectedRecord != null && curveSlot != state.reference) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = canOverlay) { overlay = !overlay }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overlay && canOverlay, onCheckedChange = { overlay = it }, enabled = canOverlay)
                    Text("Superponer $referenceName", fontSize = 13.sp)
                }
                if (!canOverlay) Text(comparison?.reason ?: "Sin referencia compatible para superponer", color = MeasureAmber, fontSize = 11.sp)
            }
            val showReference = overlay && canOverlay
            if (curveFrequency != null && curveMagnitude != null && curveFrequency.isNotEmpty()) {
                val synthetic = selectedRecord?.synthetic ?: state.syntheticResult
                if (selectedRecord == null && state.result?.valid == false) Text("CURVA DE DIAGNÓSTICO · medida no válida", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text(if (synthetic) "SIMULACIÓN · DATOS SINTÉTICOS" else "Respuesta relativa · micrófono sin calibrar", color = if (synthetic) MeasureAmber else MeasureMuted, fontSize = 12.sp)
                MeasurementMagnitudeChart(curveFrequency, curveMagnitude,
                    if (showReference) referenceRecord?.frequenciesHz else null,
                    if (showReference) referenceRecord?.magnitudeDb else null)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("20 Hz", "100", "1 k", "20 kHz").forEach { Text(it, color = MeasureMuted, fontSize = 11.sp) }
                }
                Text("Verde: ${if (selectedRecord != null) "Fuente $curveSlot" else "último resultado"}${if (showReference) " · ámbar: $referenceName" else ""}. Magnitud en dB relativos; no es SPL.", color = MeasureMuted, fontSize = 11.sp)
                selectedRecord?.let { record ->
                    Text("${record.createdAt} · ${record.signal} · ${record.sampleRate} Hz", color = MeasureMuted, fontSize = 11.sp)
                    Text("${record.inputName} → ${record.outputName}", color = MeasureMuted, fontSize = 11.sp)
                    record.warnings.forEach { Text("• $it", color = MeasureAmber, fontSize = 11.sp) }
                    TextButton(onClick = { deletion = curveSlot }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Eliminar registro de Fuente $curveSlot") }
                }
            } else Text("La curva aparecerá al completar una medida o seleccionar una fuente guardada.", color = MeasureMuted, fontSize = 13.sp)
        }
    }
    replacement?.let { slot ->
        AlertDialog(onDismissRequest = { replacement = null }, title = { Text("Reemplazar Fuente $slot") },
            text = { Text("El último resultado ocupará este espacio. El registro anterior se conserva en el archivo local de medidas.") },
            confirmButton = { TextButton(onClick = { replacement = null; onSave(slot) }) { Text("Reemplazar") } },
            dismissButton = { TextButton(onClick = { replacement = null }) { Text("Cancelar") } })
    }
    deletion?.let { slot ->
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Vaciar Fuente $slot") },
            text = { Text("Se quitará este registro de la matriz. El archivo original se conserva en el almacenamiento local de medidas.") },
            confirmButton = { TextButton(onClick = { deletion = null; onClear(slot) }) { Text("Vaciar espacio") } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("Cancelar") } })
    }
}

private fun measurementOutputLabel(device: AudioDevice, bluetoothKinds: Set<Int>) = when {
    device.kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz interno · prueba explícita"
    device.kind in bluetoothKinds -> "Bluetooth · ${device.name} · exploración"
    else -> "USB · ${device.name}"
}

@Composable
private fun MeasurementPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF1A211C), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MeasureMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun MeasurementMenu(label: String, enabled: Boolean, choices: List<Pair<Int?, String>>, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun MeasurementMagnitudeChart(frequencies: DoubleArray, magnitude: DoubleArray, referenceFrequencies: DoubleArray?, referenceMagnitude: DoubleArray?) {
    val finite = remember(frequencies, magnitude, referenceFrequencies, referenceMagnitude) {
        buildList {
            magnitude.forEachIndexed { i, value -> if (i < frequencies.size && frequencies[i] in 20.0..20000.0 && value.isFinite()) add(value) }
            referenceMagnitude?.forEachIndexed { i, value -> if (referenceFrequencies != null && i < referenceFrequencies.size && referenceFrequencies[i] in 20.0..20000.0 && value.isFinite()) add(value) }
        }
    }
    val upper = ceil((finite.maxOrNull() ?: 0.0) / 10.0) * 10.0 + 10.0
    val lower = floor((finite.minOrNull() ?: -60.0) / 10.0) * 10.0 - 10.0
    Text("Escala: ${measurementNumber(lower, 0)} a ${measurementNumber(upper, 0)} dB relativos", color = MeasureMuted, fontSize = 11.sp)
    Canvas(Modifier.fillMaxWidth().height(190.dp).background(MeasureBackground)
        .semantics { contentDescription = "Respuesta en frecuencia: frecuencia logarítmica de 20 a 20 000 hercios y magnitud relativa en decibelios. La curva ámbar representa la referencia cuando está activada." }) {
        fun x(hz: Double) = ((log10(hz / 20.0) / 3.0) * size.width).toFloat()
        fun y(db: Double) = (size.height * (1.0 - (db - lower) / max(1.0, upper - lower))).toFloat().coerceIn(0f, size.height)
        listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0).forEach { hz -> drawLine(Color(0xFF303B32), Offset(x(hz), 0f), Offset(x(hz), size.height)) }
        repeat(5) { line ->
            val at = size.height * line / 4f
            drawLine(Color(0xFF303B32), Offset(0f, at), Offset(size.width, at))
        }
        fun curve(hz: DoubleArray, db: DoubleArray, color: Color) {
            val path = Path()
            var started = false
            for (i in 0 until minOf(hz.size, db.size)) {
                if (hz[i] !in 20.0..20000.0 || !db[i].isFinite()) { started = false; continue }
                if (!started) { path.moveTo(x(hz[i]), y(db[i])); started = true } else path.lineTo(x(hz[i]), y(db[i]))
            }
            drawPath(path, color, style = Stroke(1.6.dp.toPx()))
        }
        if (referenceFrequencies != null && referenceMagnitude != null) curve(referenceFrequencies, referenceMagnitude, MeasureAmber)
        curve(frequencies, magnitude, MeasureAccent)
    }
}
