package es.sonda.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sonda.PlotFrame
import es.sonda.SondaViewModel
import es.sonda.BuildConfig
import es.sonda.audio.AudioState
import es.sonda.data.SavedCapture
import es.sonda.dsp.Spectrum
import es.sonda.dsp.OctaveBands
import es.sonda.dsp.SignalKind
import es.sonda.dsp.GeneratorSettings
import java.util.Locale
import kotlin.math.*

private val Lime = Color(0xFFB4F16E)
private val Amber = Color(0xFFFFCE76)
private val Muted = Color(0xFFA4B0A7)
private val Surface = Color(0xFF1A211C)
private val Background = Color(0xFF101411)
private fun fmt(value: Double, decimals: Int = 1) = String.format(Locale.getDefault(), "%.${decimals}f", value)

@Composable
fun SondaApp(model: SondaViewModel, onStart: (Int?, Boolean) -> Unit, onShare: (SavedCapture) -> Unit) {
    val state by model.engine.state.collectAsState()
    val devices by model.engine.devices.collectAsState()
    val plot by model.plot.collectAsState()
    val captures by model.captures.collectAsState()
    val exportError by model.exportError.collectAsState()
    var page by rememberSaveableInt()
    var diagnostics by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf<SavedCapture?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Background,
        background = Background, surface = Surface, onSurface = Color(0xFFF0F5ED),
        secondary = Amber, error = Color(0xFFFF9C97))) {
        Scaffold(containerColor = Background, bottomBar = {
            Column(Modifier.navigationBarsPadding().background(Surface)) {
                if (state.running || state.preparing) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.engine.emit(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B3330), contentColor = Color.White)) {
                        Text("SILENCIO", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { model.engine.stop() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Detener todo") }
                }
                NavigationBar(containerColor = Surface) {
                    listOf("Analizar", "Generar", "Capturas").forEachIndexed { i, title ->
                        NavigationBarItem(selected = page == i, onClick = { page = i }, icon = {
                            Text(listOf("∿", "◉", "▤")[i], fontSize = 23.sp)
                        }, label = { Text(title) })
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SONDA", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("AUDIO / DIAGNÓSTICO", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    }
                    TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Cerrar detalle" else "Diagnóstico") }
                }
                Status(state)
                if (diagnostics) Diagnostics(state, devices.map { "${it.name} (#${it.id}, ${if (it.input) "entrada" else "salida"}, tipo ${it.kind})\n${it.capabilities}" })
                if (!state.running && !state.preparing) {
                    var selected by remember { mutableStateOf<Int?>(null) }
                    var chosen by remember { mutableStateOf(false) }
                    var menu by remember { mutableStateOf(false) }
                    val outputs = devices.filter { !it.input && it.kind in setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
                    LaunchedEffect(outputs) {
                        if (!chosen) selected = outputs.firstOrNull { it.kind != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id
                        if (selected != null && outputs.none { it.id == selected }) selected = null
                    }
                    val selectedDevice = outputs.firstOrNull { it.id == selected }
                    Panel("INICIAR SESIÓN") {
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(when {
                                    selectedDevice == null -> "Solo micrófono · sin emisión"
                                    selectedDevice.kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz interno · modo de prueba"
                                    else -> "USB · ${selectedDevice.name}"
                                })
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Solo micrófono · sin emisión") }, onClick = { selected = null; chosen = true; menu = false })
                                outputs.forEach { device ->
                                    DropdownMenuItem(text = { Text(if (device.kind == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                                        "Altavoz interno · prueba explícita" else "USB · ${device.name}") }, onClick = { selected = device.id; chosen = true; menu = false })
                                }
                            }
                        }
                        Button(onClick = { onStart(selected, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Iniciar audio real") }
                        TextButton(onClick = { onStart(null, true) }, modifier = Modifier.fillMaxWidth()) { Text("Explorar simulación sin micrófono") }
                        if (outputs.none { it.kind != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }) Text("Salida USB no conectada. Puedes analizar el micrófono.", color = Muted, fontSize = 12.sp)
                    }
                }
                when (page) {
                    0 -> Analyzer(model, state, plot)
                    1 -> Generator(model, state)
                    2 -> {
                        Text("Capturas locales", style = MaterialTheme.typography.headlineSmall)
                        Text("WAV float32 original + diagnóstico JSON + último espectro CSV. Cada grabación dura hasta 120 s.", color = Muted, fontSize = 13.sp)
                        if (captures.isEmpty()) Panel("SIN CAPTURAS") { Text("Inicia audio y pulsa Grabar WAV para guardar tu primera captura.") }
                        captures.forEach { capture ->
                            Panel(if (capture.synthetic) "SIMULACIÓN · DATOS SINTÉTICOS" else "CAPTURA DE MICRÓFONO") {
                                Text(capture.title, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text("${capture.frames} muestras · ${capture.quality}", color = if (capture.synthetic) Amber else Muted)
                                OutlinedButton(onClick = { share = capture }) { Text("Exportar ZIP") }
                            }
                        }
                        exportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                Text("SONDA ${BuildConfig.VERSION_NAME} · Respuesta relativa, micrófono sin calibrar", color = Muted, fontSize = 11.sp)
            }
        }
        share?.let { capture ->
            AlertDialog(onDismissRequest = { share = null }, title = { Text("Exportar captura") },
                text = { Text("El ZIP incluye el audio grabado del entorno y los metadatos. Elige dónde compartirlo.") },
                confirmButton = { TextButton(onClick = { share = null; onShare(capture) }) { Text("Exportar") } },
                dismissButton = { TextButton(onClick = { share = null }) { Text("Cancelar") } })
        }
    }
}

@Composable private fun rememberSaveableInt() = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

@Composable private fun Status(state: AudioState) {
    Surface(color = if (state.simulation) Color(0xFF372D1D) else Surface, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(when {
                state.simulation -> "SIMULACIÓN · DATOS SINTÉTICOS"
                state.preparing -> "ABRIENDO STREAMS EN SILENCIO"
                state.running -> "MICRÓFONO INTERNO · RUTA CONFIRMADA"
                else -> "AUDIO DETENIDO"
            }, color = if (state.simulation) Amber else Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (state.running) {
                Text("Salida: ${if (state.simulation) "ninguna" else state.activeOutput}", fontSize = 12.sp, maxLines = 2)
                Text("${if (state.recording) "● GRABANDO" else "Captura activa"}  /  ${if (state.emitting) "${state.signalKind.label.uppercase()} ACTIVO" else "Emisión en silencio"}", fontSize = 12.sp)
            }
            Text(state.message, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable private fun Analyzer(model: SondaViewModel, state: AudioState, plot: PlotFrame?) {
    var frozen by remember { mutableStateOf(model.frozen) }
    var hold by remember { mutableStateOf(model.hold) }
    var cursor by remember { mutableDoubleStateOf(1000.0) }
    Text("Espectro en directo", style = MaterialTheme.typography.headlineSmall)
    Text("Densidad de potencia · dBFS/Hz", color = Muted, fontSize = 12.sp)
    val spectrum = plot?.analysis?.spectrum
    SpectrumChart(plot, cursor) { cursor = it }
    Text("Escala vertical: −130 a −10 dBFS/Hz", color = Muted, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("20 Hz", fontSize = 11.sp, color = Muted); Text("100", fontSize = 11.sp, color = Muted)
        Text("1 k", fontSize = 11.sp, color = Muted); Text("20 kHz", fontSize = 11.sp, color = Muted)
    }
    if (spectrum != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("RMS", color = Muted, fontSize = 11.sp); Text("${fmt(Spectrum.db(spectrum.rms))} dBFS", fontFamily = FontFamily.Monospace) }
            Column { Text("PICO DIGITAL", color = Muted, fontSize = 11.sp); Text("${fmt(Spectrum.db(spectrum.peak))} dBFS", fontFamily = FontFamily.Monospace) }
        }
        val bin = (cursor / spectrum.binHz).roundToInt().coerceIn(1, spectrum.psd.lastIndex)
        Text("Cursor: bin ${fmt(bin * spectrum.binHz)} Hz · ${fmt(Spectrum.db(plot!!.averagedPower[bin], true))} dBFS/Hz", color = Lime, fontSize = 12.sp)
        Text("Hann · FFT ${spectrum.size} · Δf ${fmt(spectrum.binHz, 2)} Hz · ventana ${fmt(spectrum.durationMs)} ms · media de potencia 1 s", color = Muted, fontSize = 11.sp)
    } else Text("Inicia audio para ver el espectro. No se muestran datos inventados.", color = Muted, fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = frozen, onClick = { frozen = !frozen; model.frozen = frozen }, label = { Text(if (frozen) "Congelado" else "Congelar") })
        FilterChip(selected = hold, onClick = { hold = !hold; model.hold = hold; model.clearPeaks() }, label = { Text("Máximos") })
        if (hold) TextButton(onClick = { model.clearPeaks() }) { Text("Reiniciar") }
    }
    OutlinedButton(onClick = { model.engine.setTone(cursor, state.levelDbFs) }) { Text("Usar ${fmt(cursor, 0)} Hz en el generador") }
    Panel("ESPECTROGRAMA · DATOS SIN PROMEDIAR") {
        Spectrogram(plot)
        Text("Antiguo arriba / reciente abajo · color fijo −110 a −30 dBFS/Hz · ${fmt((spectrum?.durationMs ?: 170.67) * 90 / 1000)} s · huecos sin rellenar", color = Muted, fontSize = 11.sp)
    }
    if (state.droppedFrames > 0 || state.clippedSamples > 0 || state.discontinuities > 0 || state.generatorClippedSamples > 0) {
        Text("REVISAR · clipping entrada ${state.clippedSamples} / generador ${state.generatorClippedSamples} muestras · cola perdida ${state.droppedFrames} · discontinuidades ${state.discontinuities}", color = Amber, fontSize = 12.sp)
    }
    Button(onClick = { model.engine.toggleRecording() }, enabled = state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (state.recording) "Finalizar y guardar WAV" else "Grabar WAV")
    }
}

@Composable private fun Generator(model: SondaViewModel, state: AudioState) {
    var frequency by remember(state.frequencyHz) { mutableStateOf(fmt(state.frequencyHz, 2)) }
    var error by remember { mutableStateOf<String?>(null) }
    var slider by remember(state.levelDbFs) { mutableFloatStateOf(state.levelDbFs.toFloat()) }
    val pink = state.signalKind == SignalKind.PINK_NOISE
    val maximum = GeneratorSettings.maximumFrequency(state.outputRate.takeIf { it > 0 } ?: 48000, state.signalKind, state.octaveDivision)
    Text("Generador de señal", style = MaterialTheme.typography.headlineSmall)
    Text("La selección de frecuencia no activa la emisión.", color = Muted, fontSize = 13.sp)
    Panel("SEÑAL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalKind.entries.forEach { kind ->
                FilterChip(selected = state.signalKind == kind, onClick = { model.engine.setSignalKind(kind) },
                    modifier = Modifier.weight(1f), label = { Text(kind.label) })
            }
        }
        Text("Cambiar el tipo de señal silencia la salida. Pulsa Emitir para activarla.", color = Muted, fontSize = 12.sp)
    }
    Panel(if (pink) "PASO Y ANCHURA DE CAMPANA" else "PASO DE FRECUENCIA") {
        OctaveBands.divisions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { division ->
                    FilterChip(selected = state.octaveDivision == division, onClick = { model.engine.setOctaveDivision(division) },
                        modifier = Modifier.weight(1f), label = { Text("1/$division oct") })
                }
            }
        }
        Text("− / + avanzan 1/${state.octaveDivision} de octava desde la frecuencia actual.", color = Muted, fontSize = 12.sp)
    }
    Panel("FRECUENCIA") {
        OutlinedTextField(value = frequency, onValueChange = { frequency = it }, label = { Text("Frecuencia · Hz") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.engine.stepFrequency(-1) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Bajar un paso de frecuencia" }) { Text("−", fontSize = 22.sp) }
            Button(onClick = {
                val value = frequency.replace(',', '.').toDoubleOrNull()
                if (value == null || !value.isFinite() || value !in 20.0..maximum) error = "Introduce entre 20 y ${fmt(maximum, 0)} Hz"
                else { error = null; model.engine.setTone(value, state.levelDbFs) }
            }, modifier = Modifier.weight(2f)) { Text("Aplicar Hz") }
            OutlinedButton(onClick = { model.engine.stepFrequency(1) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Subir un paso de frecuencia" }) { Text("+", fontSize = 22.sp) }
        }
        Text("${fmt(state.frequencyHz, 2)} Hz", fontSize = 32.sp, color = Lime, fontFamily = FontFamily.Monospace)
    }
    if (pink) Panel("CAMPANA DE RUIDO ROSA · 1/${state.octaveDivision} OCTAVA") {
        Text("Centro: ${fmt(state.frequencyHz, 2)} Hz", color = Lime)
        Text("Límites −3 dB: ${fmt(state.noiseLowerHz ?: state.frequencyHz, 2)} – ${fmt(state.noiseUpperHz ?: state.frequencyHz, 2)} Hz",
            fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text("Filtro con caída gradual fuera de la banda. Estabilización aproximada: ${fmt(state.noiseSettlingSeconds ?: 0.0, 2)} s.", color = Muted, fontSize = 12.sp)
        if ((state.noiseUpperHz ?: 0.0) - (state.noiseLowerHz ?: 0.0) < (state.inputRate.takeIf { it > 0 } ?: 48000) / 8192.0) {
            Text("Esta banda es más estrecha que los bins del analizador actual.", color = Amber, fontSize = 12.sp)
        }
    }
    Panel("NIVEL DIGITAL") {
        Text("${fmt(slider.toDouble(), 0)} dBFS ${if (pink) "RMS" else "pico"}", style = MaterialTheme.typography.headlineSmall)
        Slider(value = slider, onValueChange = { slider = it }, valueRange = -80f..state.generatorSettings.maximumLevel.toFloat(),
            onValueChangeFinished = { model.engine.setTone(state.frequencyHz, slider.toDouble()) },
            modifier = Modifier.semantics { contentDescription = "Nivel ${if (pink) "del ruido rosa en dBFS RMS" else "del seno en dBFS pico"}" })
        Text(if (pink) "Nivel RMS objetivo tras estabilización; el ruido fluctúa. Límite −24 dBFS RMS."
            else "Nivel de pico del seno · límite −12 dBFS.", color = Muted, fontSize = 12.sp)
        Text("Inicio −40 dBFS · rampas 20 ms. El volumen Android y la ganancia de la PA son independientes.", color = Muted, fontSize = 12.sp)
        Text("Volumen multimedia: ${state.mediaVolume}/${state.mediaVolumeMax} · estéreo L+R idénticos", color = Muted, fontSize = 12.sp)
    }
    Button(onClick = { model.engine.emit(true) }, enabled = state.running && !state.simulation && state.outputId != null && !state.emitting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (pink) "Emitir ruido rosa" else "Emitir seno") }
    if (state.outputId == null || state.simulation) Text("Para emitir, detén la sesión y selecciona un DAC USB o el modo de prueba por altavoz.", color = Muted, fontSize = 13.sp)
    Text("No hay monitorización del micrófono hacia la salida. Al salir de la app, se detiene el audio.", color = Muted, fontSize = 12.sp)
}

@Composable private fun Diagnostics(state: AudioState, devices: List<String>) {
    Panel("DIAGNÓSTICO DE RUTAS") {
        listOf(
            "Entrada solicitada" to state.requestedInput, "Entrada activa" to state.activeInput,
            "Salida solicitada" to state.requestedOutput, "Salida activa" to state.activeOutput,
            "Configuración entrada" to "${state.inputRate} Hz / ${state.inputChannels} ch / float32 / ${state.inputBufferFrames} frames buffer",
            "Configuración salida" to "${state.outputRate} Hz / ${state.outputChannels} ch / float32 / ${state.outputBufferFrames} frames buffer",
            "Fuente" to state.source, "Efectos" to state.effects,
            "Timestamp entrada" to "${state.inputTimestamp}; frame ${state.inputTimestampFrame ?: "—"}; ns ${state.inputTimestampNs ?: "—"}",
            "Timestamp salida" to "${state.outputTimestamp}; frame ${state.outputTimestampFrame ?: "—"}; ns ${state.outputTimestampNs ?: "—"}",
            "Contadores" to "${state.captureFrames} frames leídos / ${state.outputFramesWritten} frames escritos",
            "Incidencias" to "${state.droppedFrames} frames perdidos en cola / ${state.discontinuities} discontinuidades / ${state.underruns} underruns salida / ${state.clippedSamples} muestras clipping",
            "Generador" to "${state.signalKind.label}, ${state.frequencyHz} Hz, 1/${state.octaveDivision} octava, ${state.levelDbFs} dBFS ${state.generatorSettings.levelReference}; clipping: ${state.generatorClippedSamples}",
        ).forEach { (title, value) ->
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text("Los Hz corresponden a la configuración del stream, no a una comprobación del reloj físico. Android no informa de todas las pérdidas de captura. Procesado del fabricante y precisión temporal sin validar.", color = Amber, fontSize = 12.sp)
        devices.forEach { Text(it, color = Muted, fontSize = 11.sp) }
    }
}

@Composable private fun SpectrumChart(frame: PlotFrame?, cursor: Double, onCursor: (Double) -> Unit) {
    Canvas(Modifier.fillMaxWidth().height(230.dp).background(Surface, RoundedCornerShape(10.dp))
        .semantics { contentDescription = "Espectro de potencia de 20 Hz a 20 kHz. Eje vertical de menos 130 a menos 10 dBFS por hercio." }
        .pointerInput(Unit) { detectTapGestures { onCursor(20.0 * 1000.0.pow((it.x / size.width).coerceIn(0f, 1f).toDouble())) } }) {
        fun x(hz: Double) = (log10(hz / 20) / 3 * size.width).toFloat()
        fun y(db: Double) = ((-10 - db).coerceIn(0.0, 120.0) / 120 * size.height).toFloat()
        for (hz in listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0)) drawLine(Color(0xFF303B32), Offset(x(hz), 0f), Offset(x(hz), size.height))
        for (db in -130..-10 step 20) drawLine(Color(0xFF303B32), Offset(0f, y(db.toDouble())), Offset(size.width, y(db.toDouble())))
        frame?.let {
            fun curve(values: DoubleArray, color: Color) {
                val path = Path()
                var started = false
                for (k in 1 until values.size) {
                    val frequency = k * it.analysis.spectrum.binHz
                    if (frequency !in 20.0..20000.0) continue
                    val px = x(frequency); val py = y(Spectrum.db(values[k], true))
                    if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(1.6.dp.toPx()))
            }
            it.peaks?.let { p -> curve(p, Amber.copy(alpha = 0.65f)) }
            curve(it.averagedPower, Lime)
        }
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x(cursor), 0f), Offset(x(cursor), size.height), strokeWidth = 1.dp.toPx())
    }
}

@Composable private fun Spectrogram(frame: PlotFrame?) {
    Canvas(Modifier.fillMaxWidth().height(130.dp).background(Background)
        .semantics { contentDescription = "Espectrograma: frecuencia logarítmica horizontal y tiempo vertical. Color de menos 110 a menos 30 dBFS por hercio." }) {
        val rows = frame?.history ?: return@Canvas
        val binHz = frame.analysis.spectrum.binHz
        val columns = 96
        val rowHeight = size.height / 90
        val latestFrame = rows.lastOrNull()?.firstCaptureFrame ?: return@Canvas
        val frameSpan = frame.analysis.spectrum.size * 90.0
        for (row in rows) {
            val values = row.spectrum.psd
            val rowTop = size.height * (1.0 - (latestFrame - row.firstCaptureFrame + row.spectrum.size) / frameSpan).toFloat()
            if (rowTop < 0) continue
            for (column in 0 until columns) {
                val frequency = 20.0 * 1000.0.pow((column + 0.5) / columns)
                val bin = (frequency / binHz).roundToInt()
                // No invented low-frequency resolution where a cell has no FFT bin.
                val low = 20.0 * 1000.0.pow(column.toDouble() / columns)
                val high = 20.0 * 1000.0.pow((column + 1.0) / columns)
                val first = ceil(low / binHz).toInt().coerceAtLeast(1)
                val last = floor(high / binHz).toInt().coerceAtMost(values.lastIndex)
                if (first > last || bin !in values.indices) continue
                var power = 0.0
                for (k in first..last) power += values[k]
                power /= (last - first + 1)
                val intensity = ((Spectrum.db(power, true) + 110) / 80).coerceIn(0.0, 1.0).toFloat()
                val color = Color(0.06f + 0.64f * intensity, 0.12f + 0.82f * intensity, 0.15f + 0.24f * intensity)
                drawRect(color, Offset(column * size.width / columns, rowTop),
                    androidx.compose.ui.geometry.Size(size.width / columns + 0.5f, rowHeight + 0.5f))
            }
        }
    }
}
