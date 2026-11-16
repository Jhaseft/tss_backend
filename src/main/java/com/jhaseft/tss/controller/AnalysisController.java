package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.CompareRequestDTO;
import com.jhaseft.tss.dto.CrossTabRequestDTO;
import com.jhaseft.tss.dto.MultivariateRequestDTO;
import com.jhaseft.tss.service.AnalysisService;
import com.jhaseft.tss.service.DerivedVariableService;
import com.jhaseft.tss.service.MultivariateAnalysisService;
import com.jhaseft.tss.service.SurveyCompareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final MultivariateAnalysisService multivariateService;
    private final DerivedVariableService derivedVariables;
    private final SurveyCompareService compareService;

    public AnalysisController(AnalysisService analysisService,
                              MultivariateAnalysisService multivariateService,
                              DerivedVariableService derivedVariables,
                              SurveyCompareService compareService) {
        this.analysisService = analysisService;
        this.multivariateService = multivariateService;
        this.derivedVariables = derivedVariables;
        this.compareService = compareService;
    }

    /** Comparación descriptiva entre dos encuestas (titulados vs empleadores). No es un cruce/χ². */
    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestBody CompareRequestDTO req) {
        try {
            return ResponseEntity.ok(
                    compareService.compare(req.getQuestionIdA(), req.getQuestionIdB(), req.getMode()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Variables derivadas cualitativas (recodificaciones) para cruzar, p. ej. el segmento de experiencia. */
    @GetMapping("/surveys/{id}/derived-variables")
    public List<DerivedVariableService.Info> derivedVariables(@PathVariable Long id) {
        return derivedVariables.listForSurvey(id);
    }

    /** Métricas numéricas derivadas para la Variable Z del multivariable, p. ej. "cantidad de cargos". */
    @GetMapping("/surveys/{id}/derived-metrics")
    public List<DerivedVariableService.Info> derivedMetrics(@PathVariable Long id) {
        return derivedVariables.listZMetrics(id);
    }

    @PostMapping("/surveys/{id}/chi-square")
    public ResponseEntity<?> chiSquare(@PathVariable Long id,
                                        @RequestBody CrossTabRequestDTO req) {
        try {
            return ResponseEntity.ok(analysisService.crossTab(id, req.getQuestionId1(), req.getQuestionId2()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/surveys/{id}/multivariate")
    public ResponseEntity<?> multivariate(@PathVariable Long id,
                                          @RequestBody MultivariateRequestDTO req) {
        try {
            return ResponseEntity.ok(multivariateService.analyze(id, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
