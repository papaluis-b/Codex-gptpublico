package es.sonda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.sonda.audio.AnalysisFrame
import es.sonda.audio.AudioEngine
import es.sonda.data.CaptureStore
import es.sonda.data.SavedCapture
import es.sonda.dsp.PowerAverage
import es.sonda.measurement.MeasurementConfig
import es.sonda.measurement.MeasurementController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class SondaViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CaptureStore(application)
    val engine = AudioEngine(application, store)
    val measurement = MeasurementController(application)
    private val audioTransition = AtomicLong()
    private val _selectedInput = MutableStateFlow<Int?>(null)
    val selectedInput = _selectedInput.asStateFlow()
    private val _captures = MutableStateFlow<List<SavedCapture>>(emptyList())
    val captures = _captures.asStateFlow()
    private val _plot = MutableStateFlow<PlotFrame?>(null)
    val plot = _plot.asStateFlow()
    private val _exportError = MutableStateFlow<String?>(null)
    val exportError = _exportError.asStateFlow()
    @Volatile var frozen = false
    @Volatile var hold = false
    @Volatile private var resetHold = false

    init {
        viewModelScope.launch {
            engine.savedRevision.collect { refreshCaptures() }
        }
        viewModelScope.launch(Dispatchers.Default) {
            var average = PowerAverage(1.0)
            var peaks = DoubleArray(0)
            var history = emptyList<AnalysisFrame>()
            var previousFirst = -1L
            engine.analysis.collect { frame ->
                if (frame == null) {
                    _plot.value = null; average = PowerAverage(1.0); peaks = DoubleArray(0)
                    history = emptyList(); previousFirst = -1
                    return@collect
                }
                if (previousFirst >= frame.firstCaptureFrame) { average = PowerAverage(1.0); history = emptyList(); peaks = DoubleArray(0) }
                val dt = if (previousFirst < 0) frame.spectrum.size.toDouble() / frame.spectrum.sampleRate
                    else (frame.firstCaptureFrame - previousFirst).toDouble() / frame.spectrum.sampleRate
                previousFirst = frame.firstCaptureFrame
                val smooth = average.update(frame.spectrum.psd, dt)
                if (peaks.size != smooth.size || resetHold) { peaks = smooth.copyOf(); resetHold = false }
                if (hold) for (k in smooth.indices) peaks[k] = maxOf(peaks[k], smooth[k])
                if (!frozen) {
                    history = (history + frame).takeLast(90)
                    _plot.value = PlotFrame(frame, smooth, if (hold) peaks.copyOf() else null, history)
                }
            }
        }
    }
    fun clearPeaks() { resetHold = true }
    fun selectInput(id: Int?) { _selectedInput.value = id }
    fun startAnalysis(output: Int?, simulation: Boolean, input: Int? = selectedInput.value) {
        val ticket = audioTransition.incrementAndGet()
        measurement.stop("Iniciando analizador")
        measurement.audio.afterStopped {
            viewModelScope.launch { if (ticket == audioTransition.get()) engine.start(output, simulation, input) }
        }
    }
    fun startMeasurement(config: MeasurementConfig, input: Int?, output: Int) {
        val ticket = audioTransition.incrementAndGet()
        measurement.request(config, input, output) { ready ->
            viewModelScope.launch { if (ticket == audioTransition.get()) engine.stop("Sesión de medida", ready) }
        }
    }
    fun demoMeasurement(config: MeasurementConfig) {
        val ticket = audioTransition.incrementAndGet()
        measurement.demo(config) { ready ->
            viewModelScope.launch { if (ticket == audioTransition.get()) engine.stop("Simulación de medida", ready) }
        }
    }
    fun stopAll(reason: String = "Audio detenido") {
        audioTransition.incrementAndGet()
        measurement.stop(reason)
        engine.stop(reason)
    }
    fun refreshCaptures() { viewModelScope.launch(Dispatchers.IO) { _captures.value = store.list() } }
    fun export(id: String, ready: (File) -> Unit) {
        _exportError.value = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.export(id) } }
                .onSuccess(ready).onFailure { _exportError.value = it.message ?: "No se pudo exportar" }
        }
    }
    override fun onCleared() { stopAll("Aplicación cerrada"); measurement.close(); engine.close() }
}
data class PlotFrame(val analysis: AnalysisFrame, val averagedPower: DoubleArray, val peaks: DoubleArray?, val history: List<AnalysisFrame>)
