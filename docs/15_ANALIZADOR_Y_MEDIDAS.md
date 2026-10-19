# SONDA Analizador 0.3.1 · RTA, espectrograma y diferencias entre fuentes

Esta edición permite comparar el tiempo de llegada de cada sistema de altavoz al mismo punto de medida. La vista principal muestra sus diferencias respecto a una fuente de referencia, junto con las curvas de magnitud. La cadena completa forma parte de la medida: procesador, amplificación, altavoz y recorrido acústico. El generador independiente aprobado y su biblioteca 0.2.0 se conservan en sus propias etiquetas. La versión del analizador es experimental: una compilación correcta y las pruebas sintéticas no validan el teléfono, un DAC, un micrófono ni la precisión acústica del conjunto.

## Instalar y conservar la versión

- [APK Android](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-debug.apk).
- [Paquete de instalación ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-instalar.zip).
- [Código fuente de esta edición](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-codigo.zip).
- Repositorio: [papaluis-b/Codex-gptpublico](https://github.com/papaluis-b/Codex-gptpublico). Rama de desarrollo: `sonda-analizador-v0.3.1`; referencia conservada de entrega: `analizador-v0.3.1`.

Si la descarga falla dentro de ChatGPT, copiar el enlace y abrirlo en Chrome. El APK usa la identidad `es.sonda`, por lo que actualiza la aplicación general con una firma compatible; SONDA Generador (`es.sonda.generador`) sigue siendo una aplicación aparte. Exportar las medidas antes de desinstalar: Android elimina el almacenamiento privado de una app al desinstalarla.

El permiso de micrófono se solicita para capturar. En Android 12 o posterior, la exploración de rutas Bluetooth puede requerir también el permiso de dispositivos cercanos. Después de conceder permisos hay que pulsar **Emitir y medir** de nuevo: aceptarlos no inicia la emisión. No se necesita una cuenta, conexión a Internet para medir ni permiso amplio de almacenamiento. El emparejamiento Bluetooth se realiza desde los ajustes de Android.

## Módulos de trabajo

| Módulo | Uso y unidades |
| --- | --- |
| RTA / espectro en directo | Observar la captura actual, su promedio y máximos. El espectro de densidad usa dBFS/Hz; el RMS de captura usa dBFS. |
| Espectrograma en directo | Ver cómo cambia el contenido en frecuencia con el tiempo mediante color. No equivale a una medida de reverberación. |
| Medir | Emitir un barrido o pulsos conocidos y comparar la última medida con una fuente de referencia compatible. Se conserva el impulso, la magnitud relativa y el tiempo registrado del sistema. |
| Fuentes | Conservar hasta diez fuentes, revisar sus curvas y ver las diferencias temporales respecto a Fuente 1, Fuente 2 o cualquier otra referencia elegida. |

En **Analizar**, elegir **RTA**, **Espectrograma** o **Ambos** según la vista que interese. El analizador pasivo puede funcionar con micrófono sin salida conectada. La medición activa necesita entrada y salida seleccionadas y verificadas. El generador manual, las capturas WAV y el diagnóstico de audio continúan disponibles. Un cambio de módulo no convierte la señal manual en un barrido de medición.

El espectro usa una ventana Hann de 8192 muestras y promedio de potencias lineales. A 48 kHz, cada ventana dura aproximadamente 170,67 ms y los bins están separados 5,8594 Hz. La tasa de dibujo no mejora esa resolución. Los colores del espectrograma expresan densidad espectral, no SPL; una zona persistente también puede deberse a la ventana de análisis. No se atribuye certificación IEC a bandas o gráficas.

## Entradas y salidas

La selección de entrada y salida es independiente. Las rutas se solicitan a Android y después se comprueba el dispositivo realmente activo. Si Android no respeta la combinación, la app debe detener la medida e indicar la causa; no se acepta otra ruta de forma silenciosa.

| Combinación | Condición y alcance |
| --- | --- |
| Micrófono interno + salida USB | Punto de partida. Permite mantener el funcionamiento anterior y medir la cadena con el micrófono del teléfono. |
| Micrófono USB + salida USB | Requiere una interfaz que exponga entrada y salida a Android. Un DAC que solo tiene salida no puede aportar un micrófono. |
| Micrófono USB + salida Bluetooth | Exploración experimental de A2DP o BLE Audio visible como ruta multimedia. Android y los dispositivos deben permitir la combinación. |
| Micrófono interno + salida Bluetooth | También depende del enrutamiento real disponible. Conserva las mismas limitaciones de tiempo de Bluetooth. |

No se utiliza Bluetooth SCO ni el modo de comunicación telefónica como sustituto de una ruta multimedia. BLE Audio solo puede aparecer donde la versión de Android y el equipo lo expongan. Un nombre visible o una preferencia aceptada no prueban que el audio físico siga esa ruta.

Al preparar la medida, la captura se abre antes de la excitación y la salida comienza con ceros. Se verifica el enrutamiento antes de emitir. Desconectar un dispositivo, cambiar de ruta o volumen durante la medida, perder el foco de audio o abandonar el primer plano cancela la operación. Al regresar hay que iniciar otra medida; no se reanuda una señal automáticamente.

El motor de medida solicita 48 kHz, captura mono float32 y salida estéreo con la misma señal en ambos canales. Esta edición exige que Android devuelva 48 kHz para ambos streams; no adapta silenciosamente otro formato. Esto no excluye resampling dentro del sistema o del dispositivo. La captura continúa después de la señal para recoger llegadas y cola. El micrófono Bluetooth no forma parte de las entradas admitidas en esta edición.

## Primera medida de un altavoz

1. Conectar el DAC o interfaz y seleccionar la entrada y la salida. Confirmar en el diagnóstico las rutas activas, las frecuencias de muestreo y los avisos de procesado. Mantener el micrófono en el punto de escucha elegido.
2. Dejar activo un solo altavoz. Mantener los demás silenciados en la mesa o procesador para no mezclar llegadas.
3. Abrir **Medir altavoces** y elegir **Barrido log** o **Pulso**. El nivel inicial es −30 dBFS y puede ajustarse entre −60 y −12 dBFS. Comenzar con ganancia baja en la cadena y comprobar que la captura recibe señal sin clipping. El nivel digital no permite conocer por sí solo el nivel acústico.
4. Pulsar **Emitir y medir** y esperar la captura completa, incluida la cola. No mover el micrófono ni cambiar rutas, volumen o procesado durante la secuencia. **Explorar SIMULACIÓN · sin emisión** produce una prueba sintética identificada y no emite audio.
5. Revisar el resultado y los avisos. Un pico de correlación no garantiza llegada directa: una reflexión más fuerte puede desplazar el candidato. Los timestamps de ambos streams deben ser válidos para conservar una referencia temporal de la emisión y la captura.
6. Guardar en **Fuente 1**. Activar el siguiente altavoz, sin mover el micrófono ni cambiar la configuración de medida, repetir y guardar en **Fuente 2**. Continuar hasta Fuente 10 según sea necesario.
7. La matriz abre en **Diferencias**, con **Fuente 1** como referencia inicial. Elegir Fuente 2 u otra fuente medida cuando interese cambiar el punto de comparación. Revisar las diferencias antes de decidir cualquier ajuste en el procesador externo.

No se aplican retardos ni ecualización automáticamente. El procedimiento de cada altavoz debe ser equivalente; si se cambia configuración, iniciar una nueva serie de comparación.

## Barrido y pulso

El barrido senoidal logarítmico inicial recorre 40 Hz–16 kHz durante 5 s; se ofrecen duraciones de 2, 5 y 10 s. Los límites son editables dentro del rango permitido, entre 20 Hz y 20 kHz. Lleva envolventes, 0,3 s de silencio previo y 2 s de cola, y se conserva la excitación exacta utilizada. La captura incorpora margen adicional para llegadas retrasadas; la secuencia completa dura más que el barrido seleccionado. El rango de excitación no garantiza que el micrófono o altavoz tengan respuesta útil en todo ese rango.

Capturar el tiempo de cola programado no demuestra que el decaimiento físico haya terminado. Una sala o sistema con una cola más larga puede quedar truncado y alterar la respuesta. Esta edición no calcula ni valida RT60 a partir de esa cola.

La alternativa de pulso es una señal corta de banda limitada, de 0,08 s, repetida cinco veces. No es un impulso digital de una sola muestra. Se conservan las capturas individuales y la dispersión temporal entre repeticiones. Si las llegadas son ambiguas o cambian apreciablemente, repetir antes de comparar altavoces.

El DSP nuevo reside en `:measurement-core`, Kotlin/JVM sin Android, y no modifica el DSP conservado del generador 0.2.0. La detección compara la captura con la excitación mediante correlación o filtro adaptado. La respuesta usa deconvolución espectral regularizada para no dividir por valores prácticamente nulos de la excitación. La respuesta se limita a la banda excitada; no se inventa información fuera de ella.

La curva de **magnitud relativa en dB** expresa la relación con la excitación digital guardada. Incluye altavoz, sala, micrófono, interfaz y procesado de la ruta; no se le resta automáticamente un nivel medio para hacer coincidir curvas. No es una medida SPL, una respuesta anecoica ni una curva con compensación de micrófono. La llegada y el impulso conservan su origen temporal aunque una gráfica facilite su lectura. No se declara fase absoluta ni group delay calibrado.

La búsqueda de delay abarca de 0 a 1,5 s y usa el valor absoluto de la correlación para admitir una inversión de polaridad, que se registra como aviso. Compara candidatos y ruido previo para rechazar una señal insuficiente o una llegada ambigua. Los umbrales de calidad son criterios heurísticos de esta implementación, no porcentajes de precisión física. El ajuste submuestra del pico es numérico; no demuestra esa misma resolución en una medida real.

Una medida rechazada se identifica como **MEDIDA NO VÁLIDA · revisa los motivos**. No muestra un retardo principal ni permite guardarlo en una fuente. Si aparece una curva, se etiqueta **CURVA DE DIAGNÓSTICO · medida no válida** y no debe usarse como resultado de respuesta.

## Qué significa el tiempo comparado

El objetivo es obtener **la diferencia temporal entre sistemas completos en un punto fijo**. Con la misma cadena de medida y el micrófono inmóvil:

`Δt_i = t_i − t_referencia`

Si cada tiempo contiene la misma latencia de medida `L`, entonces `t_i = L + T_i` y `t_referencia = L + T_referencia`. Al restar, esa latencia común y estable se cancela: `Δt_i = T_i − T_referencia`. **Comparar estas diferencias no requiere conocer L mediante una calibración absoluta.** El procesado propio de cada sistema y su recorrido hasta el micrófono forman parte de la diferencia que se busca medir.

El tiempo bruto registrado se conserva para diagnóstico: latencia residual de entrada/salida + retardo del procesado y del altavoz + propagación acústica. Puede consultarse al desplegar el diagnóstico del sistema y permanece en los archivos guardados. La app no necesita aislar el retardo puro del altavoz para este objetivo diferencial, y no resta una cifra genérica de Android, del DAC o de Bluetooth.

Los frames de captura y salida se relacionan mediante timestamps de ambos streams en la base monotónica de Android. El instante de pulsar Medir o de escribir un buffer no se usa como instante físico de emisión. Si faltan los timestamps, son discontinuos o la referencia no resulta fiable, la medida debe quedar inválida para delay cuantitativo. El motor requiere al menos 100 ms de avance de timestamps, rechaza saltos internos mayores de 2 ms y considera inválida una deriva relativa superior a 200 ppm. Estos controles comprueban la integridad temporal de los streams; son distintos de conocer por calibración la latencia absoluta común. No expresan una precisión física prometida ni validan físicamente el DAC.

Entrada USB, salida USB, captura interna y Bluetooth pueden emplear relojes distintos o resampling. La deriva puede deformar el impulso y el barrido. Esta edición no aplica una corrección genérica de deriva ni garantiza sincronía entre dispositivos.

Clipping, underruns de salida o discontinuidades detectadas invalidan la medida. AudioRecord no expone todos los overruns posibles: la ausencia de avisos no demuestra que una ruta física no haya perdido muestras.

En Bluetooth se añaden codec, búferes y latencia que puede variar entre conexiones o emisiones. La parte común se cancela si se mantiene estable en las medidas comparadas. Si cambia, ese cambio puede confundirse con una diferencia del sistema de altavoz. Lo mismo ocurre al cambiar rutas o procesado entre medidas. Un valor estable en cinco pulsos no demuestra que la latencia será estable en la siguiente serie; por eso se repiten las medidas y se comprueba la ruta concreta.

## Matriz: diez fuentes, ms y metros equivalentes

Cada espacio, **Fuente 1** a **Fuente 10**, contiene una medida: tiempo registrado del sistema, espectro, señal utilizada, rutas, configuración y avisos. La vista inicial es **Diferencias** y la referencia inicial es **Fuente 1**. Puede elegirse Fuente 2 o cualquier otra fuente que tenga medida. Los tiempos brutos se mantienen disponibles en el diagnóstico desplegable. Para las fuentes comparables, la diferencia es:

`diferencia_ms = tiempo_fuente_ms − tiempo_referencia_ms`

Ejemplo: Fuente 1 = 120 ms y Fuente 2 = 124 ms. Tomando Fuente 1 como referencia, Fuente 2 muestra **+4 ms**: su llegada es posterior. Si ambas medidas sumasen el mismo offset de 100 ms, serían 220 y 224 ms y seguirían mostrando +4 ms. Al elegir Fuente 2 como referencia, Fuente 1 muestra **−4 ms**: su llegada es anterior. Un signo negativo expresa adelanto respecto a la referencia.

La conversión usa `metros_equivalentes = diferencia_ms × c / 1000`, con `c = 343 m/s` inicialmente y un valor editable entre 300 y 380 m/s. En el ejemplo, +4 ms equivalen a **+1,372 m**; al invertir la referencia, −4 ms equivalen a −1,372 m. Son **metros equivalentes de la diferencia temporal**. Pueden incluir diferencias de procesado y no tienen por qué representar solo una diferencia geométrica de distancias.

Solo se muestran diferencias válidas entre registros de la misma sesión y configuración compatible: rutas, fuente de captura, efectos, formatos/parámetros de medida y volumen registrados. La última medida también muestra su diferencia respecto a la referencia cuando comparten esa clave de comparación. Si la clave difiere, los tiempos individuales siguen disponibles en el diagnóstico pero la diferencia se marca no comparable. Al reiniciar la aplicación, las medidas nuevas pertenecen a otra sesión y no se comparan con las de la anterior. Una medida sintética no se compara con una real. Una clave igual es una condición de software, no una garantía de latencia física idéntica.

Puede verse la curva de la última medida o de una fuente guardada, y superponer la referencia si es compatible. Sustituir o vaciar un espacio pide confirmación, cambia su asignación actual y conserva sus registros originales en el historial privado. Un registro finalizado no se sobreescribe con el mismo identificador. Por ello, las medidas antiguas pueden ocupar espacio aunque ya no aparezcan asignadas a Fuente 1–10.

## Guardado y exportación

Al guardar una medida válida en una fuente, sus resultados y brutos se conservan en almacenamiento privado. La exportación crea un ZIP de las medidas guardadas que se comparte con el selector de Android; el usuario elige destino. Incluye audio del entorno capturado por el micrófono.

```text
matrix.json
manifest.json
records/<UUID>/
    metadata.json
    spectrum.csv
    excitation.wav
    capture.wav
    impulse.wav
    capture-repeat-N.wav   # cuando hay capturas de repetición adicionales
```

`matrix.json` describe los diez espacios actuales. `records/` incluye también los registros históricos sustituidos o vaciados. Los WAV conservan las muestras mono float32, no una imagen de la gráfica. Los metadatos describen unidades, retardo observado sin compensación, configuración, timestamps disponibles, incidencias y hashes SHA-256 de los brutos. El CSV contiene frecuencia en Hz y magnitud relativa en dB.

La escritura de medidas y exportación se realiza fuera de los hilos de audio. Se requiere espacio para los archivos y una reserva de 50 MB. Ante una matriz dañada se muestra un error y se conserva el contenido para recuperación; no se borra ni se sustituye silenciosamente por una matriz vacía. No hay borrado automático de históricos.

## Protocolo corto de comprobación física

Registrar modelo del teléfono, Android/One UI, interfaz, micrófono, altavoz, cable/hub, rutas activas, nivel, volumen Android y configuración. Un resultado obtenido con otro equipo no valida esta combinación.

**Micrófono interno + salida USB:** con una sola fuente y posición fija, comprobar físicamente qué micrófono recoge y qué salida emite. Hacer cinco barridos y cinco series de pulsos sin modificar nada. Anotar dispersión, clipping y avisos. Repetir después de cerrar/abrir los streams para observar si cambia el offset. Desconectar USB y comprobar cancelación y ausencia de reanudación automática.

**Micrófono USB + salida USB:** confirmar que la interfaz expone una entrada y que la ruta activa corresponde al micrófono conectado. Repetir el protocolo anterior. Si se puede introducir un delay conocido en el procesador del altavoz sin mover nada, comprobar el incremento recuperado. Ese incremento es una comprobación directa del diferencial de sistema que interesa medir.

**Micrófono USB + salida Bluetooth:** emparejar primero desde Android; escoger salida multimedia A2DP/BLE y entrada USB. Confirmar ambas rutas activas, repetir diez series sin reconectar y después reconectando Bluetooth. Registrar cambios y dispersión para comprobar si la parte común se mantiene estable. Si no hay timestamps fiables o la combinación no se mantiene, conservar el diagnóstico y no utilizar las diferencias para alinear altavoces. No descontar manualmente una latencia «típica» de Bluetooth.

En las tres combinaciones, salir de la app, bloquear pantalla, provocar pérdida de foco y cambiar de ruta deben silenciar/cancelar sin rearmar. Para comprobar variaciones de tiempo acústico, desplazar el micrófono una distancia conocida manteniendo la geometría controlada. Las reflexiones o un subgrave requieren interpretar la llegada por banda; el pico mayor no se identifica automáticamente como llegada directa.

## Compilar y verificar

Herramientas de la base: Gradle 8.13, JDK 21, bytecode 17, AGP 8.13.2, Kotlin 2.2.21, compileSdk/targetSdk 36 y minSdk 28. El entorno y la instalación se explican en el README y en `scripts/setup-cloud.sh`.

```bash
source scripts/cloud-env.sh
./gradlew :measurement-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El APK de compilación queda en `app/build/outputs/apk/debug/`. La entrega pública incorpora sus checksums en `artifacts/SHA256SUMS.txt`.

### Evidencia histórica de 0.3.0

Comprobación de software del 18 de octubre de 2026: **102 pruebas superadas, sin fallos ni pruebas omitidas**.

| Área | Resultado y alcance |
| --- | --- |
| `measurement-core` | 16 pruebas PASS. Incluyen regresiones a 48 kHz con barridos de 5/10 s, pulso de 80 ms, delays de 0/1,5 s y respuesta FIR con reflexión sintética. |
| Aplicación general | 39 pruebas PASS: flujos de app/audio, pantalla de medida, controlador, backend de audio controlado, timestamps y almacenamiento/exportación. Las pruebas de backend no usan hardware físico. |
| DSP conservado | 27 pruebas PASS. |
| Biblioteca del generador | 14 pruebas PASS. |
| App del generador | 6 pruebas PASS. |
| Lint de la aplicación general | 0 errores; 3 avisos conocidos sobre actualización de Robolectric y consultas de espacio disponible. |
| Compilación e identidad | APK generado: `es.sonda`, versión 0.3.0, versionCode 3, minSdk 28 y targetSdk 36. Firma compatible con la aplicación general anterior. |

Certificado de firma debug, SHA-256: `e647455e30c1617ccca0eb3e855e7d25a3f20d92d61752a094ada61ae7b703ef`. El log de la verificación en el entorno de desarrollo es `/tmp/sonda-analyzer-verified-build.log`; los resultados JUnit quedan en los directorios `build/test-results/` de cada módulo al ejecutar las comprobaciones.

### Verificación de 0.3.1

La actualización 0.3.1 centra la presentación en las diferencias entre sistemas y mantiene los tiempos brutos para diagnóstico y exportación. La compilación y verificación de software del 18 de octubre de 2026 finalizan con **104 pruebas superadas**: 41 de la aplicación general, 16 del núcleo de medida y 47 de regresión entre DSP, biblioteca y app del generador.

Las dos pruebas nuevas de interfaz cubren la invariancia al sumar un offset común de +100 ms, los signos al cambiar la referencia y el rechazo de comparaciones incompatibles. El lint conserva 0 errores y 3 avisos conocidos. El APK corresponde a `es.sonda`, versión 0.3.1 y versionCode 4. Log de esta verificación en el entorno de desarrollo: `/tmp/sonda-analyzer-differentials-build.log`.

Las siguientes comprobaciones físicas permanecen **NO EJECUTADAS** por el entorno de desarrollo:

- Instalación y uso en el teléfono del usuario.
- Rutas simultáneas con el DAC o micrófono USB concreto.
- Bluetooth A2DP/BLE con captura USB y estabilidad de sus timestamps.
- Repetibilidad acústica, precisión de delay y contraste con referencia independiente.

No se declara una precisión temporal o de respuesta acústica a partir de las pruebas de software.

## Fuentes primarias

- [AudioRecord: captura y `getTimestamp`](https://developer.android.com/reference/android/media/AudioRecord).
- [AudioTrack: reproducción y `getTimestamp`](https://developer.android.com/reference/android/media/AudioTrack).
- [AudioRouting: `setPreferredDevice` y `getRoutedDevice`](https://developer.android.com/reference/android/media/AudioRouting).
- [AudioTimestamp: relación entre frame y tiempo](https://developer.android.com/reference/android/media/AudioTimestamp).
- [AudioDeviceInfo: tipos USB, A2DP, BLE y capacidades declaradas](https://developer.android.com/reference/android/media/AudioDeviceInfo).
- [Permisos Bluetooth de Android 12 y posteriores](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

Estas APIs describen lo que Android permite solicitar u observar; no aseguran que una combinación concreta esté disponible ni caracterizan su latencia física.
