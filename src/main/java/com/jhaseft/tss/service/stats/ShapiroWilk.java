package com.jhaseft.tss.service.stats;

import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.Arrays;

/**
 * Prueba de normalidad de Shapiro-Wilk, algoritmo de Royston (1995) AS R94,
 * válido para 3 ≤ n ≤ 5000. Devuelve el estadístico W y su p-valor.
 *
 * Se usa para contrastar la normalidad de "años de vida profesional" en el Informe de
 * Calidad (valor de referencia: W ≈ 0,893, p &lt; 0,001 para n = 180).
 */
public final class ShapiroWilk {

    private static final NormalDistribution N01 = new NormalDistribution(0, 1);

    private ShapiroWilk() {}

    /** @return {W, pValue}. */
    public static double[] test(double[] input) {
        int n = input.length;
        if (n < 3) throw new IllegalArgumentException("Shapiro-Wilk requiere n ≥ 3");

        double[] x = input.clone();
        Arrays.sort(x);

        int n2 = n / 2;
        double[] a = new double[n2 + 1];   // 1-indexado

        final double[] c1 = {0d, 0.221157, -0.147981, -2.071190, 4.434685, -2.706056};
        final double[] c2 = {0d, 0.042981, -0.293762, -1.752461, -3.582633, 5.682633};
        final double[] c3 = {0.544, -0.39978, 0.025054, -6.714e-4};
        final double[] c4 = {1.3822, -0.77857, 0.062767, -0.0020322};
        final double[] c5 = {-1.5861, -0.31082, -0.083751, 0.0038915};
        final double[] c6 = {-0.4803, -0.082676, 0.0030302};
        final double[] g  = {-2.273, 0.459};

        double an = n;

        if (n == 3) {
            a[1] = Math.sqrt(0.5);
        } else {
            double summ2 = 0;
            for (int i = 1; i <= n2; i++) {
                a[i] = N01.inverseCumulativeProbability((i - 0.375) / (an + 0.25)); // m_i (negativo)
                summ2 += a[i] * a[i];
            }
            summ2 *= 2;
            double ssumm2 = Math.sqrt(summ2);
            double rsn = 1.0 / Math.sqrt(an);
            double a1 = poly(c1, rsn) - a[1] / ssumm2;

            int i1;
            double fac;
            if (n > 5) {
                i1 = 3;
                double a2 = -a[2] / ssumm2 + poly(c2, rsn);
                fac = Math.sqrt((summ2 - 2.0 * a[1] * a[1] - 2.0 * a[2] * a[2])
                        / (1.0 - 2.0 * a1 * a1 - 2.0 * a2 * a2));
                a[1] = a1;
                a[2] = a2;
            } else {
                i1 = 2;
                fac = Math.sqrt((summ2 - 2.0 * a[1] * a[1]) / (1.0 - 2.0 * a1 * a1));
                a[1] = a1;
            }
            for (int i = i1; i <= n2; i++) a[i] = -a[i] / fac;
        }

        // W = (Σ a_i (x_{n+1-i} − x_i))² / Σ(x_i − x̄)²
        double mean = Arrays.stream(x).average().orElse(0);
        double ssq = 0;
        for (double v : x) ssq += (v - mean) * (v - mean);

        double sa = 0;
        for (int i = 1; i <= n2; i++) sa += a[i] * (x[i - 1] - x[n - i]);
        double w = sa * sa / ssq;

        double pw = pValue(w, n, an, c3, c4, c5, c6, g);
        return new double[]{w, pw};
    }

    private static double pValue(double w, int n, double an,
                                 double[] c3, double[] c4, double[] c5, double[] c6, double[] g) {
        if (n == 3) {
            double pi6 = 1.90985931710274, stqr = 1.04719755119660;
            double pw = pi6 * (Math.asin(Math.sqrt(w)) - stqr);
            return Math.max(0, Math.min(1, pw));
        }
        double y = Math.log(1 - w);
        double xx = Math.log(an);
        double m, s;
        if (n <= 11) {
            double gamma = poly(g, an);
            if (y >= gamma) return 1e-19;   // cola prácticamente nula
            y = -Math.log(gamma - y);
            m = poly(c3, an);
            s = Math.exp(poly(c4, an));
        } else {
            m = poly(c5, xx);
            s = Math.exp(poly(c6, xx));
        }
        double z = (y - m) / s;
        return 1.0 - N01.cumulativeProbability(z);   // cola superior
    }

    /** Horner: c[0] + c[1]·x + c[2]·x² + … */
    private static double poly(double[] c, double x) {
        double p = c[c.length - 1];
        for (int i = c.length - 2; i >= 0; i--) p = p * x + c[i];
        return p;
    }
}
