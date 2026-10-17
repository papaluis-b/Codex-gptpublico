# SONDA Android

Versión de especificación: 1.0 · 15 de octubre de 2026.
Nombre provisional, sin decisión de marca. Idioma del producto: español.

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
