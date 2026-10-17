package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.CrossTabRequestDTO;
import com.jhaseft.tss.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
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
}
