# Prompt inicial para Codex

Actúa como desarrollador de Android y de procesamiento digital de audio. Construye SONDA, una aplicación personal en español para analizar y ajustar sistemas de sonido desde un Samsung Galaxy S24 Ultra. El nombre es provisional.

Lee primero `AGENTS.md`, `README.md` y todos los documentos de `docs/`. Usa esos documentos como especificación. Respeta las decisiones del usuario; resuelve los detalles reversibles con criterio técnico y registra las suposiciones. No conviertas las decisiones pendientes en un cuestionario que impida avanzar.

La cadena inicial es: teléfono → DAC o interfaz USB-C → mesa/procesador/PA → micrófono interno del teléfono. Debes poder generar y grabar simultáneamente. La selección de salida USB y entrada interna debe comprobarse sobre rutas reales; solicitar una ruta no demuestra que esté activa. Si la combinación no funciona, informa de la limitación y conserva las herramientas que sí funcionan, sin cambiar silenciosamente al micrófono del accesorio.

Funciones previstas:
1. RTA/espectro en directo, promediado, retención de máximos y espectrograma.
2. Generador de seno, ruido rosa y barrido logarítmico, con frecuencia y nivel manuales, selección desde gráfico, barrido local y silencio siempre accesible.
3. Delay: emitir una señal conocida corta, capturarla, estimar su llegada mediante correlación y mostrarla sobre la grabación. Distinguir retardo observado de compensado. No confundir la orden de reproducción con el instante físico de salida.
4. Barrido de medición: generar y guardar la excitación, capturarla y deconvolucionarla para obtener respuesta al impulso y respuesta en frecuencia relativa.
5. Guardar sesiones, capturas y configuración; comparar posiciones y antes/después.

Arquitectura propuesta: Kotlin y Jetpack Compose para interfaz y control; C++ y Oboe para audio y DSP cuando resulte apropiado. Comprueba las versiones estables y compatibles antes de fijar dependencias y documenta las seleccionadas. No añadas frameworks de más. Procesamiento FFT y escritura a disco fuera del callback de audio. Gráficos limitados a una tasa razonable, independiente del análisis.

Trabaja por hitos. Empieza por H0/H1 de `docs/07_PLAN.md`: estructura compilable, modo de simulación claramente marcado, motor de audio, diagnóstico de rutas, captura/reproducción simultáneas y generador básico. A continuación implementa RTA y las mediciones. No sustituyas la prueba de audio por una maqueta visual. No exijas una prueba física para continuar el trabajo independiente de software, pero no declares validado el hardware sin probarlo.

En el primer hito entrega:
- Código completo y estructura mantenible, con documentación para compilar e instalar.
- APK debug si el entorno permite compilarla; si no, explica el bloqueo exacto y los comandos necesarios, sin afirmar que compiló.
- Pantalla de diagnóstico que distinga rutas solicitadas y activas, frecuencia real de muestreo, canales, formato, estado de timestamps, discontinuidades y clipping digital.
- Captura del micrófono y generación simultánea; si no hay DAC conectado, altavoz interno únicamente en modo de prueba explícito.
- Silencio al detener, ante error, pérdida de ruta o paso de la app a segundo plano.
- Pruebas DSP sintéticas relevantes y registro de lo comprobado y lo pendiente.

No presentes SPL absoluto sin calibración, ni una señal interna del generador como referencia eléctrica medida. No presentes coherencia de transferencia o fase absoluta como fiables sin referencia temporal y cadena validadas. No centres automáticamente en cero la respuesta al impulso destruyendo la información de llegada. No propongas corrección automática de la PA en la primera versión.

Valida especialmente: exactitud de amplitud y energía del RTA, continuidad del generador, normalización de deconvolución, preservación del tiempo, falsos positivos de delay, clipping y comportamiento al cambiar rutas. Todo resultado sintético debe estar separado de los resultados reales.

Al terminar cada hito resume lo implementado, cómo se ha comprobado, qué sigue pendiente en el teléfono y el siguiente paso concreto. Continúa con tareas de software autorizadas que no dependan de una validación física pendiente.
