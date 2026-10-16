# Decisiones de la primera versión

## 2026-10-16 · H0/H1, SONDA 0.1.0

El usuario autoriza implementar una primera aplicación funcional a partir del paquete de especificaciones. La entrega incluye H0/H1 y un analizador básico: entrada integrada, salida USB independiente, seno con rampa, WAV original, diagnóstico y exportación. Delay, deconvolución, ruido rosa, bandado por octavas y compensación quedan para los hitos siguientes; no aparecen controles que simulen esas funciones.

### Plataforma y herramientas

- Android nativo, Kotlin 2.2.21 y Jetpack Compose BOM 2025.10.01.
- Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 21; bytecode JVM 17.
- compileSdk/targetSdk 36, build-tools 36.0.0 y minSdk 28.
- Activity 1.11.0, Lifecycle 2.9.4, AndroidX Core 1.17.0.
- Versiones estables disponibles comprobadas en los índices oficiales de Google Maven y Maven Central; se elige un conjunto compatible de versiones fijadas, sin actualizaciones dinámicas.
- No se necesita servidor, cuenta, permiso de Internet ni servicio en segundo plano.

### Motor

Para este hito se usa AudioRecord/AudioTrack, float32, entrada mono y salida estéreo L+R, con workers dedicados de prioridad audio. La propuesta C++/Oboe no es una condición del producto: se reserva para una migración posterior si los perfiles y las pruebas de continuidad/latencia lo justifican. El DSP es Kotlin/JVM independiente de Android y no necesita JNI para las pruebas deterministas.

Se solicitan streams a 48 kHz y se utilizan los valores de configuración devueltos por Android. Estos valores no prueban el reloj físico ni ausencia de resampling. Si el formato no está disponible, se muestra el error; no se cambia de fuente silenciosamente.

El stream de entrada debe usar el ID del micrófono integrado y la salida el ID USB elegido. El altavoz interno requiere selección explícita del modo de prueba. Se abren streams en silencio, se comprueba routedDevice y solo después se admite Emitir. Solicitar una ruta no equivale a validarla físicamente. Toda pérdida/cambio de ruta, error de E/S, pérdida de foco o salida del primer plano detiene la emisión. Volver a la app no reactiva el tono.

La fuente solicitada es UNPROCESSED cuando Android declara soporte, con MIC como elección explícita si no lo declara. Se intenta desactivar AGC/NS/AEC controlables y se registra el resultado. No se garantiza que Samsung elimine todo el procesado.

Los hilos de E/S reutilizan buffers. La cola SPSC contiene 128 bloques de hasta 1024 muestras (≈2,73 s a 48 kHz), comunica pérdidas y conserva el índice original de frame. Un worker independiente escribe y analiza. No se rellena audio perdido ni se atraviesan huecos con una ventana FFT. La detección de pérdida de plataforma es parcial: Android no expone todos los overruns de captura. Se inspeccionan timestamps y underruns de salida; cero incidencias detectadas no prueba continuidad física.

### DSP y presentación

FFT radix-2 de 8192 muestras, Hann periódica, sin zero padding. PSD unilateral = factor unilateral × |FFT|² / (fs × sum(w²)); DC y Nyquist no se duplican. La amplitud de tono usa sum(w), y se distingue del eje de PSD. RMS referido a amplitud 1: un seno de pico 0 dBFS tiene RMS −3,0103 dBFS.

Promedio exponencial de potencia con constante 1 s calculada con tiempo de captura. El cursor selecciona el bin más cercano; no inventa resolución entre bins. El máximo se mantiene sobre la potencia promediada. El espectrograma muestra PSD sin promedio, escala logarítmica y color fijo. Las celdas sin bins no se interpolan para aparentar resolución. La simulación produce un seno determinista de 1 kHz, pico −20 dBFS, sin acceder al micrófono o altavoces.

El generador conserva fase entre bloques y al modificar frecuencia. El nivel arranca en −40 dBFS pico y se limita a −12 dBFS; transiciones de amplitud con rampa lineal de 20 ms. El silencio ante fallo pausa y vacía la salida. No existe monitorización micrófono→salida.

### Datos

Grabaciones explícitas de hasta 120 s, mono IEEE float32, con cabecera RIFF/fact. Se conservan amplitudes originales, frame inicial, sample rate, rutas, timestamps disponibles, eventos y estado sintético/real. WAV con checksum SHA-256. Exportación mediante FileProvider y selector Android, con confirmación de que contiene audio del entorno. Los ZIP quedan en la caché; los originales permanecen privados. No hay subida automática ni permisos amplios de almacenamiento.

La última ventana espectral disponible se exporta en CSV si pertenece a la grabación. No representa el espectro promedio de toda la sesión. No se presentan valores SPL, delay compensado, fase absoluta o métricas de calibración.

### Fuentes primarias

- [Índice AGP](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
- [Compose BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml)
- [Kotlin](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml)
- [Android SDK, paquetes y checksums](https://dl.google.com/android/repository/repository2-1.xml)
- [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)
- [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)
- [AudioRouting](https://developer.android.com/reference/android/media/AudioRouting)
- [AudioTimestamp](https://developer.android.com/reference/android/media/AudioTimestamp)

Las pruebas en JVM y la generación de un APK no validan el S24 Ultra ni el DAC. El informe de esta entrega se mantiene en `11_ESTADO_H0_H1.md`.
