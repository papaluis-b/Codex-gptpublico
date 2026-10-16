package es.sonda.generador

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import es.sonda.generador.audio.GeneratorOutputEngine

class GeneratorViewModel(application: Application) : AndroidViewModel(application) {
    val engine = GeneratorOutputEngine(application)
    override fun onCleared() { engine.close() }
}
