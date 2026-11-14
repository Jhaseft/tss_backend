package com.jhaseft.tss.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhaseft.tss.dto.MultivariateRequestDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO;
import com.jhaseft.tss.service.MultivariateAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generador de entregables (no forma parte de la suite normal). Escribe el JSON de la salida del
 * endpoint multivariable para los dos cruces pedidos en PROMPT_1. Ejecutar con:
 *   ./mvnw.cmd -Dgen.entregables=true -Dtest=EntregablesGeneratorTest test
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "gen.entregables", matches = "true")
class EntregablesGeneratorTest {

    @Autowired
    private MultivariateAnalysisService svc;

    private final ObjectMapper mapper = new ObjectMapper();

    private void write(String file, Long x, Long y, Long z) throws Exception {
        MultivariateRequestDTO req = new MultivariateRequestDTO();
        req.setQuestionIdX(x); req.setQuestionIdY(y); req.setQuestionIdZ(z);
        MultivariateResultDTO r = svc.analyze(15L, req);
        Path out = Path.of("docs", "informe", "handoff", "entregables", file);
        Files.createDirectories(out.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), r);
        System.out.println("Escrito: " + out.toAbsolutePath() + "  (n=" + r.getUsedN() + ")");
    }

    @Test
    void generarJsonMultivariable() throws Exception {
        // Área de interés × Organización preferida × Años (cruce recomendado, §3.5.2).
        write("mv_450x451x453.json", 450L, 451L, 453L);
        // Área de interés × Fuente de financiamiento × Años (cruce del Informe 1 §3.5.1).
        write("mv_450x447x453.json", 450L, 447L, 453L);
    }
}
