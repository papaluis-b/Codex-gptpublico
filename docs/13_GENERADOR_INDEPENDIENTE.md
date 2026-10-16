# SONDA Generador 0.1.0 · aplicación independiente

El generador se entrega también como una aplicación dedicada. Nombre en el teléfono: **SONDA Generador**, con icono propio. Paquete `es.sonda.generador`, versión 0.1.0 y versionCode 1. Puede coexistir con SONDA (`es.sonda`, 0.2.0); no hay que sustituir ni desinstalar la aplicación general.

## Instalación y uso

1. Descargar `SONDA-Generador-0.1.0-instalar.zip` desde el enlace del README. Si ChatGPT no descarga, copiar el enlace en Chrome.
2. Abrir Mis archivos → Descargas → ZIP → Extraer, y tocar `SONDA-Generador-0.1.0-debug.apk`. Autorizar la fuente que indique Android y pulsar Instalar.
3. Abrir SONDA Generador. Conectar el DAC USB y seleccionarlo, o elegir explícitamente Altavoz del teléfono · prueba. Pulsar Preparar salida. Este paso abre el stream en silencio y comprueba la ruta activa.
4. Elegir Seno o Ruido rosa. Seleccionar 1/3, 1/24, 1/32 o 1/48 de octava. Los botones − / + avanzan esa fracción desde la frecuencia actual; también se admite frecuencia manual con punto o coma decimal.
5. En ruido rosa, la fracción seleccionada fija también la anchura de la campana. Se muestran centro, bordes del filtro a −3 dB y estabilización aproximada. Los filtros tienen caída gradual fuera de la banda.
6. Ajustar el nivel y pulsar Emitir. SILENCIO está siempre accesible; Detener salida libera la sesión. Cambiar de tipo de señal silencia y exige pulsar Emitir de nuevo.

El seno usa dBFS pico y el ruido dBFS RMS con referencia 1; límites de −12 y −24 dBFS respectivamente. El valor inicial es −40 dBFS. El volumen multimedia Android y la ganancia física son independientes del nivel digital. Para las definiciones de banda, normalización, algoritmo rosa y límites estadísticos, ver `12_GENERADOR_FRACCIONES_Y_ROSA.md`.

La app funciona en primer plano. Al salir, bloquear el teléfono, perder foco de audio o cambiar/desconectar la ruta, se detiene la salida. Volver a la app no reactiva la señal. La selección de controles no activa la emisión. No se incluye reproducción en segundo plano ni conexión entre SONDA y SONDA Generador para emitir desde una mientras se usa la otra.

## Arquitectura y permisos

Tres módulos: `:app` es SONDA general; `:generator` es la app independiente; `:dsp` contiene la generación de señal y las pruebas matemáticas compartidas. `:generator` depende de `:dsp` y no de `:app`.

El generador abre únicamente AudioTrack: solicita salida estéreo float32 a 48 kHz, usa el sample rate del stream abierto y verifica el dispositivo activo. Los estados distinguen preparación, salida abierta en silencio y emisión. Un identificador de sesión impide que un arranque cancelado o un worker antiguo reactive audio. Los filtros se preparan fuera del hilo de salida; este reutiliza sus buffers. El diagnóstico muestra ruta, formato, volumen y contadores disponibles de underrun/clipping.

No usa micrófono, red, almacenamiento compartido ni cuentas. No solicita RECORD_AUDIO ni INTERNET. No contiene captura WAV, analizador, biblioteca de medidas ni simulación de entrada. Conserva los controles mientras vive su ViewModel; al abrir una instancia nueva vuelve a los valores iniciales y al silencio.

## Compilación

Mismo JDK 21, SDK 36, Gradle 8.13 y versiones Android/Kotlin de SONDA. Preparación completa del entorno y validación de ambas apps: `bash scripts/setup-cloud.sh`. Para una compilación local del generador:

```bash
source scripts/cloud-env.sh
./gradlew :dsp:test :generator:testDebugUnitTest :generator:lintDebug :generator:assembleDebug --no-daemon --console=plain
apksigner verify generator/build/outputs/apk/debug/generator-debug.apk
adb install -r generator/build/outputs/apk/debug/generator-debug.apk
```

El APK de entrega usa firma de desarrollo. El ZIP de código contiene las fuentes de los tres módulos y las instrucciones; no incluye SDK, JDK, cachés, claves de firma ni APK. Las dependencias y la referencia primaria del filtro rosa se documentan en `DEPENDENCIES.md`.

## Validación automática · 16 de octubre de 2026

Comprobación conjunta de los tres módulos:

```bash
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :generator:testDebugUnitTest :generator:lintDebug :generator:assembleDebug --no-daemon --console=plain
```

- **20 pruebas DSP, PASS.** Se conservan las comprobaciones del seno, ruido rosa, bandas, RMS, transiciones y estabilidad descritas en `12_GENERADOR_FRACCIONES_Y_ROSA.md`.
- **10 pruebas del motor independiente, PASS.** Backend de salida controlado: escritura de ceros antes de verificar ruta; rechazo de salida distinta a la seleccionada; pérdida de ruta y restauración sin rearme; foco denegado/perdido; desconexión USB; cancelación durante apertura; arranques/paradas rápidos; sample rate devuelto de 32 kHz con ajuste de la banda; cambio de tipo en silencio; error de escritura y cierre sin rearme.
- **4 pruebas de interfaz, PASS.** Recorrido Compose por las cuatro fracciones, pasos y campana rosa; frecuencia manual válida y rechazo de texto inválido/no finito/fuera de rango; unidades pico/RMS y límites de nivel; identidad independiente y ausencia de permisos de micrófono e Internet.
- **8 pruebas de SONDA general, PASS.** La app general conserva las comprobaciones de la entrega 0.2 sin modificar su código.
- **Compilación y lint de ambas apps, PASS.** El generador tiene cero errores lint y una advertencia de versión más reciente de Robolectric; se mantiene la versión fijada y probada 4.16.1. SONDA conserva sus dos advertencias conocidas. `bash -n scripts/setup-cloud.sh` valida la sintaxis del script actualizado para ambas apps.
- **APK, PASS.** Firma v2 verificada con apksigner; comprobados paquete `es.sonda.generador`, nombre SONDA Generador, versión 0.1.0, versionCode 1, Activity de lanzamiento propia y biblioteca arm64 de Compose. El único permiso declarado en el APK es el permiso interno de receptor AndroidX; no hay micrófono ni Internet. El APK general de SONDA 0.2.0 conserva exactamente su SHA-256 anterior.

Total del proyecto: **42 pruebas, 0 fallos, 0 errores y 0 omitidas**. Los checks sin cambios reutilizan resultados de Gradle. Tras la revisión se corrigió la publicación transitoria de diagnósticos de una sesión anterior y se repitieron las pruebas, lint y compilación del módulo independiente. Los XML de la nueva app se generan en `generator/build/test-results/testDebugUnitTest`; el informe lint en `generator/build/reports/lint-results-debug.html`.

Estas pruebas ejecutan código Android en JVM y un backend simulado de salida; no validan un AudioTrack físico, Samsung ni DAC.

## Prueba física pendiente

No se ha instalado ni ejecutado esta app en el Samsung ni en un DAC físico. En el teléfono:

- Confirmar que SONDA y SONDA Generador aparecen como apps separadas y que el generador no pide acceso al micrófono.
- Preparar el DAC seleccionado, contrastar el dispositivo con el diagnóstico y comprobar que permanece en silencio hasta pulsar Emitir.
- Probar seno y ruido rosa, todas las fracciones, frecuencia manual, niveles y cambios de tipo. Verificar campanas con analizador externo y esperar la estabilización indicada; las bandas estrechas requieren capturas largas.
- Emitir durante diez minutos y revisar underruns, clipping, temperatura y consumo.
- Probar SILENCIO, Detener salida, desconexión USB, cambio de ruta, pérdida de foco, Home, bloqueo y rotación; comprobar que la señal no se reanuda sola.
- Comprobar texto grande y orientación horizontal, y registrar teléfono, Android/One UI, DAC y volumen usados.
