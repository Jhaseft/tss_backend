package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.CompareResultDTO;
import com.jhaseft.tss.service.SurveyCompareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comparativo titulados (15) vs empleadores (14) contra la BD. Verifica que son dos distribuciones
 * independientes con IC de Wilson, no un cruce. q419 = medio por el que el empleador convoca;
 * q467 = medio por el que el titulado obtuvo su empleo (mismo constructo, dos muestras).
 */
@SpringBootTest
class SurveyCompareServiceDbTest {

    @Autowired
    private SurveyCompareService svc;

    @Test
    void comparaMedioDeReclutamientoPorCategorias() {
        CompareResultDTO r = svc.compare(419L, 467L, "CATEGORIES");

        assertEquals("CATEGORIES", r.getMode());
        assertEquals(80, r.getNA());                 // empleadores que respondieron q419
        assertTrue(r.getNB() > 0);
        assertNotNull(r.getDisclaimer());
        assertFalse(r.getRows().isEmpty());

        // Aritmética: pct = count/n·100 y el % cae dentro de su IC de Wilson.
        for (CompareResultDTO.Row row : r.getRows()) {
            if (row.countA > 0) {
                assertEquals(row.countA * 100.0 / r.getNA(), row.pctA, 1e-6);
                assertTrue(row.ciLowA <= row.pctA + 1e-9 && row.pctA <= row.ciHighA + 1e-9);
            }
            assertEquals(row.pctA - row.pctB, row.gap, 1e-9);
        }
    }

    @Test
    void comparaTemasEnTextoLibrePorKeywords() {
        // Áreas/herramientas/competencias que demandan los empleadores (texto libre), por tema común.
        CompareResultDTO r = svc.compare(421L, 423L, "KEYWORDS");

        assertEquals("KEYWORDS", r.getMode());
        assertTrue(r.getNA() > 0 && r.getNB() > 0);
        assertFalse(r.getRows().isEmpty());
        // Las etiquetas deben ser temas del vocabulario común (no texto crudo).
        assertTrue(r.getRows().stream().anyMatch(row ->
                row.label.contains("IA") || row.label.contains("Datos")
                        || row.label.contains("Ciber") || row.label.contains("Cloud")
                        || row.label.contains("Desarrollo") || row.label.contains("Habilidades")));
    }
}
