package com.jhaseft.tss.stats;

import com.jhaseft.tss.dto.MultivariateResultDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO.Cell;
import com.jhaseft.tss.service.MultivariateAnalysisService;
import com.jhaseft.tss.service.MultivariateAnalysisService.Rec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caso del docente (10 titulados) para el núcleo de agregación multivariable. No usa BD:
 * {@link MultivariateAnalysisService#aggregate} es puro. Valores esperados de la hoja del docente.
 */
class MultivariateAnalysisServiceTest {

    // El núcleo no usa los repositorios; se puede instanciar con dependencias nulas.
    private final MultivariateAnalysisService svc =
            new MultivariateAnalysisService(null, null, null, null, null);

    private static final List<Rec> DOCENTE = List.of(
            new Rec("C.Datos", "B.Estatales", 3),   // T1
            new Rec("Ciberseg", "R.Propios", 5),    // T2
            new Rec("Ing.SW", "F.Empresa", 1),      // T3
            new Rec("C.Datos", "B.Estatales", 7),   // T4
            new Rec("Redes", "Credito", 2),         // T5
            new Rec("Ciberseg", "R.Propios", 4),    // T6
            new Rec("Ing.SW", "F.Empresa", 6),      // T7
            new Rec("C.Datos", "B.Estatales", 3),   // T8
            new Rec("Ing.SW", "Credito", 5),        // T9
            new Rec("Redes", "R.Propios", 2));      // T10

    private Cell cell(MultivariateResultDTO r, String x, String y) {
        return r.getCells().stream()
                .filter(c -> c.x.equals(x) && c.y.equals(y)).findFirst().orElse(null);
    }

    @Test
    void casoDelDocente() {
        MultivariateResultDTO r = svc.aggregate(DOCENTE, null, null);

        assertEquals(10, r.getUsedN());
        assertEquals(3.8, r.getTotal().meanZ, 1e-9);

        Cell cDatos = cell(r, "C.Datos", "B.Estatales");
        assertEquals(3, cDatos.n);
        assertEquals(4.33, cDatos.meanZ, 0.01);

        assertEquals(2, cell(r, "Ciberseg", "R.Propios").n);
        assertEquals(4.50, cell(r, "Ciberseg", "R.Propios").meanZ, 1e-9);

        assertEquals(2, cell(r, "Ing.SW", "F.Empresa").n);
        assertEquals(3.50, cell(r, "Ing.SW", "F.Empresa").meanZ, 1e-9);

        assertEquals(1, cell(r, "Ing.SW", "Credito").n);
        assertEquals(5.0, cell(r, "Ing.SW", "Credito").meanZ, 1e-9);

        assertEquals(1, cell(r, "Redes", "Credito").n);
        assertEquals(2.0, cell(r, "Redes", "Credito").meanZ, 1e-9);

        assertEquals(1, cell(r, "Redes", "R.Propios").n);
        assertEquals(2.0, cell(r, "Redes", "R.Propios").meanZ, 1e-9);

        // 6 combinaciones observadas; los puntos individuales son los 10 titulados.
        assertEquals(6, r.getCells().size());
        assertEquals(10, r.getPoints().size());
    }

    @Test
    void marginalPorX() {
        MultivariateResultDTO r = svc.aggregate(DOCENTE, null, null);
        var ing = r.getMarginalX().stream().filter(m -> m.label.equals("Ing.SW")).findFirst().orElseThrow();
        assertEquals(3, ing.n);                       // T3=1, T7=6, T9=5
        assertEquals(4.0, ing.meanZ, 1e-9);
        var datos = r.getMarginalX().stream().filter(m -> m.label.equals("C.Datos")).findFirst().orElseThrow();
        assertEquals(3, datos.n);
        assertEquals(13.0 / 3, datos.meanZ, 1e-9);
    }
}
