package es.sonda.example;

import es.sonda.dsp.GeneratorSettings;
import es.sonda.dsp.SignalGenerator;
import es.sonda.dsp.SignalKind;
import java.util.Locale;

/** A plain JVM host: published DSP buffers without Android or physical audio output. */
public final class DspExample {
    public static void main(String[] args) {
        SignalGenerator generator = new SignalGenerator(48000);
        float[] warmup = new float[48000];
        float[] second = new float[48000];
        generator.render(warmup, warmup.length, 1);
        for (float sample : warmup) {
            if (sample != 0f) throw new AssertionError("Creation must remain silent");
        }
        generator.setEnabled(true);
        generator.render(warmup, warmup.length, 1);
        generator.render(second, second.length, 1);
        double sineRms = rmsDb(second);
        if (Math.abs(sineRms + 43.0103) > 0.01) throw new AssertionError("Unexpected sine RMS: " + sineRms);

        generator.configure(new GeneratorSettings(SignalKind.PINK_NOISE, 1000.0, -40.0, 3), false);
        generator.render(warmup, warmup.length, 1);
        generator.render(second, second.length, 1);
        double pinkRms = rmsDb(second);
        if (Math.abs(pinkRms + 40.0) > 2.0) throw new AssertionError("Unexpected pink RMS: " + pinkRms);
        if (generator.getClippedSamples() != 0L) throw new AssertionError("Generator clipped");
        System.out.printf(Locale.ROOT, "DSP_JVM_OK sine=%.4f dBFS RMS pink=%.4f dBFS RMS samples=%d%n",
                sineRms, pinkRms, second.length);
    }

    private static double rmsDb(float[] samples) {
        double sum = 0.0;
        for (float sample : samples) {
            if (!Float.isFinite(sample) || Math.abs(sample) > 1f) throw new AssertionError("Invalid sample");
            sum += (double) sample * sample;
        }
        return 10.0 * Math.log10(sum / samples.length);
    }
}
