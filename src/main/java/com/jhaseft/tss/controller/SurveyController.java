package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.*;
import com.jhaseft.tss.model.Question;
import com.jhaseft.tss.model.SubmissionAnswer;
import com.jhaseft.tss.service.PublicFormService;
import com.jhaseft.tss.service.StatsService;
import com.jhaseft.tss.service.SurveyDefinitionService;
import com.jhaseft.tss.model.QuestionOption;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.SurveyImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SurveyController {

    private final SurveyImportService importService;
    private final SurveyDefinitionService definitionService;
    private final StatsService statsService;
    private final PublicFormService publicFormService;
    private final SurveyRepository surveyRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final SubmissionAnswerOptionRepository answerOptionRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;

    public SurveyController(SurveyImportService importService,
                             SurveyDefinitionService definitionService,
                             StatsService statsService,
                             PublicFormService publicFormService,
                             SurveyRepository surveyRepo,
                             SubmissionRepository submissionRepo,
                             SubmissionAnswerRepository answerRepo,
                             SubmissionAnswerOptionRepository answerOptionRepo,
                             QuestionRepository questionRepo,
                             QuestionOptionRepository optionRepo) {
        this.importService = importService;
        this.definitionService = definitionService;
        this.statsService = statsService;
        this.publicFormService = publicFormService;
        this.surveyRepo = surveyRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.answerOptionRepo = answerOptionRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
    }

    // ── Admin: crear encuesta desde definición JSON ──────────────────────────

    @PostMapping("/surveys/from-definition")
    public ResponseEntity<SurveyImportResultDTO> fromDefinition(
            @RequestBody SurveyDefinitionDTO def) {
        try {
            return ResponseEntity.ok(definitionService.create(def));
        } catch (IllegalArgumentException e) {
            SurveyImportResultDTO err = new SurveyImportResultDTO();
            err.setErrors(List.of("Definición inválida: " + e.getMessage()));
            return ResponseEntity.badRequest().body(err);
        }
    }

    // ── Admin: importar respuestas CSV a survey existente ────────────────────

    @PostMapping("/surveys/{id}/import-responses")
    public ResponseEntity<SurveyImportResultDTO> importResponses(
            @PathVariable Long id,
            @RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            SurveyImportResultDTO err = new SurveyImportResultDTO();
            err.setErrors(List.of("El archivo está vacío."));
            return ResponseEntity.badRequest().body(err);
        }
        try {
            return ResponseEntity.ok(importService.importResponses(id, file));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            SurveyImportResultDTO err = new SurveyImportResultDTO();
            err.setErrors(List.of("Error leyendo el archivo: " + e.getMessage()));
            return ResponseEntity.internalServerError().body(err);
        }
    }

    // ── Admin: listar encuestas ──────────────────────────────────────────────

    @GetMapping("/surveys")
    public ResponseEntity<List<SurveyDTO>> list() {
        List<SurveyDTO> dtos = surveyRepo.findAll().stream().map(s -> {
            SurveyDTO dto = new SurveyDTO();
            dto.setId(s.getId());
            dto.setName(s.getName());
            dto.setOrigin(s.getOrigin().name());
            dto.setPublicSlug(s.getPublicSlug());
            dto.setCreatedAt(s.getCreatedAt());
            dto.setResponseCount(submissionRepo.countBySurveyId(s.getId()));
            return dto;
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    // ── Admin: detalle de encuesta ───────────────────────────────────────────

    @GetMapping("/surveys/{id}")
    public ResponseEntity<SurveyDTO> get(@PathVariable Long id) {
        return surveyRepo.findById(id).map(s -> {
            SurveyDTO dto = new SurveyDTO();
            dto.setId(s.getId());
            dto.setName(s.getName());
            dto.setOrigin(s.getOrigin().name());
            dto.setPublicSlug(s.getPublicSlug());
            dto.setCreatedAt(s.getCreatedAt());
            dto.setResponseCount(submissionRepo.countBySurveyId(s.getId()));
            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Admin: eliminar encuesta (cascada manual) ────────────────────────────

    @DeleteMapping("/surveys/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!surveyRepo.existsById(id)) return ResponseEntity.notFound().build();

        List<Long> submissionIds = submissionRepo.findBySurveyId(id)
                .stream().map(s -> s.getId()).toList();

        if (!submissionIds.isEmpty()) {
            List<Long> answerIds = answerRepo.findBySubmissionIdIn(submissionIds)
                    .stream().map(SubmissionAnswer::getId).toList();
            if (!answerIds.isEmpty()) {
                answerOptionRepo.deleteAll(answerOptionRepo.findByAnswerIdIn(answerIds));
                answerRepo.deleteAllById(answerIds);
            }
            submissionRepo.deleteAllById(submissionIds);
        }

        List<Long> questionIds = questionRepo.findBySurveyIdOrderByPosition(id)
                .stream().map(Question::getId).toList();
        if (!questionIds.isEmpty()) {
            optionRepo.deleteAll(optionRepo.findByQuestionIdIn(questionIds));
            questionRepo.deleteAllById(questionIds);
        }

        surveyRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: estadísticas de una encuesta ─────────────────────────────────

    @GetMapping("/surveys/{id}/stats")
    public ResponseEntity<SurveyStatsDTO> stats(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(statsService.compute(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Público: estructura del formulario (sin datos de otros respondentes) ─

    @GetMapping("/public/surveys/{slug}")
    public ResponseEntity<Map<String, Object>> publicForm(@PathVariable String slug) {
        return surveyRepo.findByPublicSlug(slug).map(survey -> {
            List<Question> questions = questionRepo
                    .findBySurveyIdOrderByPosition(survey.getId());

            List<Map<String, Object>> questionList = new ArrayList<>();
            for (Question q : questions) {
                Map<String, Object> qMap = new LinkedHashMap<>();
                qMap.put("id", q.getId());
                qMap.put("text", q.getText());
                qMap.put("type", q.getType().name());
                qMap.put("position", q.getPosition());
                qMap.put("required", !q.isPersonalData());
                if (q.getSection() != null) qMap.put("section", q.getSection());
                if (q.getMatrixGroup() != null) qMap.put("matrixGroup", q.getMatrixGroup());

                if (q.getParentQuestion() != null) {
                    qMap.put("parentQuestionId", q.getParentQuestion().getId());
                    qMap.put("conditionalValue", q.getConditionalValue());
                }

                List<QuestionOption> opts = optionRepo.findByQuestionIdOrderByPosition(q.getId());
                if (!opts.isEmpty()) {
                    qMap.put("options", opts.stream().map(o -> {
                        Map<String, Object> om = new LinkedHashMap<>();
                        om.put("id", o.getId());
                        om.put("displayText", o.getDisplayText());
                        return om;
                    }).toList());
                }

                questionList.add(qMap);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("surveyId", survey.getId());
            body.put("name", survey.getName());
            body.put("questions", questionList);
            return ResponseEntity.ok(body);

        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Público: enviar respuestas ────────────────────────────────────────────

    @PostMapping("/public/surveys/{slug}/respond")
    public ResponseEntity<Map<String, Object>> respond(
            @PathVariable String slug,
            @RequestBody SurveyResponseDTO body) {
        try {
            Long subId = publicFormService.saveResponse(slug, body.getAnswers());
            return ResponseEntity.ok(Map.of("submissionId", subId, "status", "ok"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno"));
        }
    }
}
