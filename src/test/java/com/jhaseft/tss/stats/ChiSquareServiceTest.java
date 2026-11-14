package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.ChiSquareResultDTO;
import com.jhaseft.tss.service.stats.ChiSquareService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica χ², gl, p, V de Cramér, Cochran y Fisher contra los 12 cruces de referencia de
 * titulados (criterio B, n = 184). Tablas observadas de scripts/cross.json; valores esperados de
 * chi_referencia_184.md. Tolerancia: ±0,01 en χ²; ±0,001 en p y V.
 */
class ChiSquareServiceTest {

    private final ChiSquareService svc = new ChiSquareService();

    private ChiSquareResultDTO run(long[][] obs) { return svc.test(obs); }

    private void assertChi(long[][] obs, double chi, int df, double p, double v,
                           int lowCells, int totalCells) {
        ChiSquareResultDTO r = run(obs);
        assertEquals(chi, r.getChiSquare(), 0.01, "χ²");
        assertEquals(df, r.getDegreesOfFreedom(), "gl");
        assertEquals(p, r.getPValue(), 0.001, "p");
        assertEquals(v, r.getCramersV(), 0.001, "V");
        assertEquals(lowCells, r.getLowExpectedCells(), "celdas E<5");
        assertEquals(totalCells, r.getTotalCells(), "total celdas");
    }

    @Test
    void edadPorSituacionLaboral() {
        assertChi(new long[][]{{3, 22, 26, 18, 70}, {3, 14, 3, 3, 5}, {0, 2, 6, 2, 7}},
                28.8, 8, 0.0003436514, 0.28, 7, 15);
    }

    @Test
    void edadAgrupadaPorSituacion() {
        assertChi(new long[][]{{25, 44, 70}, {17, 6, 5}, {2, 8, 7}},
                26.73, 4, 2.2542212e-05, 0.27, 1, 9);
    }

    @Test
    void generoPorSituacion() {
        assertChi(new long[][]{{112, 27}, {20, 8}, {15, 2}},
                2.03, 2, 0.36322243, 0.105, 1, 6);
    }

    @Test
    void generoPorTiempoPrimerEmpleo() {
        assertChi(new long[][]{{54, 24, 28}, {15, 6, 7}},
                0.06, 2, 0.96984182, 0.021, 0, 6);
    }

    @Test
    void segmentoPorSituacion() {
        assertChi(new long[][]{{60, 18, 6}, {17, 5, 3}, {60, 4, 7}},
                8.63, 4, 0.07091415, 0.155, 2, 9);
    }

    @Test
    void segmentoPorTiempoPrimerEmpleo() {
        assertChi(new long[][]{{33, 16, 17}, {11, 1, 6}, {24, 12, 12}},
                3.31, 4, 0.50779293, 0.112, 2, 9);
    }

    @Test
    void nivelPorFinanciamientoConFisher() {
        long[][] obs = {{77, 34}, {3, 5}};
        assertChi(obs, 3.44, 1, 0.06363758, 0.17, 1, 4);
        ChiSquareResultDTO r = run(obs);
        assertNotNull(r.getFisherP());
        assertEquals(0.11207426, r.getFisherP(), 0.0005, "Fisher");
        assertFalse(r.isCochranOk(), "25% celdas E<5 → Cochran no cumple");
    }

    @Test
    void formacionContinuaPorInteresConFisher() {
        long[][] obs = {{105, 50}, {14, 15}};
        assertChi(obs, 4.05, 1, 0.04412527, 0.148, 0, 4);
        assertEquals(0.05667212, run(obs).getFisherP(), 0.0005, "Fisher");
    }

    @Test
    void percepcionPorOrganizacionConFisher() {
        long[][] obs = {{41, 7}, {69, 38}};
        assertChi(obs, 7.05, 1, 0.00794599, 0.213, 0, 4);
        assertEquals(0.00771735, run(obs).getFisherP(), 0.0005, "Fisher");
    }

    @Test
    void nivelPorLugarDondeCurso() {
        assertChi(new long[][]{{33, 2}, {20, 3}, {7, 16}, {20, 18}},
                33.5, 3, 2.526179e-07, 0.531, 0, 8);
    }

    @Test
    void generoPorRemuneracion() {
        assertChi(new long[][]{{36, 8}, {47, 11}, {29, 8}},
                0.17, 2, 0.92065550, 0.034, 0, 6);
    }

    @Test
    void sectorPorRemuneracion() {
        assertChi(new long[][]{{27, 17}, {43, 15}, {36, 1}},
                14.58, 2, 0.00068171, 0.324, 0, 6);
    }

    @Test
    void interpretacionDeCohenPorGradosMenores() {
        // V grande con k−1 = 1 (tabla 4×2, Nivel × Lugar).
        assertEquals("Grande", run(new long[][]{{33, 2}, {20, 3}, {7, 16}, {20, 18}}).getCramersVLevel());
        // V = 0,324 con k−1 = 1 (2 columnas) → Mediano (umbrales 0,10/0,30/0,50).
        assertEquals("Mediano", run(new long[][]{{27, 17}, {43, 15}, {36, 1}}).getCramersVLevel());
    }
}
