# Interfaz móvil

## Principios

Diseñar para teléfono, no reducir una pantalla de escritorio. Lectura rápida, controles grandes y unidades visibles. Tema oscuro de alto contraste, colores accesibles y estado distinguible sin depender del color. Orientación vertical completa; horizontal amplía gráficos. La prueba en S24 Ultra incluye escala de fuente aumentada.

Barra persistente: entrada activa, salida activa, estado de captura y estado de emisión. Mute siempre accesible cuando hay salida activa. Configuración técnica detallada en un panel de diagnóstico, no interrumpiendo cada medición.

## Navegación: tres áreas

### Analizar

Selector: Directo / Delay / Barrido.

Directo: RTA y espectrograma, cursor de frecuencia/nivel, congelar, guardar y superponer captura. Ajustes desplegables de FFT, banda, promediado y escala. Mostrar ventana temporal y separación de bins cerca del ajuste FFT.

Delay: botón Medir, estado de progreso, resultado en ms con etiqueta Observado/Compensado, gráfica temporal ampliable y selector de candidatos. Detalle con repeticiones y dispersión. Una lectura inválida dice «Sin llegada fiable» y ofrece revisar el gráfico/repetir; no mostrar cero como sustituto.

Barrido: rango, duración y nivel; Medir y Cancelar; resultado relativo, impulso y comparación. La gráfica no aparece como válida hasta completar controles de calidad. Estado «procesado del micrófono no verificado» accesible y persistente en metadatos.

### Generar

Tipo de señal, frecuencia grande, mando de ajuste fino, nivel en dBFS y salida/canal. Inicio y silencio separados de selección. Seno, ruido rosa y barrido. Controles avanzados solo al desplegar.

Pulsación en gráfico selecciona frecuencia, sin emisión. La opción «seguir cursor» solo modifica un generador ya activado por el usuario y tiene indicador claro. Zoom y arrastre del gráfico no cambian accidentalmente frecuencia activa: separar gestos o bloquear modo de control.

### Capturas

Sesiones por evento/recinto con fecha, posición, tipo, nota y estado de calidad. Vista A/B con colores, unidades y condiciones. Exportación explícita. No comparar automáticamente datos de tipos o normalizaciones incompatibles.

## Primer inicio

Permiso de micrófono cuando se necesita. Elegir salida USB si hay una disponible y comprobar entrada. Sin DAC, indicar «Salida USB no conectada»; permitir analizar y ofrecer prueba por altavoz interno mediante acción explícita. No impedir usar RTA por no tener salida.

## Textos de estado

- «Micrófono interno · ruta confirmada» o «Entrada sin verificar».
- «Retardo observado: incluye la cadena de medida».
- «Compensación no validada para esta configuración».
- «Respuesta relativa · micrófono sin calibrar».
- «Simulación: datos sintéticos», siempre visible si aplica.
- «Ruta cambiada: emisión detenida».

No mostrar todas las explicaciones como ventanas emergentes. El dato, estado y acción relevante deben ser suficientes; el detalle técnico está a un toque.
