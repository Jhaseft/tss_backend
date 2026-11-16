package com.jhaseft.tss.service.stats;

/**
 * Intervalo de confianza de Wilson para una proporción (Informe de Calidad, punto 8). Más fiable que
 * el de Wald con n pequeño o proporciones extremas. Devuelve los límites en porcentaje.
 *
 * Referencias verificadas: mujeres 33/148 → 22,3 % [16,3; 29,7]; prevé contratar 68/80 → 85,0 %
 * [75,6; 91,2]. Por ser muestreo no probabilístico, el intervalo es referencial.
 */
public final class WilsonInterval {

    private static final double Z95 = 1.959963984540054;   // z para 95 %

    private WilsonInterval() {}

    public record Result(double pct, double low, double high) {}

    public static Result of(long count, long n) {
        return of(count, n, Z95);
    }

    public static Result of(long count, long n, double z) {
        if (n <= 0) return new Result(0, 0, 0);
        double p = (double) count / n;
        double z2 = z * z;
        double denom = 1 + z2 / n;
        double center = (p + z2 / (2 * n)) / denom;
        double margin = (z / denom) * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        return new Result(p * 100, Math.max(0, center - margin) * 100, Math.min(1, center + margin) * 100);
    }
}
