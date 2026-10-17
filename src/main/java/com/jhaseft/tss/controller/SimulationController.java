package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.SimulationRequestDTO;
import com.jhaseft.tss.dto.SimulationResultDTO;
import com.jhaseft.tss.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/surveys/{id}/simulate")
    public ResponseEntity<?> simulate(@PathVariable Long id,
                                       @RequestBody SimulationRequestDTO req) {
        try {
            int iters = req.getIterations() != null ? req.getIterations() : 1000;
            SimulationResultDTO result = simulationService.bootstrap(id, req.getQuestionId(), iters);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
