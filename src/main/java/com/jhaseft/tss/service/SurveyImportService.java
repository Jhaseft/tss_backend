package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.SurveyImportResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.FileReaderService.ParsedFile;
import com.jhaseft.tss.service.QuestionInferenceService.InferredQuestion;
import com.jhaseft.tss.service.QuestionInferenceService.OptionDef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SurveyImportService {

    private final FileReaderService fileReader;
    private final QuestionInferenceService inference;
    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final LikertScaleRepository likertScaleRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final SubmissionAnswerOptionRepository answerOptionRepo;

    // Formatos de fecha de MS Forms y Google Forms
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yy H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    public SurveyImportService(FileReaderService fileReader,
                                QuestionInferenceService inference,
                                SurveyRepository surveyRepo,
                                QuestionRepository questionRepo,
                                QuestionOptionRepository optionRepo,
                                LikertScaleRepository likertScaleRepo,
                                SubmissionRepository submissionRepo,
                                SubmissionAnswerRepository answerRepo,
                                SubmissionAnswerOptionRepository answerOptionRepo) {
        this.fileReader = fileReader;
        this.inference = inference;
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.likertScaleRepo = likertScaleRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.answerOptionRepo = answerOptionRepo;
    }

    @Transactional
    public SurveyImportResultDTO importFile(MultipartFile file) throws IOException {
        // 1. Parsear archivo
        ParsedFile parsed = fileReader.parse(file);
        List<String> headers = parsed.getHeaders();
        List<Map<String, String>> rows = parsed.getRows();

        // 2. Detectar origen y columna de timestamp
        SurveyOrigin origin = inference.detectOrigin(headers);
        String tsColumn = inference.getTimestampColumn(headers, origin);

        // 3. Verificar duplicado por hash del contenido
        String hash = sha256(file.getBytes());
        if (surveyRepo.existsBySourceHash(hash)) {
            SurveyImportResultDTO dup = new SurveyImportResultDTO();
            dup.setErrors(List.of("Este archivo ya fue cargado anteriormente."));
            return dup;
        }

        // 4. Inferir preguntas
        List<InferredQuestion> questions = inference.inferAll(headers, rows, origin);

        // 5. Persistir Survey
        Survey survey = new Survey();
        survey.setName(Objects.requireNonNull(file.getOriginalFilename()));
        survey.setOrigin(origin);
        survey.setFileName(file.getOriginalFilename());
        survey.setSourceHash(hash);
        survey.setPublicSlug(UUID.randomUUID().toString());
        survey = surveyRepo.save(survey);

        // 6. Persistir Questions + Options
        Map<String, Question> colToQuestion = new LinkedHashMap<>();
        for (InferredQuestion iq : questions) {
            Question q = new Question();
            q.setSurvey(survey);
            q.setColumnName(iq.getColumnName());
            q.setText(truncate(iq.getText(), 1000));
            q.setType(iq.getType());
            q.setPosition(iq.getPosition());
            q.setPersonalData(iq.isPersonalData());
            q.setMatrixGroup(iq.getMatrixItems().isEmpty() ? null
                    : String.join("||", iq.getMatrixItems()));

            if (iq.getLikertScaleName() != null) {
                q.setLikertScale(getOrCreateLikertScale(iq.getLikertScaleName(), iq.getOptions()));
            }

            q = questionRepo.save(q);

            for (OptionDef opt : iq.getOptions()) {
                QuestionOption qo = new QuestionOption();
                qo.setQuestion(q);
                qo.setValue(truncate(opt.getValue(), 500));
                qo.setDisplayText(truncate(opt.getDisplayText(), 500));
                qo.setPosition(opt.getPosition());
                qo.setNumericValue(opt.getNumericValue());
                optionRepo.save(qo);
            }

            colToQuestion.put(iq.getColumnName(), q);

            // Para MATRIX_LIKERT, mapear también cada ítem de columna a la misma pregunta
            for (String item : iq.getMatrixItems()) {
                colToQuestion.put(item, q);
            }
        }

        // 7. Precargar opciones por pregunta para dedup en respuestas múltiples
        Map<Long, Map<String, QuestionOption>> optionsByQuestion = new HashMap<>();
        for (Question q : colToQuestion.values()) {
            if (!optionsByQuestion.containsKey(q.getId())) {
                Map<String, QuestionOption> opMap = new LinkedHashMap<>();
                optionRepo.findByQuestionIdOrderByPosition(q.getId())
                        .forEach(o -> opMap.put(inference.normalize(o.getValue()), o));
                optionsByQuestion.put(q.getId(), opMap);
            }
        }

        // 8. Persistir Submissions + Answers
        int loaded = 0;
        List<String> errors = new ArrayList<>();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Map<String, String> row = rows.get(rowIdx);

            // Dedup por rowIndex
            if (submissionRepo.existsBySurveyIdAndRowIndex(survey.getId(), rowIdx)) continue;

            String tsRaw = tsColumn != null ? row.getOrDefault(tsColumn, "") : "";
            LocalDateTime submittedAt = parseTimestamp(tsRaw);

            Submission sub = new Submission();
            sub.setSurvey(survey);
            sub.setSubmittedAt(submittedAt);
            sub.setRowIndex(rowIdx);
            sub.setSource(SubmissionSource.IMPORT);
            sub.setInconsistent(false);
            sub = submissionRepo.save(sub);

            for (Map.Entry<String, Question> entry : colToQuestion.entrySet()) {
                String colName = entry.getKey();
                Question q = entry.getValue();
                String raw = row.getOrDefault(colName, "").trim();

                // Para MATRIX_LIKERT, la clave es el ítem individual (columna original)
                String matrixItemLabel = null;
                if (q.getType() == QuestionType.MATRIX_LIKERT && !raw.isBlank()) {
                    matrixItemLabel = cleanItem(colName);
                }

                SubmissionAnswer ans = new SubmissionAnswer();
                ans.setSubmission(sub);
                ans.setQuestion(q);
                ans.setRawValue(raw.isBlank() ? null : raw);
                ans.setNormalizedValue(raw.isBlank() ? null : inference.normalize(raw));
                ans.setNotApplicable(raw.isBlank());
                ans = answerRepo.save(ans);

                // Opciones para tipos de selección
                if (!raw.isBlank()) {
                    if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                        saveMultipleOptions(ans, raw, q, optionsByQuestion, origin);
                    } else if (q.getType() == QuestionType.SINGLE_NOMINAL
                            || q.getType() == QuestionType.SINGLE_ORDINAL
                            || q.getType() == QuestionType.LIKERT
                            || q.getType() == QuestionType.MATRIX_LIKERT) {
                        saveSingleOption(ans, raw, matrixItemLabel, q, optionsByQuestion);
                    }
                }
            }
            loaded++;
        }

        SurveyImportResultDTO result = new SurveyImportResultDTO();
        result.setSurveyId(survey.getId());
        result.setPublicSlug(survey.getPublicSlug());
        result.setSurveyName(survey.getName());
        result.setOrigin(origin.name());
        result.setRowsLoaded(loaded);
        result.setQuestionsDetected(questions.size());
        result.setErrors(errors);
        return result;
    }

    // ── Helpers de persistencia ──────────────────────────────────────────────

    private void saveMultipleOptions(SubmissionAnswer ans, String raw, Question q,
                                      Map<Long, Map<String, QuestionOption>> optsByQ,
                                      SurveyOrigin origin) {
        List<String> parts;
        if (origin == SurveyOrigin.MS_FORMS) {
            parts = Arrays.stream(raw.split(";"))
                    .map(String::trim).filter(s -> !s.isBlank()).toList();
        } else {
            parts = Arrays.stream(raw.split(",\\s+"))
                    .map(String::trim).filter(s -> !s.isBlank()).toList();
        }

        Map<String, QuestionOption> opMap = optsByQ.getOrDefault(q.getId(), Map.of());
        for (String part : parts) {
            String normPart = inference.normalize(part);
            QuestionOption opt = opMap.get(normPart);
            SubmissionAnswerOption sao = new SubmissionAnswerOption();
            sao.setAnswer(ans);
            if (opt != null) {
                sao.setOption(opt);
            } else {
                // Opción no conocida → buscar o crear
                QuestionOption newOpt = createOptionOnDemand(q, part);
                opMap.put(normPart, newOpt);
                sao.setOption(newOpt);
            }
            answerOptionRepo.save(sao);
        }
    }

    private void saveSingleOption(SubmissionAnswer ans, String raw, String matrixLabel,
                                   Question q, Map<Long, Map<String, QuestionOption>> optsByQ) {
        Map<String, QuestionOption> opMap = optsByQ.getOrDefault(q.getId(), Map.of());
        String normRaw = inference.normalize(raw);
        QuestionOption opt = opMap.get(normRaw);
        if (opt == null) return; // valor sin opción conocida (texto libre)

        SubmissionAnswerOption sao = new SubmissionAnswerOption();
        sao.setAnswer(ans);
        sao.setOption(opt);
        if (matrixLabel != null) sao.setFreeText(matrixLabel);
        answerOptionRepo.save(sao);
    }

    private QuestionOption createOptionOnDemand(Question q, String raw) {
        QuestionOption opt = new QuestionOption();
        opt.setQuestion(q);
        opt.setValue(inference.normalize(raw));
        opt.setDisplayText(raw.trim());
        opt.setPosition(999);
        return optionRepo.save(opt);
    }

    private LikertScale getOrCreateLikertScale(String name, List<OptionDef> options) {
        return likertScaleRepo.findByName(name).orElseGet(() -> {
            LikertScale ls = new LikertScale();
            ls.setName(name);
            ls.setOptionsJson(buildOptionsJson(options));
            return likertScaleRepo.save(ls);
        });
    }

    private String buildOptionsJson(List<OptionDef> opts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < opts.size(); i++) {
            OptionDef o = opts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"value\":\"").append(o.getValue())
              .append("\",\"numeric\":").append(o.getNumericValue() == null ? "null" : o.getNumericValue())
              .append("}");
        }
        return sb.append("]").toString();
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(raw.trim(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDateTime.now();
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String cleanItem(String colName) {
        String s = colName.trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1).trim();
        return truncate(s, 500);
    }
}
