# Evidencia de la entrega · SONDA 0.1.0

Fecha: 16 de octubre de 2026. Entorno Linux x86_64, Temurin JDK 21.0.12.1+1, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Android SDK 36. Base de especificación: commit `1732e8d446ea15c131d5d2e9bf3bc2ef797abee2`; implementación en el checkout de trabajo.

## Implementado

- Proyecto Android instalable y DSP JVM separado.
- Análisis de micrófono sin necesidad de DAC; FFT/PSD Hann, RMS/pico, promedio de potencia, máximos, congelación y espectrograma.
- Solicitud y comprobación de entrada integrada y salida USB independientes; altavoz interno solo con selección expresa del modo de prueba.
- Seno estéreo idéntico L/R, fase continua y rampas de ganancia. Inicio siempre en silencio.
- Diagnóstico de formato, buffers, rutas, timestamps y contadores disponibles.
- Grabación explícita de WAV original, almacenamiento privado, JSON de condiciones/estado y exportación ZIP mediante selector Android.
- Simulación determinista visible y distinguida también en los archivos.
- Parada por segundo plano, error, pérdida de foco y cambio/desconexión de ruta. No hay reanudación automática del tono.

## Verificación automática

Comando de validación:

```bash
source scripts/cloud-env.sh
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

- **PASS · 11 pruebas DSP:** seno centrado y entre bins; Parseval con ruido/DC/Nyquist; estabilidad de potencia al cambiar FFT; promedio lineal; silencio y clipping; fase/frecuencia entre bloques; rampas y silencio; overflow y orden concurrente de la cola; reapertura de muestras float32 y cabecera WAV.
- **PASS · compilación Kotlin Android:** código de aplicación y pruebas compilados en el entorno cloud.
- **PASS · 5 pruebas Android en Robolectric (API 35):** recorrido real por la interfaz y parada al ir al fondo sin rearmar al regresar; captura sintética, persistencia, reapertura y exportación de WAV/JSON/CSV con checksum; cancelación de arranques pendientes; imposibilidad de emitir desde simulación; rechazo de hardware ausente sin cambiar de entrada/salida silenciosamente.
- **PASS · lintDebug:** cero errores. Dos advertencias conocidas: existe una versión más reciente de Robolectric (se mantiene la versión estable probada), y el control de espacio usa bytes libres físicos sin contar cachés recuperables de otras apps (criterio conservador).
- **PASS · assembleDebug:** APK generado en `app/build/outputs/apk/debug/app-debug.apk`; firma APK v2 verificada con apksigner. Contiene biblioteca de Compose para arm64; solicita RECORD_AUDIO y el permiso interno de AndroidX, sin permiso INTERNET.
- **PASS · instalación reproducible:** `bash scripts/setup-cloud.sh` completó con exit 0 reutilizando las descargas verificadas, licencias SDK, compilación, suites, lint y verificación de firma.

Total: **16 pruebas, 0 fallos, 0 errores y 0 omitidas**. Las pruebas DSP ya ejecutadas se reutilizaron por Gradle en la ejecución conjunta al no haber cambiado el módulo. El análisis y los archivos WAV sí se ejercitaron; una tarea sin fuentes o un check de configuración omitido por Gradle no se cuenta como prueba.

Los XML/HTML de resultados se generan bajo `dsp/build/test-results/test`, `dsp/build/reports/tests/test`, `app/build/test-results/testDebugUnitTest` y `app/build/reports/tests/testDebugUnitTest`. El informe lint queda en `app/build/reports/lint-results-debug.html`.

## No ejecutado en hardware

- Instalación y arranque en el S24 Ultra físico; versión Android/One UI.
- Compatibilidad del DAC concreto y entrada integrada/salida USB simultáneas.
- Diez minutos de continuidad, carga térmica y consumo.
- Confirmación física de las rutas, timestamps, procesado residual del micrófono y banda útil.
- Desconexión USB, revocación del permiso, bloqueo/pérdida de foco con salida física y texto grande/orientación en el teléfono real.

Robolectric ejecuta código Android en JVM con dispositivos simulados. No es un emulador completo ni una prueba de un teléfono/DAC. Cero pérdidas detectadas no excluye overruns de captura no expuestos por Android.

## Siguiente hito

Ejecutar el protocolo físico de `10_COMPILAR_Y_PROBAR.md` y conservar sus WAV/JSON. En software, continuar H2 con ruido rosa y bandado validado; después H3/H4 con correlación y barrido/deconvolución. No se ha implementado ni validado compensación temporal, delay, SPL absoluto o equivalencia con analizadores de referencia.
