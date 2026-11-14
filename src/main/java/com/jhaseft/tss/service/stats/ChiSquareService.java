package com.jhaseft.tss.service.stats;

import com.jhaseft.tss.dto.ChiSquareResultDTO;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.HypergeometricDistribution;
import org.springframework.stereotype.Service;

/**
 * Prueba χ² de independencia con verificación de supuestos (Informe de Calidad, punto 7):
 * tabla de esperadas, regla de Cochran (% de celdas con E &lt; 5 y si alguna &lt; 1), prueba exacta
 * de Fisher bilateral en tablas 2×2, V de Cramér con interpretación de Cohen según k − 1.
 *
 * Sin corrección de Yates (así lo hace el estudio). χ², p y V salen de la MISMA tabla.
 * Verificado contra los 12 cruces de titulados (criterio B); ver chi_referencia_184.md.
 */
@Service
public class ChiSquareService {

    public ChiSquareResultDTO test(long[][] obs) {
        int r = obs.length, c = obs[0].length;
        long[] rowTot = new long[r], colTot = new long[c];
        long n = 0;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++) {
                rowTot[i] += obs[i][j];
                colTot[j] += obs[i][j];
                n += obs[i][j];
            }

        double[][] exp = new double[r][c];
        double chi = 0;
        int low = 0;
        boolean below1 = false;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++) {
                double e = n > 0 ? (double) rowTot[i] * colTot[j] / n : 0;
                exp[i][j] = e;
                if (e < 5) low++;
                if (e < 1) below1 = true;
                if (e > 0) chi += (obs[i][j] - e) * (obs[i][j] - e) / e;
            }

        int df = Math.max(0, (r - 1) * (c - 1));
        double p = (df > 0 && chi > 0)
                ? 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(chi) : 1.0;

        int k = Math.min(r, c);
        double v = (n > 0 && k > 1) ? Math.sqrt(chi / ((double) n * (k - 1))) : 0;

        ChiSquareResultDTO res = new ChiSquareResultDTO();
        res.setN(n);
        res.setExpected(exp);
        res.setChiSquare(chi);
        res.setDegreesOfFreedom(df);
        res.setPValue(p);
        res.setCramersV(v);
        res.setCramersK(k);
        res.setCramersVLevel(cohenLevel(v, k - 1));
        res.setLowExpectedCells(low);
        res.setTotalCells(r * c);
        res.setLowExpectedPct(low * 100.0 / (r * c));
        res.setAnyExpectedBelow1(below1);
        res.setCochranOk(low * 100.0 / (r * c) <= 20.0 && !below1);
        if (r == 2 && c == 2) res.setFisherP(fisherExact2x2(obs));
        return res;
    }

    /**
     * Prueba exacta de Fisher bilateral para una tabla 2×2, por el método de las colas iguales
     * (suma de las probabilidades hipergeométricas ≤ la de la tabla observada, margenes fijos).
     */
    public static double fisherExact2x2(long[][] t) {
        int a = (int) t[0][0], b = (int) t[0][1], c = (int) t[1][0], d = (int) t[1][1];
        int row1 = a + b, col1 = a + c, n = a + b + c + d;
        HypergeometricDistribution hg = new HypergeometricDistribution(n, col1, row1);
        double pObs = hg.probability(a);
        int lo = Math.max(0, col1 - (n - row1));   // soporte de a
        int hi = Math.min(col1, row1);
        double sum = 0;
        for (int x = lo; x <= hi; x++) {
            double px = hg.probability(x);
            if (px <= pObs * (1 + 1e-7)) sum += px;
        }
        return Math.min(1.0, sum);
    }

    /** Interpretación de Cohen de la V de Cramér según los grados menores (k − 1). */
    private static String cohenLevel(double v, int dfMin) {
        double small, medium, large;
        switch (Math.min(Math.max(dfMin, 1), 3)) {
            case 1 -> { small = 0.10; medium = 0.30; large = 0.50; }
            case 2 -> { small = 0.07; medium = 0.21; large = 0.35; }
            default -> { small = 0.06; medium = 0.17; large = 0.29; }
        }
        if (v < small) return "Insignificante";
        if (v < medium) return "Pequeño";
        if (v < large) return "Mediano";
        return "Grande";
    }
}
