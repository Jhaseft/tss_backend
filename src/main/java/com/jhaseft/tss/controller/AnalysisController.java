package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.CrossTabRequestDTO;
import com.jhaseft.tss.dto.MultivariateRequestDTO;
import com.jhaseft.tss.service.AnalysisService;
import com.jhaseft.tss.service.MultivariateAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final MultivariateAnalysisService multivariateService;

    public AnalysisController(AnalysisService analysisService,
                              MultivariateAnalysisService multivariateService) {
        this.analysisService = analysisService;
        this.multivariateService = multivariateService;
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
