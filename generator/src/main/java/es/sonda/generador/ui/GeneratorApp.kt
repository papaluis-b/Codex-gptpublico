package es.sonda.generador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sonda.dsp.GeneratorSettings
import es.sonda.dsp.OctaveBands
import es.sonda.dsp.SignalKind
import es.sonda.generador.BuildConfig
import es.sonda.generador.GeneratorViewModel
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val Teal = Color(0xFF80E1D4)
private val Amber = Color(0xFFFFD08A)
private val Muted = Color(0xFFA3B9B5)
private val Background = Color(0xFF101717)
private val Surface = Color(0xFF1B2726)
private fun fmt(value: Double, decimals: Int = 2) = String.format(Locale.getDefault(), "%.${decimals}f", value)

@Composable
fun GeneratorApp(model: GeneratorViewModel) {
    val state by model.engine.state.collectAsState()
    val devices by model.engine.devices.collectAsState()
    val settings = state.settings
    val pink = settings.kind == SignalKind.PINK_NOISE
    val opened = state.running || state.preparing
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var frequency by remember(settings.frequencyHz) { mutableStateOf(fmt(settings.frequencyHz)) }
    var error by remember(settings.frequencyHz) { mutableStateOf<String?>(null) }
    var slider by remember(settings.kind, settings.levelDbFs) { mutableFloatStateOf(settings.levelDbFs.toFloat()) }
    val maximum = GeneratorSettings.maximumFrequency(state.sampleRate.takeIf { it > 0 } ?: 48000, settings.kind, settings.octaveDivision)
    val frequencyPosition = (ln(settings.frequencyHz / 20.0) / ln(maximum / 20.0)).coerceIn(0.0, 1.0).toFloat()
    LaunchedEffect(devices) {
        if (devices.none { it.id == selected }) selected = null
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, onPrimary = Background, secondary = Amber,
        background = Background, surface = Surface, onSurface = Color(0xFFF0F7F5), error = Color(0xFFFFA6A1))) {
        Scaffold(containerColor = Background, bottomBar = {
            Column(Modifier.background(Surface).navigationBarsPadding().imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.emitting) "${settings.kind.label} activo · ${fmt(settings.frequencyHz)} Hz"
                    else if (state.routingVerified) "Salida preparada · en silencio" else "Emisión en silencio",
                    color = if (state.emitting) Teal else Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.engine.emit(true) },
                        enabled = state.running && state.routingVerified && !state.emitting,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                        Text(if (pink) "Emitir ruido rosa" else "Emitir seno")
                    }
                    Button(onClick = { model.engine.emit(false) }, modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF943F3A), contentColor = Color.White)) {
                        Text("SILENCIO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SONDA Generador", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("SENO / RUIDO ROSA", color = Teal, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(state.message, color = if (state.emitting) Teal else Muted, fontSize = 14.sp)
                Panel("SALIDA DE AUDIO") {
                    if (opened) {
                        Text(state.requestedOutput, fontWeight = FontWeight.Bold)
                        Text(if (state.routingVerified) "Activa: ${state.activeOutput}" else "Comprobando la ruta…", color = Muted, fontSize = 12.sp)
                        OutlinedButton(onClick = { model.engine.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Detener salida") }
                    } else {
                        devices.forEach { device ->
                            FilterChip(selected = selected == device.id, onClick = { selected = device.id },
                                label = { Text(if (device.isSpeaker) "Altavoz del teléfono · prueba" else device.name) },
                                modifier = Modifier.fillMaxWidth())
                        }
                        if (devices.none { it.isUsb }) Text("Conecta un DAC USB o selecciona el altavoz para una prueba.", color = Muted, fontSize = 12.sp)
                        if (devices.isEmpty()) Text("No hay una salida compatible disponible.", color = Amber, fontSize = 12.sp)
                        Button(onClick = { selected?.let(model.engine::start) }, enabled = selected != null,
                            modifier = Modifier.fillMaxWidth()) { Text("Preparar salida") }
                        Text("La salida se prepara en silencio. Después pulsa Emitir.", color = Muted, fontSize = 12.sp)
                    }
                }
                Panel("SEÑAL") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalKind.entries.forEach { kind ->
                            FilterChip(selected = settings.kind == kind, onClick = { model.engine.setSignalKind(kind) },
                                modifier = Modifier.weight(1f), label = { Text(kind.label) })
                        }
                    }
                    Text("Cambiar el tipo silencia la salida. Pulsa Emitir para activarla de nuevo.", color = Muted, fontSize = 12.sp)
                }
                Panel(if (pink) "PASO Y ANCHURA DE CAMPANA" else "PASO DE FRECUENCIA") {
                    OctaveBands.divisions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { division ->
                                FilterChip(selected = settings.octaveDivision == division,
                                    onClick = { model.engine.setOctaveDivision(division) }, modifier = Modifier.weight(1f),
                                    label = { Text("1/$division oct") })
                            }
                        }
                    }
                    Text("− / + avanzan 1/${settings.octaveDivision} de octava desde la frecuencia actual.", color = Muted, fontSize = 12.sp)
                }
                Panel("FRECUENCIA") {
                    Text("${fmt(settings.frequencyHz)} Hz", color = Teal, fontSize = 32.sp, fontFamily = FontFamily.Monospace)
                    Text("AJUSTE CONTINUO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Slider(value = frequencyPosition, onValueChange = { position ->
                        model.engine.setContinuousFrequency(20.0 * (maximum / 20.0).pow(position.toDouble()))
                    }, valueRange = 0f..1f, steps = 0,
                        modifier = Modifier.semantics {
                            contentDescription = "Control continuo de frecuencia"
                            stateDescription = "${fmt(settings.frequencyHz)} Hz"
                        })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (mark in 0..3) {
                            val hz = 20.0 * (maximum / 20.0).pow(mark / 3.0)
                            Text(if (hz >= 999.5) "${fmt(hz / 1000, 1)} kHz" else "${fmt(hz, 0)} Hz",
                                color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                    Text("Arrastra para cambiar la frecuencia de forma continua. Los botones − / + usan el paso de octava.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(value = frequency, onValueChange = { frequency = it; error = null },
                        label = { Text("Frecuencia · Hz") }, singleLine = true, isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.engine.stepFrequency(-1) }, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "Bajar un paso de frecuencia" }) { Text("−", fontSize = 22.sp) }
                        Button(onClick = {
                            val value = frequency.replace(',', '.').toDoubleOrNull()
                            if (value == null || !value.isFinite() || value !in 20.0..maximum) error = "Introduce entre 20 y ${fmt(maximum, 0)} Hz"
                            else { error = null; model.engine.setTone(value, settings.levelDbFs) }
                        }, modifier = Modifier.weight(2f)) { Text("Aplicar Hz") }
                        OutlinedButton(onClick = { model.engine.stepFrequency(1) }, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "Subir un paso de frecuencia" }) { Text("+", fontSize = 22.sp) }
                    }
                }
                if (pink) Panel("CAMPANA DE RUIDO ROSA · 1/${settings.octaveDivision} OCTAVA") {
                    Text("Centro: ${fmt(settings.frequencyHz)} Hz", color = Teal)
                    Text("Bordes del filtro a −3 dB: ${fmt(state.noiseLowerHz ?: settings.frequencyHz)} – ${fmt(state.noiseUpperHz ?: settings.frequencyHz)} Hz",
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Caída gradual fuera de la banda. Estabilización aproximada: ${fmt(state.noiseSettlingSeconds ?: 0.0)} s.", color = Muted, fontSize = 12.sp)
                }
                Panel("NIVEL DIGITAL") {
                    Text("${fmt(slider.toDouble(), 0)} dBFS ${if (pink) "RMS" else "pico"}", style = MaterialTheme.typography.headlineSmall)
                    Slider(value = slider, onValueChange = { slider = it }, valueRange = -80f..settings.maximumLevel.toFloat(),
                        onValueChangeFinished = { model.engine.setTone(settings.frequencyHz, slider.toDouble()) },
                        modifier = Modifier.semantics { contentDescription = "Nivel ${if (pink) "del ruido rosa en dBFS RMS" else "del seno en dBFS pico"}" })
                    Text(if (pink) "RMS objetivo tras estabilización; el ruido fluctúa. Límite −24 dBFS RMS."
                        else "Nivel de pico del seno · límite −12 dBFS.", color = Muted, fontSize = 12.sp)
                    Text("El volumen Android y la ganancia de la PA se ajustan por separado.", color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Cerrar diagnóstico" else "Diagnóstico de salida") }
                if (diagnostics) Panel("DIAGNÓSTICO") {
                    Text("Solicitada: ${state.requestedOutput}\nActiva: ${state.activeOutput}", fontSize = 12.sp)
                    Text(if (state.sampleRate > 0) "Stream: ${state.sampleRate} Hz · ${state.channels} canales · float32 · buffer ${state.bufferFrames} frames"
                        else "Stream sin abrir", fontSize = 12.sp, color = Muted)
                    Text("Underruns: ${state.underruns} · clipping: ${state.clippedSamples} muestras", fontSize = 12.sp, color = Muted)
                    Text("Volumen multimedia: ${state.mediaVolume}/${state.mediaVolumeMax} · L/R idénticos", fontSize = 12.sp, color = Muted)
                }
                Text("Generador independiente · ${BuildConfig.VERSION_NAME}\nFunciona sin Internet y sin usar el micrófono.", color = Muted, fontSize = 11.sp)
                Text("Al salir de la app o desconectar la salida, el audio se detiene.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}
