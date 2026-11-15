package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.CrossTabResultDTO;
import com.jhaseft.tss.service.AnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cruces χ² contra la BD de desarrollo (encuesta 15, criterio B). Verifica dos correcciones pedidas:
 *  1. Se descartan las categorías vacías: Género (443) excluye "Prefiero no indicar" (0 respuestas),
 *     así el cruce con Situación laboral (455) queda en gl = 2 y cumple Cochran (1/6 celdas E<5).
 *  2. La variable derivada "Segmento de experiencia" (−453) permite cruzar "años de vida profesional"
 *     recodificado: Segmento × Situación = χ² 8,63; gl 4; n 180 (referencia chi_referencia_184.md).
 */
@SpringBootTest
class AnalysisServiceDbTest {

    @Autowired
    private AnalysisService svc;

    @Test
    void generoPorSituacionDescartaCategoriaVacia() {
        CrossTabResultDTO r = svc.crossTab(15L, 443L, 455L);

        assertFalse(r.getRowLabels().stream().anyMatch(l -> l.toLowerCase().contains("prefiero")),
                "La categoría vacía 'Prefiero no indicar' no debe aparecer");
        assertEquals(184, r.getN());
        assertEquals(2, r.getDegreesOfFreedom());
        assertEquals(2.03, r.getChiSquare(), 0.05);
        assertEquals(6, r.getTotalCells());
        assertEquals(1, r.getLowExpectedCells());
        assertTrue(r.isCochranOk(), "16,7% de celdas E<5 ≤ 20% → Cochran cumple");
        assertNull(r.getWarning());
    }

    @Test
    void segmentoDeExperienciaPorSituacion() {
        // −453 = variable derivada "Segmento de experiencia" sobre la pregunta 453.
        CrossTabResultDTO r = svc.crossTab(15L, -453L, 455L);

        assertEquals(180, r.getN());
        assertEquals(3, r.getRowLabels().size());
        assertEquals(4, r.getDegreesOfFreedom());
        assertEquals(8.63, r.getChiSquare(), 0.05);
        assertEquals(9, r.getTotalCells());
        assertEquals(2, r.getLowExpectedCells());
    }
}
