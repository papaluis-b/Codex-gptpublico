# Dependencias y licencias

Dependencias de aplicación: Kotlin 2.2.21, Jetpack Compose (BOM 2025.10.01), Activity 1.11.0, Lifecycle 2.9.4, Core 1.17.0, y sus dependencias transitivas AndroidX/Kotlin coroutines: Apache-2.0. El DSP de la aplicación está implementado en el repositorio, sin FFT nativa de terceros.

SONDA (`:app`) y SONDA Generador (`:generator`) usan estas mismas versiones y comparten `:dsp`. La aplicación independiente no añade bibliotecas de audio ni depende del módulo de la aplicación general.

Herramientas: Gradle 8.13 y Android Gradle Plugin 8.13.2 (Apache-2.0); Android SDK Platform 36 y Build-Tools 36.0.0 (licencias Android SDK de cada paquete); Eclipse Temurin JDK 21.0.12.1+1 (GPL-2.0 con Classpath Exception y avisos incluidos en su distribución). El JDK/SDK no se incluyen dentro del APK.

Solo pruebas: JUnit 4.13.2 (EPL-1.0), Hamcrest (BSD-3-Clause), Robolectric 4.16.1 (MIT), AndroidX Test Core 1.7.0 y Runner 1.7.0/Ext JUnit 1.3.0 (Apache-2.0). No se incluyen en la aplicación de producción.

Los POM y archivos de licencia originales se conservan en las distribuciones/cachés de herramientas. No se ha tomado código ni activos de las aplicaciones de referencia citadas en la especificación. Las dependencias no aportan cuentas, telemetría ni red a las mediciones; el manifiesto no solicita INTERNET.

## Referencias del DSP de ruido rosa (0.2)

La transferencia matemática y los coeficientes del filtro de coloración se basan en la [nota original de Paul Kellett, marzo de 2000, conservada en MusicDSP](https://raw.githubusercontent.com/bdejong/musicdsp/master/source/files/pink.txt). Se implementa en Kotlin mediante estados y coeficientes propios; no se añade una biblioteca de ejecución. La nota se cita como referencia del algoritmo, sin atribuirle una licencia que no declara. El generador xorshift32, el diseño Butterworth por transformación bilineal y la normalización RMS están implementados y probados en este repositorio. Detalles y límites en `docs/12_GENERADOR_FRACCIONES_Y_ROSA.md`.
