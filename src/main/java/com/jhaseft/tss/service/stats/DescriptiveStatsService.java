package com.jhaseft.tss.service.stats;

import com.jhaseft.tss.dto.DescriptiveStatsDTO;
import org.apache.commons.math3.distribution.TDistribution;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula la estadística descriptiva robusta del Informe de Calidad (punto 5) a partir de una
 * lista de valores numéricos ya depurados (los produce {@code NumericAnswerParser}).
 *
 * Varianza y DE muestrales (n − 1). Percentiles por interpolación lineal (QUARTILE.INC de Excel).
 * Asimetría y curtosis en su versión muestral (SKEW/KURT de Excel). Normalidad por Shapiro-Wilk
 * (Royston). Valores de referencia verificados contra "años de vida profesional" (n = 180, criterio
 * B): media 9,031; mediana 6; DE 7,651; Q1 2; Q3 14; IC95 [7,91; 10,16]; W = 0,893.
 */
@Service
public class DescriptiveStatsService {

    public DescriptiveStatsDTO compute(List<Double> input) {
        if (input == null || input.isEmpty())
            throw new IllegalArgumentException("Se requiere al menos un valor.");

        List<Double> v = new ArrayList<>(input);
        Collections.sort(v);
        int n = v.size();

        DescriptiveStatsDTO d = new DescriptiveStatsDTO();
        d.setN(n);

        double mean = v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        d.setMean(mean);
        d.setMedian(percentile(v, 0.5));
        d.setMin(v.get(0));
        d.setMax(v.get(n - 1));
        d.setRange(v.get(n - 1) - v.get(0));

        // Modas: todas las que empatan en la frecuencia máxima (si todas son únicas, no hay moda).
        fillModes(d, v);

        if (n >= 2) {
            double ss = 0;
            for (double x : v) ss += (x - mean) * (x - mean);
            double var = ss / (n - 1);
            double sd = Math.sqrt(var);
            d.setVariance(var);
            d.setStdDev(sd);
            d.setCv(mean != 0 ? sd / mean * 100.0 : 0);
            double se = sd / Math.sqrt(n);
            d.setStandardError(se);

            double t = new TDistribution(n - 1).inverseCumulativeProbability(0.975);
            d.setTValue(t);
            d.setCiLow(mean - t * se);
            d.setCiHigh(mean + t * se);
        }

        // Cuartiles / percentiles (interpolación lineal, QUARTILE.INC).
        double q1 = percentile(v, 0.25), q3 = percentile(v, 0.75), iqr = q3 - q1;
        d.setQ1(q1);
        d.setQ3(q3);
        d.setIqr(iqr);
        d.setP5(percentile(v, 0.05));
        d.setP10(percentile(v, 0.10));
        d.setP90(percentile(v, 0.90));
        d.setP95(percentile(v, 0.95));
        d.setP99(percentile(v, 0.99));

        // Vallas de Tukey y atípicos.
        double lf = q1 - 1.5 * iqr, uf = q3 + 1.5 * iqr;
        double lef = q1 - 3.0 * iqr, uef = q3 + 3.0 * iqr;
        d.setLowerFence(lf);
        d.setUpperFence(uf);
        d.setLowerExtremeFence(lef);
        d.setUpperExtremeFence(uef);
        List<Double> moderate = new ArrayList<>();
        List<Double> extreme = new ArrayList<>();
        for (double x : v) {
            if (x < lef || x > uef) extreme.add(x);
            else if (x < lf || x > uf) moderate.add(x);
        }
        d.setModerateOutliers(moderate);
        d.setExtremeOutliers(extreme);
        d.setLowerWhisker(v.stream().filter(x -> x >= lf).findFirst().orElse(v.get(0)));
        double uw = v.get(0);
        for (double x : v) if (x <= uf) uw = x;
        d.setUpperWhisker(uw);

        // Forma.
        double sd = d.getStdDev();
        if (sd > 0) d.setSkewnessPearson(3 * (mean - d.getMedian()) / sd);
        if (n >= 3 && sd > 0) {
            double s3 = 0;
            for (double x : v) s3 += Math.pow((x - mean) / sd, 3);
            d.setSkewnessCoefficient((double) n / ((n - 1.0) * (n - 2.0)) * s3);
        }
        if (n >= 4 && sd > 0) {
            double s4 = 0;
            for (double x : v) s4 += Math.pow((x - mean) / sd, 4);
            double kurt = (double) n * (n + 1) / ((n - 1.0) * (n - 2.0) * (n - 3.0)) * s4
                    - 3.0 * (n - 1) * (n - 1) / ((n - 2.0) * (n - 3.0));
            d.setKurtosis(kurt);
        }

        // Normalidad.
        if (n >= 3) {
            double[] sw = ShapiroWilk.test(v.stream().mapToDouble(Double::doubleValue).toArray());
            d.setShapiroW(sw[0]);
            d.setShapiroP(sw[1]);
        }
        return d;
    }

    /** Percentil por interpolación lineal (tipo 7 / QUARTILE.INC). {@code v} debe venir ordenado. */
    public static double percentile(List<Double> v, double p) {
        int n = v.size();
        if (n == 1) return v.get(0);
        double h = (n - 1) * p;
        int lo = (int) Math.floor(h);
        if (lo >= n - 1) return v.get(n - 1);
        return v.get(lo) + (h - lo) * (v.get(lo + 1) - v.get(lo));
    }

    private void fillModes(DescriptiveStatsDTO d, List<Double> v) {
        Map<Double, Integer> freq = new LinkedHashMap<>();
        for (double x : v) freq.merge(x, 1, Integer::sum);
        int maxFreq = freq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Double> modes = new ArrayList<>();
        if (maxFreq > 1)
            freq.forEach((val, f) -> { if (f == maxFreq) modes.add(val); });
        Collections.sort(modes);
        d.setModes(modes);
        d.setModeFrequency(maxFreq > 1 ? maxFreq : 0);
    }
}
