package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.SurveyDTO;
import com.jhaseft.tss.dto.SurveyImportResultDTO;
import com.jhaseft.tss.model.Question;
import com.jhaseft.tss.model.QuestionOption;
import com.jhaseft.tss.repository.QuestionOptionRepository;
import com.jhaseft.tss.repository.QuestionRepository;
import com.jhaseft.tss.repository.SubmissionRepository;
import com.jhaseft.tss.repository.SurveyRepository;
import com.jhaseft.tss.service.SurveyImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SurveyController {

    private final SurveyImportService importService;
    private final SurveyRepository surveyRepo;
    private final SubmissionRepository submissionRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;

    public SurveyController(SurveyImportService importService,
                             SurveyRepository surveyRepo,
                             SubmissionRepository submissionRepo,
                             QuestionRepository questionRepo,
                             QuestionOptionRepository optionRepo) {
        this.importService = importService;
        this.surveyRepo = surveyRepo;
        this.submissionRepo = submissionRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
    }

    // ── Admin: subir archivo ─────────────────────────────────────────────────

    @PostMapping("/surveys/upload")
    public ResponseEntity<SurveyImportResultDTO> upload(
            @RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            SurveyImportResultDTO err = new SurveyImportResultDTO();
            err.setErrors(List.of("El archivo está vacío."));
            return ResponseEntity.badRequest().body(err);
        }
        try {
            SurveyImportResultDTO result = importService.importFile(file);
            if (result.getSurveyId() == null) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
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

    // ── Público: estructura del formulario (sin datos de otros respondentes) ─

    @GetMapping("/public/surveys/{slug}")
    public ResponseEntity<Map<String, Object>> publicForm(@PathVariable String slug) {
        return surveyRepo.findByPublicSlug(slug).map(survey -> {
            List<Question> questions = questionRepo
                    .findBySurveyIdAndPersonalDataFalseOrderByPosition(survey.getId());

            List<Map<String, Object>> questionList = new ArrayList<>();
            for (Question q : questions) {
                Map<String, Object> qMap = new LinkedHashMap<>();
                qMap.put("id", q.getId());
                qMap.put("text", q.getText());
                qMap.put("type", q.getType().name());
                qMap.put("position", q.getPosition());
                qMap.put("required", !q.isPersonalData());

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
}
