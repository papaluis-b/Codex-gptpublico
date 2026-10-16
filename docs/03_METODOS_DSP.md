# Métodos de medición y DSP

Este documento fija criterios técnicos. Los valores de configuración son valores iniciales propuestos, no capacidades verificadas del Samsung.

## 1. Magnitudes y normalización

Audio interno flotante normalizado. Definir dBFS de amplitud mediante 20 log10 de la amplitud respecto al pico de escala completa. Con referencia RMS igual a 1, seno de pico 1 tiene RMS −3,0103 dBFS. Si se elige otra convención, convertir y etiquetar explícitamente; no mezclar convenciones.

Separar: amplitud de tono en dBFS, densidad espectral de potencia en dBFS/Hz, energía integrada de banda y respuesta relativa en dB. Un eje dB no debe cambiar de significado al cambiar FFT/bandado sin indicación.

Clipping digital observable no excluye saturación analógica anterior. Procesamiento en double para cálculos offline delicados si es necesario; medir coste antes de optimizar.

## 2. Analizador en directo

Ventana Hann inicial, tamaños 4096/8192/16384/32768/65536, configurables después de medir rendimiento. Solapamiento y frecuencia de cálculo separados de tasa de refresco visual. Empezar con 8192 para observación general y un preset de graves con ventana mayor.

En 48 kHz: N=8192 → separación de bins 5,8594 Hz y ventana 170,67 ms; N=65536 → 0,7324 Hz y 1,3653 s. Son separación de bins y duración, no garantía de distinguir resonancias a esas distancias: la ventana y relación señal/ruido también importan. Mostrar ambos valores. Zero padding solo interpola.

Corregir ganancia coherente para amplitud de tonos y ancho de banda equivalente de ruido para PSD. Manejar correctamente espectro unilateral y bins DC/Nyquist. Validar con Parseval y señales conocidas.

Promediar potencias lineales antes de pasar a dB. Definir tiempos de integración reales e independientes del refresco. Peak hold debe guardar máximo y permitir reinicio. No usar peak hold como curva de respuesta.

Bandas 1/3, 1/6, 1/12 y 1/24 de octava: integrar energía con límites definidos y ponderación de bins de borde. No presentarlas como filtros normalizados IEC si no se implementan y validan como tales. Separar el bandado del suavizado de una curva de respuesta. Si no hay resolución suficiente en graves, mostrarlo en vez de crear detalle artificial.

Espectrograma basado en STFT: escala logarítmica de frecuencia y rango de color fijo/configurable. Identificar el instante de cada ventana; no asignar resolución temporal falsa. RTA rosa por bandas y espectro rosa por Hz no tienen la misma pendiente: explicar brevemente en ayuda contextual.

## 3. Generador

Seno con acumulador de fase, continuidad al cambiar frecuencia y rampas para cambios de nivel y arranque/parada. Las rampas y envolventes forman parte de la señal exacta guardada para medir. Evitar cambios bruscos de dispositivo. Las transiciones de frecuencia de barrido manual no deben convertirse en medidas automáticas de respuesta.

Control de nivel en dBFS separado del volumen Android. Propuesta inicial: amplitud pico −40 dBFS al primer uso y límite suave configurable de −12 dBFS, sin garantía de nivel acústico seguro: la ganancia de la PA es desconocida. Registrar ambos controles cuando se pueda. No restaurar estado «emitiendo» al abrir.

Ruido rosa: algoritmo documentado y validado sobre potencia por octava. Ruido limitado en banda opcional; no etiquetar como rosa una aproximación sin rango de validez conocido. Guardar excitación real/semilla y versión del algoritmo para medidas que la usen.

Seno manual: rango nominal propuesto 20 Hz–20 kHz restringido por sample rate real y ruta. Poder introducir frecuencia exacta; ajuste logarítmico y pasos finos. No sugerir que el micrófono tiene respuesta útil en todo ese rango.

Barrido local configurable alrededor de una frecuencia seleccionada. Secuencia excitación/silencio con duración ajustable. Mute prioritario; un fallo debe poner el stream en silencio aunque la UI esté ocupada.

## 4. Delay mediante señal conocida

### Método

Registrar pretrigger, emitir estímulo corto conocido y capturar la cola. Puede ser pulso de banda limitada, chirp corto o secuencia codificada. Comparar su detectabilidad y ancho de banda antes de elegir el predeterminado. Un seno continuo aislado es ambiguo en múltiplos de período y no debe ser la señal de delay general.

Estimar candidatos mediante correlación/matched filter sobre un intervalo de búsqueda explícito. Correlación normalizada con energía mínima; gestionar inversión de polaridad. Usar la señal exacta emitida. Ponderar/limitar banda si mejora detección, conservando metadatos. La coloración acústica puede alterar el pico; no garantizar que coincide con el inicio físico.

Mostrar candidato principal y alternativas relevantes, curva, ruido previo, relación entre candidatos y dispersión entre repeticiones. Permitir selección manual. Rechazar o marcar dudoso ante falta de señal, clipping, discontinuidad o ambigüedad. Una medida de confianza es heurística hasta validarse; no mostrar porcentajes de exactitud inventados.

Repetir al menos cinco veces para caracterizar estabilidad. Usar mediana y dispersión robusta, conservar medidas individuales. Espaciar pulsos para no confundir colas con nuevas emisiones. Identificar cada evento o variar secuencia cuando haga falta.

### Qué retardo se muestra

Retardo observado = latencia residual de salida/captura + retardo de la cadena de sonido + propagación acústica.

Mantener campos separados para observado, compensación aplicada y compensado. Sin compensación validada, el último es null. No restar una latencia genérica de Android ni una cifra copiada de otro teléfono.

Timestamps de plataforma pueden mejorar la estimación pero deben contrastarse; no aseguran por sí mismos emisión física exacta con cualquier DAC.

### Calibración propuesta y límites

Una prueba acústica a distancia conocida estima un offset combinado que también incluye latencias del altavoz/procesador usado. Guardarlo como referencia de esa cadena, no como calibración pura del DAC. Para caracterizar diferencias conocidas se puede introducir un delay digital en la misma cadena sin mover nada; no valida el valor absoluto inicial.

Una compensación completa de instrumento requiere un montaje de referencia apropiado o medición independiente. En fase futura, referencia eléctrica capturada y micrófono en la misma interfaz ofrecen una base más controlada.

Invalidar perfil tras cambiar ruta, sample rate, buffers relevantes, sistema o parámetros que afecten al tiempo. Verificarlo al principio de sesión; no prometer estabilidad entre reinicios sin medirla.

Distancia equivalente opcional d=c·t. Valor predeterminado aproximado c=343 m/s para 20 °C y campo editable; etiquetar metros equivalentes. No usar sensores inexistentes ni atribuir la latencia DSP a distancia real.

## 5. Barrido de medición

Propuesta: barrido senoidal logarítmico de 5 s, 40 Hz–16 kHz, con duraciones 2/5/10 s y rango editable. Estos valores simplifican la prueba inicial; no describen la respuesta válida del micrófono. Añadir pregrabación y cola inicial de 2 s, ajustable a la sala. Incluir fades y guardar excitación exacta.

Separar «barrido para escuchar» de «barrido de medición». Durante la medida bloquear cambios de frecuencia/nivel y registrar cualquier cambio de volumen o ruta detectable.

Pipeline: captura previa → excitación → cola → controles de integridad → alineación conservando origen → tratamiento de deriva si validado → deconvolución → impulso completo → ventana de análisis → FFT → respuesta relativa.

Implementar inversión de barrido logarítmico con compensación correcta de amplitud o inversión espectral regularizada documentada. No dividir sin protección por X(f) cerca de cero. En inversión espectral, H(f)=Y(f)·conj(X(f))/(|X(f)|²+λ(f)), con padding suficiente para evitar wrap-around y selección de λ validada. Rango fuera de excitación se enmascara. La elección concreta depende de pruebas sintéticas y estabilidad, no de apariencia gráfica.

Guardar el impulso completo con su desplazamiento temporal. Puede haber una vista centrada en la llegada, pero conservar origen y transformación. Longitud de captura, cola y FFT deben admitir el retardo esperado. No desplazar el pico a cero antes de calcular delay.

La respuesta incluye micrófono, DAC y procesado. No confundir con respuesta anecoica. Comparación A/B con normalización opcional debe conservar curvas originales e indicar el offset retirado. Sin referencia temporal validada, no mostrar fase absoluta o group delay como resultado profesional validado.

Relojes distintos pueden deformar impulso y respuesta. Tratar como riesgo y medir deriva. Para fase futura, diseñar referencia/compensación comprobada; no aplicar una corrección de deriva que suprima parte del retardo real.

## 6. Resonancias y decaimiento

Primera aproximación: espectrograma, pulsos y comparación espacial. Posteriormente, calcular bandas/curvas de decaimiento sobre IR o excitación interrumpida. Documentar la respuesta temporal de los filtros: un filtro estrecho o una ventana larga también genera persistencia aparente.

Solo estimar T20/T30 u otras pendientes si hay rango dinámico, tramo y ruido adecuados. No llamar RT60 a cualquier cola medida ni extrapolar sin indicar método y calidad. En bajas frecuencias el comportamiento modal puede hacer poco representativa una cifra global de reverberación.

No aplicar fórmulas simples de Q a una curva suavizada sin validar supuestos. No generar filtros correctores automáticos. Una clasificación de modos, reflexiones, vibraciones o feedback requiere evidencias adicionales y queda fuera del MVP.
