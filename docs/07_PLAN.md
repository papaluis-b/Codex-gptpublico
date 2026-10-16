# Plan de implementación

No fijar calendario ni coste sin inspeccionar entorno, equipo y disponibilidad de pruebas físicas. Un prototipo visible puede llegar antes que un instrumento validado.

## H0 — Base compilable

Inspeccionar repositorio/SDK/NDK existentes. Elegir versiones estables y documentarlas. Crear proyecto Android, núcleo DSP independiente, diagnóstico básico, tests y modo sintético marcado. Entrega: compilación verificable o bloqueo exacto, instrucciones de instalación y decisiones registradas.

## H1 — Audio y rutas

Micrófono interno y salida DAC simultáneos; seno, mute, captura WAV y diagnóstico de streams. Manejar desconexiones y ciclo de vida. Entrega: APK debug cuando sea posible y protocolo de prueba en Samsung. Punto de control físico: ¿funciona esa combinación real de rutas? Sin DAC disponible, continuar con modo de prueba interno y registrar USB como no ejecutado.

## H2 — Analizador y generador útil

RTA normalizado, promediado, máximos, espectrograma, controles táctiles, ruido rosa, barrido manual/local y capturas A/B. Validar niveles y respuesta temporal del análisis. Punto de control: estabilidad y repetibilidad del micrófono.

## H3 — Delay

Señal corta conocida, correlación, candidatos, repeticiones y curva temporal. Conservar referencia de frames/timestamps. Mostrar observado por defecto. Compensación solo cuando se valide; no bloquear el módulo por no disponer aún de calibración absoluta. Entrega: tests sintéticos y protocolo de incrementos conocidos.

## H4 — Barrido de medición

Captura sincronizada en software, deconvolución validada, impulso, respuesta relativa y exportación reproducible. Tratar deriva de relojes y máscaras de banda válida. Guardar tiempo original aunque se use una vista centrada. Punto de control: repetibilidad de barridos reales y límites de banda.

## H5 — Resonancias y refinamiento

Excitación/silencio, análisis de decaimiento documentado, marcadores y simplificación según uso real. Waterfall solo si mejora lectura móvil y el método está validado. No introducir métricas avanzadas para llenar pantallas.

## Posterior — Hardware externo

Micrófono calibrado, interfaz de dos entradas y referencia eléctrica. Entonces transferencia de magnitud/fase/coherencia y mejores mediciones de alineamiento. Evaluar cada dispositivo; no asumir compatibilidad universal.

## Riesgos y respuesta

| Riesgo | Respuesta prevista |
| --- | --- |
| Android no mantiene entrada interna/salida USB | Detectar, documentar y evaluar alternativa específica; no sustituir ruta sin aviso |
| Micrófono procesado o no lineal | Caracterizar, marcar modo orientativo y limitar interpretación |
| Latencia variable | Separar observado/compensado, repetir e invalidar perfiles |
| Relojes distintos | Registrar y evaluar deriva; limitar métricas que dependan de sincronía |
| Sobrecalentamiento/carga gráfica | Reducir refresco visual y acotar buffers sin ocultar discontinuidades |
| Reflejos dominan correlación | Candidatos, banda de análisi