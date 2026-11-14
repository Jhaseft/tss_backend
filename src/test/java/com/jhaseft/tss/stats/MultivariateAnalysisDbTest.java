package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.MultivariateRequestDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO.Cell;
import com.jhaseft.tss.dto.MultivariateResultDTO.Marginal;
import com.jhaseft.tss.service.MultivariateAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verificación con datos reales (BD de desarrollo): Área de interés (450) × Organización preferida
 * (451) × Años (453), encuesta 15, criterio B. Valores de referencia de multivariable_referencia_184.md
 * (n = 151, media general 8,77). Requiere conexión a la BD; márcalo aparte si no está disponible.
 */
@SpringBootTest
class MultivariateAnalysisDbTest {

    @Autowired
    private MultivariateAnalysisService svc;

    private Cell cell(MultivariateResultDTO r, String x, String y) {
        return r.getCells().stream()
                .filter(c -> c.x.equals(x) && c.y.equals(y)).findFirst().orElse(null);
    }

    private Marginal marginalX(MultivariateResultDTO r, String x) {
        return r.getMarginalX().stream().filter(m -> m.label.equals(x)).findFirst().orElse(null);
    }

    @Test
    void areaPorOrganizacionPorAnios() {
        MultivariateRequestDTO req = new MultivariateRequestDTO();
        req.setQuestionIdX(450L);
        req.setQuestionIdY(451L);
        req.setQuestionIdZ(453L);

        MultivariateResultDTO r = svc.analyze(15L, req);

        assertEquals(151, r.getUsedN());
        assertEquals(8.77, r.getTotal().meanZ, 0.01);

        // Celdas de control.
        assertEquals(19, cell(r, "IA", "DIRECCIÓN DE POSGRADO FCyT").n);
        assertEquals(11.89, cell(r, "IA", "DIRECCIÓN DE POSGRADO FCyT").meanZ, 0.01);
        assertEquals(18, cell(r, "IA", "UNIVERSIDADES INTERNACIONALES").n);
        assertEquals(7.83, cell(r, "IA", "UNIVERSIDADES INTERNACIONALES").meanZ, 0.01);
        assertEquals(13, cell(r, "Ingenieria de Software", "UNIVERSIDADES INTERNACIONALES").n);
        assertEquals(2.69, cell(r, "Ingenieria de Software", "UNIVERSIDADES INTERNACIONALES").meanZ, 0.01);

        // Marginales.
        assertEquals(69, marginalX(r, "IA").n);
        assertEquals(10.74, marginalX(r, "IA").meanZ, 0.01);
        assertEquals(36, marginalX(r, "Ingenieria de Software").n);
        assertEquals(4.92, marginalX(r, "Ingenieria de Software").meanZ, 0.01);
    }
}
