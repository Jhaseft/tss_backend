package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.DescriptiveStatsDTO;
import com.jhaseft.tss.service.stats.DescriptiveStatsService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica la estadística descriptiva contra los valores de referencia del Informe de Calidad
 * para "años de vida profesional" (pregunta 453, criterio B, n = 180). Los 180 valores son los
 * que produce el NumericAnswerParser sobre los crudos de la BD.
 */
class DescriptiveStatsServiceTest {

    private final DescriptiveStatsService svc = new DescriptiveStatsService();

    // 180 valores depurados (criterio B) de la pregunta 453.
    private static final double[] V = {
            0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.5,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6,
            6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 9, 9, 10, 10, 10, 10, 11, 11, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 16, 16, 16, 17, 17, 17, 18, 19, 20, 20,
            20, 20, 20, 20, 20, 20, 20, 20, 21, 21, 22, 22, 22, 23, 23, 23, 25, 25, 25, 25, 26, 28,
            28, 35};

    private DescriptiveStatsDTO ref() {
        List<Double> list = Arrays.stream(V).boxed().toList();
        return svc.compute(list);
    }

    @Test
    void tendenciaCentralYDispersion() {
        DescriptiveStatsDTO d = ref();
        assertEquals(180, d.getN());
        assertEquals(9.031, d.getMean(), 0.01);
        assertEquals(6.0, d.getMedian(), 1e-9);
        assertEquals(7.651, d.getStdDev(), 0.01);
        assertEquals(58.53, d.getVariance(), 0.05);
        assertEquals(84.7, d.getCv(), 0.2);
        assertEquals(0.57, d.getStandardError(), 0.01);
        assertEquals(List.of(1.0), d.getModes());
        assertEquals(25, d.getModeFrequency());
        assertEquals(0.0, d.getMin(), 1e-9);
        assertEquals(35.0, d.getMax(), 1e-9);
    }

    @Test
    void intervaloDeConfianza() {
        DescriptiveStatsDTO d = ref();
        assertEquals(1.973, d.getTValue(), 0.005);
        assertEquals(7.91, d.getCiLow(), 0.02);
        assertEquals(10.16, d.getCiHigh(), 0.02);
    }

    @Test
    void cuartilesYPercentiles() {
        DescriptiveStatsDTO d = ref();
        assertEquals(2.0, d.getQ1(), 1e-9);
        assertEquals(14.0, d.getQ3(), 1e-9);
        assertEquals(12.0, d.getIqr(), 1e-9);
        assertEquals(1.0, d.getP5(), 1e-9);
        assertEquals(1.0, d.getP10(), 1e-9);
        assertEquals(20.0, d.getP90(), 1e-9);
        assertEquals(23.0, d.getP95(), 1e-9);
        assertEquals(28.0, d.getP99(), 1e-9);
    }

    @Test
    void vallasDeTukeyYAtipicos() {
        DescriptiveStatsDTO d = ref();
        assertEquals(32.0, d.getUpperFence(), 1e-9);     // Q3 + 1,5·RIC
        assertEquals(50.0, d.getUpperExtremeFence(), 1e-9); // Q3 + 3·RIC
        assertEquals(List.of(35.0), d.getModerateOutliers());
        assertTrue(d.getExtremeOutliers().isEmpty());
        assertEquals(28.0, d.getUpperWhisker(), 1e-9);
    }

    @Test
    void forma() {
        DescriptiveStatsDTO d = ref();
        assertEquals(0.81, d.getSkewnessCoefficient(), 0.01);
        assertEquals(1.19, d.getSkewnessPearson(), 0.01);
        assertEquals(-0.19, d.getKurtosis(), 0.01);
    }

    @Test
    void shapiroWilk() {
        DescriptiveStatsDTO d = ref();
        assertEquals(0.893, d.getShapiroW(), 0.002);
        assertTrue(d.getShapiroP() < 0.001, "p debe ser < 0,001");
    }
}
