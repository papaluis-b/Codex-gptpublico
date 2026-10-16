# SONDA Android — documentación de desarrollo para Codex

Versión de especificación: 1.0 · 15 de octubre de 2026.
Nombre provisional, sin decisión de marca. Idioma del producto: español.

## Propósito

Crear una herramienta personal de medición y diagnóstico de sistemas de sonido para un técnico de eventos y artes escénicas. Equipo objetivo inicial: Samsung Galaxy S24 Ultra. Prioridades: simplicidad, eficiencia, funcionamiento sin internet y resultados verificables.

Configuración acordada: salida por DAC/interfaz USB-C hacia mesa, procesador o PA; captura mediante el micrófono interno del teléfono. El modelo de DAC y la versión Android/One UI todavía no están determinados. No bloquear el desarrollo de software por esos datos; registrarlos al probar el dispositivo. El micrófono externo y la transferencia de dos entradas quedan previstos para fases posteriores.

## Cómo usar este paquete

1. Descomprimir en el repositorio de trabajo o adjuntar el ZIP a Codex y pedirle que lo extraiga.
2. Entregar el contenido de `PROMPT_CODEX.md` como instrucción inicial.
3. Codex debe leer `AGENTS.md` y todos los documentos de `docs/` antes de implementar.
4. El primer resultado será una aplicación instalable de diagnóstico de rutas y audio. Después, desarrollar las funciones por hitos.
5. Las validaciones en teléfono se distinguen de las pruebas automáticas. Una prueba pendiente no equivale a una función validada.

Este paquete contiene especificaciones y ejemplos de datos. No contiene una app compilada, código de medición probado ni resultados reales del Samsung.

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
