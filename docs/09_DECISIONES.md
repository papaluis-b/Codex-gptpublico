# Decisiones de implementación

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

## 2026-10-16 · SONDA 0.2.0, generador por fracciones de octava

Se incorporan las modificaciones solicitadas: pasos seleccionables de 1/3, 1/24, 1/32 y 1/48 de octava y elección entre seno y ruido rosa en campanas de esa anchura. Se usan razones binarias exactas desde la frecuencia actual. No se presentan como frecuencias preferidas ni filtros certificados IEC.

La campana es un paso banda Butterworth de orden total 4, con bordes a −3 dB y caída gradual. Se normaliza el ruido a un RMS objetivo independiente de la anchura; el nivel del seno sigue referido a pico. El ruido se limita a −24 dBFS RMS y el seno a −12 dBFS pico. El diseño y la calibración de coeficientes se realizan fuera del hilo de salida. El cambio de campana pasa por ganancia cero con rampas de 20 ms; cambiar de seno a ruido o viceversa exige volver a pulsar Emitir.

Los metadatos añaden tipo, fracción, bordes, referencia de nivel, algoritmo, semilla y clipping del generador. El WAV sigue siendo la captura original del micrófono. La simulación conserva su señal sintética fija de 1 kHz y no reproduce la selección del generador ni emite audio. Funcionamiento, fuentes, pruebas y pendientes de 0.2 en `12_GENERADOR_FRACCIONES_Y_ROSA.md`.

## 2026-10-16 · SONDA Generador 0.1.0, aplicación independiente

El usuario solicita una app separada como módulo de generador. Se añade el módulo Android `:generator`, con nombre SONDA Generador, icono y applicationId `es.sonda.generador`. La app general sigue en `:app` con `es.sonda`; ambas pueden coexistir. El generador independiente comparte `:dsp`, con la misma señal, pasos y controles de nivel de SONDA 0.2. No se copia el motor matemático ni se introduce otra dependencia de DSP.

Se implementa salida AudioTrack independiente. Como esta app solo emite, no abre AudioRecord ni necesita permiso de micrófono o una entrada antes de emitir. La selección USB o de altavoz es explícita; la ruta activa se comprueba con el stream en silencio antes de habilitar Emitir. Foco, desconexión, cambio de ruta, error y segundo plano detienen la salida sin rearmar. No se añade servicio de segundo plano ni se establece comunicación entre aplicaciones. Las pruebas con un backend de salida controlado ejercitan los estados sin presentarlos como validación física.

La aplicación independiente no incluye analizador, grabación, exportación de medidas ni simulación de captura. Su guía, compilación y validación se mantienen en `13_GENERADOR_INDEPENDIENTE.md`.

## 2026-10-17 · SONDA Generador 0.2.0, ajuste continuo

El usuario solicita sensación de control analógico mediante un potenciómetro rotatorio o deslizante, además de los pasos. Se elige un deslizador horizontal continuo con escala logarítmica: posición `p` de 0 a 1 → `20 * (máximo/20)^p` Hz. Reparte el recorrido por razones de frecuencia y ofrece manejo directo con un dedo. El máximo procede del sample rate y tipo/banda del generador. Los botones de fracciones y la entrada exacta siguen sincronizados con el mismo estado de frecuencia; el deslizador no encaja en los pasos seleccionados.

Los cambios se aplican en cada evento de arrastre, sin esperar a levantar el dedo. No activan una salida silenciada; si ya se emite, conservan el armado y el nivel. La ruta continua del ruido rosa enlaza dos bancos de filtros preasignados mediante un fundido lineal de 20 ms y retiene la última solicitud durante cada fundido. Así se evita bajar a cero y reiniciar la campana en cada evento. Los ajustes discretos conservan su transición anterior. Los filtros y su normalización se siguen preparando fuera del hilo de audio, sin interpolar coeficientes.

La señal de una campana muy estrecha sigue necesitando asentamiento y su RMS puede variar durante el movimiento. El control continuo es manual; no constituye un barrido de medición. La actualización conserva applicationId e incrementa versionCode a 2 para instalarse sobre SONDA Generador 0.1.0.

## 2026-10-18 · Conservación y módulo reutilizable

El usuario aprueba SONDA Generador 0.2 y solicita conservarla para integrar o evolucionar dentro o fuera de otras apps. Se fija la referencia aprobada mediante `generador-app-v0.2.0` en el commit `01eb7fab88218c9361dd3a9b4e305ffe086d2ce5`; el APK y ZIP de fuentes originales se conservan byte a byte.

Se extraen motor/modelos/backend y pantalla a `:generator-kit`, biblioteca Android con API propia sin depender de Activity, ViewModel, BuildConfig, icono o identidad de la app. Los paquetes públicos del motor y DSP se mantienen. `:generator` pasa a ser un anfitrión de esa biblioteca. Se preservan los archivos del motor y DSP, sus algoritmos y controles. La pantalla añade parada por pausa o salida de composición sin cerrar el motor propiedad del anfitrión.

Se publican `es.sonda:generator-kit:0.2.0` como AAR y `es.sonda:dsp:0.2.0` como JAR, con fuentes y metadatos transitivos, exclusivamente en un directorio Maven local. Se comprueba la integración con proyectos independientes que consumen esos binarios: Android/Compose y Java/JVM. La etiqueta `generator-kit-v0.2.0` conserva la extracción y su paquete; futuras modificaciones deben usar versiones y etiquetas nuevas.

La entrega incluye binarios, repositorio Maven, fuentes, ejemplos, API, protocolo de validación, referencias Git, checksums y los originales aprobados. El script de empaquetado contrasta los archivos del motor/DSP contra las fuentes aprobadas y registra sus hashes. La licencia general del código propio se mantiene sin asignar; no se elige una nueva licencia como efecto de esta extracción. Guía completa en `14_MODULO_REUTILIZABLE.md`.

## 2026-10-18 · SONDA Analizador 0.3.0, fuentes y medida con señal conocida

El usuario solicita retomar el analizador con RTA, espectrograma en directo y medidas de magnitud y retardo mediante barrido o pulso, además de una matriz de diez altavoces. Se continúa la aplicación general `:app` (`es.sonda`) como edición experimental 0.3.0. El APK aprobado del generador, su biblioteca y el núcleo DSP conservado 0.2.0 mantienen sus referencias anteriores.

Se incorpora un núcleo de medida Kotlin/JVM separado en `:measurement-core`: excitación conocida, correlación/filtro adaptado, deconvolución espectral regularizada y respuesta relativa limitada a la banda excitada. El barrido inicial es logarítmico, 40 Hz–16 kHz y 5 s, con duraciones de 2/5/10 s. El pulso de banda limitada dura 0,08 s y se repite cinco veces. Se conservan excitación, captura, impulso, capturas individuales y metadatos; un gráfico no sustituye a los originales.

El tiempo se presenta como retardo observado sin compensación: suma de latencia residual de la ruta, procesado del altavoz y propagación acústica. Se utilizan frames/timestamps de entrada y salida en base monotónica; no se toma el botón ni una escritura de buffer como origen físico. La falta de referencia temporal válida impide un delay cuantitativo válido. No se descuenta una cifra genérica de Android o Bluetooth, no se declara «retardo puro» y no se muestran fase absoluta ni calibración acústica no verificadas.

La matriz conserva Fuente 1–10 y permite elegir cualquier fuente medida como referencia. Los tiempos se expresan en ms y metros equivalentes con velocidad inicial editable de 343 m/s. Las diferencias usan fuente menos referencia y pueden ser negativas. Solo se comparan registros compatibles de la misma sesión/configuración y del mismo tipo real/sintético. La igualdad de metadatos no demuestra constancia de latencia Bluetooth. La aplicación no aplica delay o EQ automáticamente.

Se mantienen entrada interna y salida USB como punto de partida y se permite explorar entrada USB independiente con salida USB o Bluetooth multimedia A2DP/BLE, cuando Android y los dispositivos lo permitan. Un DAC solo de salida no proporciona entrada de micrófono. No se usa SCO ni modo de comunicación como alternativa silenciosa. Los streams se abren con salida a cero, la captura precede a la excitación y se comprueban las rutas activas. Cambios de ruta/volumen, foco o primer plano cancelan sin reanudación automática.

Los registros finalizados usan UUID y no se sobrescriben. Sustituir o vaciar una fuente conserva los brutos históricos; la exportación ZIP contiene matriz actual, registros históricos, WAV float32 mono, CSV, JSON y hashes. La escritura se realiza fuera de los hilos de audio. No se añade nube, servicio externo, monitorización micrófono→salida ni borrado automático de históricos.

La validación de software termina con 102 pruebas superadas sin fallos ni omisiones: 16 del núcleo de medida, 39 de la aplicación general y 47 de regresión entre DSP, biblioteca y app del generador. Incluye casos a 48 kHz y backends de audio controlados; no son pruebas físicas. El lint de la app no presenta errores y conserva tres avisos conocidos. El APK 0.3.0 usa versionCode 3 y mantiene el certificado de firma de la aplicación general para actualizarla. Las combinaciones físicas de teléfono, DAC, micrófono y Bluetooth, la estabilidad de sus relojes y la precisión acústica siguen pendientes de protocolo real. Guía de uso, significado de las unidades, exportación, compilación y comprobación manual en `15_ANALIZADOR_Y_MEDIDAS.md`.

## 2026-10-18 · SONDA Analizador 0.3.1, diferencias del sistema como vista principal

El usuario precisa que la magnitud de interés es la diferencia temporal de cada sistema completo respecto a una referencia, con el micrófono fijo. Se centra el flujo en `Δt_i = t_i − t_referencia`: Fuente 1 es la referencia inicial y puede sustituirse por Fuente 2 o cualquier otra fuente medida. El procesado de cada sistema, altavoz y recorrido acústico forman parte del resultado buscado.

Una latencia común y estable de la cadena de medida se cancela al restar los dos tiempos, por lo que este objetivo diferencial no requiere conocer esa latencia mediante calibración absoluta. Por ejemplo, 120 y 124 ms producen +4 ms (+1,372 m equivalentes a 343 m/s); sumar 100 ms a ambos deja la diferencia intacta y elegir la segunda fuente como referencia da −4 ms para la primera. Si Bluetooth introduce una variación entre medidas, o cambian rutas o procesado, esa parte no se cancela y puede interpretarse erróneamente como diferencia del altavoz.

La matriz abre en Diferencias. Los tiempos brutos del sistema pasan al diagnóstico desplegable y siguen guardándose y exportándose. La última medida presenta su diferencia respecto a la referencia cuando su clave de comparación coincide; se conservan las comprobaciones de sesión, rutas, configuración y real/sintético. Los metros expresan el equivalente de la diferencia temporal y pueden incluir procesado; no se identifican siempre con diferencia geométrica de distancia.

La referencia de entrega es `analizador-v0.3.1` en la rama `sonda-analizador-v0.3.1`. La compilación de esta actualización termina correctamente y la verificación de software suma 104 pruebas superadas: 41 de la aplicación general, 16 del núcleo de medida y 47 de regresión del generador/DSP. Las dos pruebas nuevas de interfaz comprueban offset común de +100 ms, signos/cambio de referencia y configuraciones incompatibles. El lint mantiene cero errores y tres avisos conocidos; el APK usa versión 0.3.1 y versionCode 4. Los 102 tests de la sección anterior se conservan como evidencia histórica de 0.3.0. Las pruebas físicas de teléfono, USB y Bluetooth permanecen pendientes.
