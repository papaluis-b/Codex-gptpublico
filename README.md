# SONDA Android

Versión de especificación: 1.0 · 15 de octubre de 2026.
Nombre provisional, sin decisión de marca. Idioma del producto: español.

## SONDA Analizador 0.3.1 · RTA, espectrograma y medidas de altavoces

[Descargar instalador ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-instalar.zip) · [APK](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-debug.apk) · [Fuentes ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/analizador-v0.3.1/artifacts/SONDA-Analizador-0.3.1-codigo.zip) · [Guía de uso, medidas y rutas](docs/15_ANALIZADOR_Y_MEDIDAS.md).

RTA y espectrograma en directo como vistas independientes o conjuntas; módulo de medida con barrido logarítmico y pulso pasa-banda de cinco repeticiones. Prioriza los **diferenciales de llegada** de diez registros, **Fuente 1–Fuente 10**, respecto a la fuente de referencia elegida: Δms y metros equivalentes con signo. Conserva también la magnitud relativa y los tiempos originales del sistema en diagnóstico. Conserva señales emitidas, capturas, impulso, metadatos, repeticiones y espectros en un archivo local exportable. La simulación está identificada y se mantiene separada de las medidas reales.

Se mantiene micrófono interno + DAC USB. Se añaden entrada de micrófono USB y salida Bluetooth A2DP/BLE como opciones exploratorias, con selección explícita y comprobación de la ruta real. Un DAC debe exponer una entrada para admitir un micrófono; uno de salida solamente no añade esa capacidad. La app no necesita red ni cuentas; requiere permiso de micrófono y, al elegir Bluetooth en Android 12+, dispositivos cercanos. Emparejar Bluetooth desde los ajustes de Android.

El objetivo es comparar el sistema completo: `Δt = tiempo_fuente − tiempo_referencia`. La latencia común se cancela si permanece estable, sin necesitar conocerla mediante una calibración absoluta. Mantener el micrófono y la cadena sin cambios durante la serie. Bluetooth puede variar entre medidas y alterar el diferencial. Los metros expresan la diferencia temporal equivalente. Respuesta relativa, sin calibración SPL ni afirmación de respuesta anecoica. La validación física con teléfono, micrófono, DAC y Bluetooth sigue pendiente.

Instala el ZIP desde Chrome si la descarga de ChatGPT falla: Mis archivos → extraer → tocar el APK. Actualiza la app general `es.sonda` a 0.3.1; el generador independiente conserva su propia instalación y versión aprobada. La base del generador sigue fijada en `generator-kit-v0.2.0`.

Compilación y validación: `bash scripts/build-analyzer.sh`. El nuevo núcleo `:measurement-core` es Kotlin/JVM independiente de Android; `:app` lo integra con audio, persistencia e interfaz. Etiqueta de entrega `analizador-v0.3.1`, rama `sonda-analizador-v0.3.1`. No sustituir artefactos anteriores al evolucionar el módulo.

Verificación de software 0.3.1: **104 pruebas superadas**, incluidas señales a 48 kHz, persistencia y exportación, referencias temporales, rutas ausentes, cambios de módulo y cancelación de arranques pendientes. Las pruebas de diferenciales verifican cambio de referencia, signos y cancelación de un offset común de +100 ms. APK compilado y firma comprobada; lint sin errores. Esto no verifica la precisión acústica ni las combinaciones de hardware reales.

## Módulo reutilizable · SONDA Generador Kit 0.2.0

[Guardar el kit completo](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/tags/generator-kit-v0.2.0/artifacts/SONDA-Generador-0.2.0-kit.zip) · [Guía de integración y evolución](docs/14_MODULO_REUTILIZABLE.md) · [Código de la versión conservada](https://github.com/papaluis-b/Codex-gptpublico/tree/generator-kit-v0.2.0).

La versión aprobada queda guardada como biblioteca Android AAR y núcleo DSP JVM JAR, con fuentes, API, repositorio Maven local, dos proyectos de ejemplo, documentación y hashes. El kit incluye el APK original y su ZIP de fuentes en `baseline/`, sin sustituirlos por una recompilación.

La etiqueta [`generador-app-v0.2.0`](https://github.com/papaluis-b/Codex-gptpublico/tree/generador-app-v0.2.0) fija la app aprobada en `01eb7fab88218c9361dd3a9b4e305ffe086d2ce5`; [`generator-kit-v0.2.0`](https://github.com/papaluis-b/Codex-gptpublico/tree/generator-kit-v0.2.0) fija su extracción para reutilizarla. El repositorio es la fuente común; el ZIP permite guardar y llevar el módulo a otro proyecto.

| Pieza | Uso |
| --- | --- |
| `:generator-kit` / `es.sonda:generator-kit:0.2.0` | Motor Android y pantalla `GeneratorScreen(engine)`, integrables en otra app. |
| `:dsp` / `es.sonda:dsp:0.2.0` | Generación y DSP en JVM, sin depender de Android. |
| `:generator` | App independiente que usa la misma biblioteca como anfitrión. |
| `examples/generator-host` y `examples/dsp-jvm` | Consumidores externos de los binarios publicados. |

Para generar el kit con las herramientas instaladas: `bash scripts/build-generator-kit.sh`. La guía explica cómo consumir los módulos por fuentes o Maven, gestionar su ciclo de vida, fijar versiones y conservar una base limpia para evolucionar. [Dependencias](DEPENDENCIES.md) · [Estado de licencias](LICENSE_STATUS.md).

## SONDA Generador 0.2.0 · control continuo de frecuencia

[Descargar instalador ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-generador-v0.2.0/artifacts/SONDA-Generador-0.2.0-instalar.zip) · [Descargar APK](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-generador-v0.2.0/artifacts/SONDA-Generador-0.2.0-debug.apk) · [Código fuente ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-generador-v0.2.0/artifacts/SONDA-Generador-0.2.0-codigo.zip).

App dedicada a generar seno y ruido rosa en campanas. Añade un **deslizador de frecuencia continuo y logarítmico**: cambia los Hz mientras arrastras, con lectura numérica en directo. Conserva el ajuste exacto y los botones por pasos de **1/3, 1/24, 1/32 y 1/48 de octava**. Tiene su propio icono y el identificador `es.sonda.generador`, por lo que puede instalarse junto a la app general. Selección de salida USB y modo de prueba por altavoz; funciona sin micrófono, cuenta ni Internet. La salida se prepara en silencio y requiere pulsar Emitir. Al salir de la app se detiene.

Para instalar desde el teléfono, copia el enlace en Chrome, abre el ZIP en Mis archivos, pulsa Extraer y toca `SONDA-Generador-0.2.0-debug.apk`. Se instala como actualización de SONDA Generador 0.1.0. [Guía y validación del generador independiente](docs/13_GENERADOR_INDEPENDIENTE.md).

El módulo Android `:generator` y la aplicación general `:app` comparten el núcleo `:dsp`; cada aplicación genera su propio APK. Para compilar solo el generador:

```bash
./gradlew :dsp:test :generator:testDebugUnitTest :generator:lintDebug :generator:assembleDebug
```

## Aplicación 0.2.0 · fracciones de octava y ruido rosa

[Descargar ZIP de instalación](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-v0.2.0/artifacts/SONDA-0.2.0-instalar.zip) · [Descargar APK para Android](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-v0.2.0/artifacts/SONDA-0.2.0-debug.apk) · [Descargar código fuente ZIP](https://github.com/papaluis-b/Codex-gptpublico/raw/refs/heads/sonda-v0.2.0/artifacts/SONDA-0.2.0-codigo.zip).

Si abres el enlace desde ChatGPT y falla la descarga, copia el enlace y pégalo en Chrome. Para el ZIP de instalación: abre Mis archivos → Descargas → SONDA-0.2.0-instalar.zip → Extraer; después toca SONDA-0.2.0-debug.apk. Esta entrega conserva la firma de 0.1.0 para poder actualizarla sin desinstalar.

Proyecto Android Kotlin/Compose con diagnóstico de rutas solicitadas/activas, micrófono integrado, salida USB independiente, seno de fase continua con rampa, analizador PSD/espectrograma, grabación WAV float32 y exportación local ZIP. En Generar puedes seleccionar pasos de **1/3, 1/24, 1/32 y 1/48 de octava**. Al elegir **Ruido rosa**, la fracción también determina la anchura de su campana, centrada en la frecuencia seleccionada; se muestran los bordes del filtro a −3 dB. Incluye simulación identificada y núcleo DSP independiente para pruebas JVM. No requiere cuenta ni Internet en el teléfono.

```bash
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Requiere JDK 21 y Android SDK 36. APK: `app/build/outputs/apk/debug/app-debug.apk`.

[Compilación, instalación y prueba manual](docs/10_COMPILAR_Y_PROBAR.md) · [Decisiones](docs/09_DECISIONES.md) · [Generador 0.2: funcionamiento y pruebas](docs/12_GENERADOR_FRACCIONES_Y_ROSA.md) · [Evidencia de 0.1](docs/11_ESTADO_H0_H1.md) · [Dependencias y licencias](DEPENDENCIES.md).

Las rutas reales, la captura/reproducción simultáneas y el procesado del S24 Ultra necesitan validación en el teléfono y DAC. Delay, respuesta mediante barrido y compensación quedan para los hitos siguientes.

## Propósito

Crear una herramienta personal de medición y diagnóstico de sistemas de sonido para un técnico de eventos y artes escénicas. Equipo objetivo inicial: Samsung Galaxy S24 Ultra. Prioridades: simplicidad, eficiencia, funcionamiento sin internet y resultados verificables.

Configuración acordada: salida por DAC/interfaz USB-C hacia mesa, procesador o PA; captura mediante el micrófono interno del teléfono. El modelo de DAC y la versión Android/One UI todavía no están determinados. No bloquear el desarrollo de software por esos datos; registrarlos al probar el dispositivo. El micrófono externo y la transferencia de dos entradas quedan previstos para fases posteriores.

## Especificaciones del producto

`PROMPT_CODEX.md`, `AGENTS.md` y `docs/01` a `docs/08` definen la especificación inicial. Leerlos antes de continuar implementando. Trabajar por hitos y conservar la separación entre pruebas sintéticas y validaciones físicas. El ejemplo de medida sigue siendo ficticio; no contiene resultados reales del Samsung.

## Documentos

| Archivo | Contenido |
| --- | --- |
| PROMPT_CODEX.md | Instrucción lista para iniciar el desarrollo |
| AGENTS.md | Reglas de trabajo para el repositorio |
| docs/01_PRODUCTO.md | Alcance, casos de uso y requisitos |
| docs/02_AUDIO_ANDROID.md | Rutas, audio nativo y arquitectura |
| docs/03_METODOS_DSP.md | RTA, generador, delay, barridos y decaimiento |
| docs/04_INTERFAZ.md | Pantallas e interacción móvil |
| docs/05_DATOS.md | Persistencia, exportación y reproducibilidad |
| docs/06_VALIDACION.md | Pruebas sintéticas, pruebas físicas y aceptación |
| docs/07_PLAN.md | Hitos, riesgos y decisiones pendientes |
| docs/08_INVESTIGACION.md | Referencias y conclusiones de la investigación |
| examples/measurement.example.json | Ejemplo ficticio de registro de medida |

## Decisiones ya tomadas

- Android nativo; S24 Ultra como referencia física inicial.
- Uso individual, local y sin cuentas.
- Entrada inicial: micrófono del Samsung. Salida prevista: DAC USB-C a la PA.
- Generador controlable, RTA/espectrograma, medición de delay por señal conocida y respuesta mediante barrido.
- Herramienta de diagnóstico y ajuste manual; no modifica automáticamente la EQ de la PA.
- Guardar las señales originales y los metadatos necesarios para revisar cada medida.
- Diseño extensible para audio externo, sin exigirlo al usuario en la primera versión.

## Qué significa «medir» aquí

Con el micrófono interno sin caracterizar, la respuesta es relativa e incluye su coloración y cualquier procesado residual. El retardo inicialmente observado incluye la cadena de medida. Solo se denominará compensado cuando exista un procedimiento de compensación validado para esa ruta y configuración.

No se promete equivalencia con Smaart, un sonómetro certificado o una interfaz de medición calibrada. Se busca una herramienta útil cuya calidad se pueda demostrar e ir mejorando.
