package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.SurveyImportResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.FileReaderService.ParsedFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final SubmissionAnswerOptionRepository answerOptionRepo;

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
                                SubmissionRepository submissionRepo,
                                SubmissionAnswerRepository answerRepo,
                                SubmissionAnswerOptionRepository answerOptionRepo) {
        this.fileReader = fileReader;
        this.inference = inference;
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.answerOptionRepo = answerOptionRepo;
    }

    // ── Importar respuestas CSV a survey existente (definido via JSON) ───────

    @Transactional
    public SurveyImportResultDTO importResponses(Long surveyId, MultipartFile file) throws IOException {
        Survey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new NoSuchElementException("Survey " + surveyId + " no encontrado"));

        List<Question> allQuestions = questionRepo.findBySurveyIdOrderByPosition(surveyId);
        Map<String, Question> normIndex = new LinkedHashMap<>();
        for (Question q : allQuestions) {
            normIndex.put(inference.normalize(q.getColumnName()), q);
        }

        ParsedFile parsed = fileReader.parse(file);
        SurveyOrigin origin = survey.getOrigin();
        String tsColumn = inference.getTimestampColumn(parsed.getHeaders(), origin);

        // Excluye columnas de metadatos (ID, Puntos:, Comentarios:, etc.). Sin esto, en
        // MS Forms las columnas "Puntos: <pregunta>" y "Comentarios: <pregunta>" contienen
        // el texto de la pregunta y el matcher difuso las asignaba a la misma pregunta,
        // creando 2-3 respuestas por persona e inflando el n de las estadísticas.
        List<String> questionHeaders = inference.filterMetadata(parsed.getHeaders(), origin);

        Map<String, Question> colToQuestion = new LinkedHashMap<>();
        Set<Long> usedQuestions = new HashSet<>();
        for (String header : questionHeaders) {
            if (header.equals(tsColumn)) continue;
            Question q = matchQuestion(header, normIndex);
            // Una sola columna por pregunta: evita duplicar respuestas si dos encabezados
            // casi idénticos apuntan a la misma pregunta.
            if (q != null && usedQuestions.add(q.getId())) {
                colToQuestion.put(header, q);
            }
        }

        Map<Long, Map<String, QuestionOption>> optionsByQuestion = new HashMap<>();
        for (Question q : colToQuestion.values()) {
            if (!optionsByQuestion.containsKey(q.getId())) {
                Map<String, QuestionOption> opMap = new LinkedHashMap<>();
                optionRepo.findByQuestionIdOrderByPosition(q.getId())
                        .forEach(o -> opMap.put(inference.normalize(o.getValue()), o));
                optionsByQuestion.put(q.getId(), opMap);
            }
        }

        int loaded = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> rows = parsed.getRows();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Map<String, String> row = rows.get(rowIdx);

            if (submissionRepo.existsBySurveyIdAndRowIndex(surveyId, rowIdx)) continue;

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

                SubmissionAnswer ans = new SubmissionAnswer();
                ans.setSubmission(sub);
                ans.setQuestion(q);
                ans.setRawValue(raw.isBlank() ? null : raw);
                ans.setNormalizedValue(raw.isBlank() ? null : inference.normalize(raw));
                ans.setNotApplicable(raw.isBlank());
                ans = answerRepo.save(ans);

                if (!raw.isBlank()) {
                    if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                        saveMultipleOptions(ans, raw, q, optionsByQuestion, origin);
                    } else if (q.getType() == QuestionType.SINGLE_NOMINAL
                            || q.getType() == QuestionType.SINGLE_ORDINAL
                            || q.getType() == QuestionType.LIKERT
                            || q.getType() == QuestionType.MATRIX_LIKERT) {
                        saveSingleOption(ans, raw, q, optionsByQuestion);
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
        result.setQuestionsDetected(colToQuestion.size());
        result.setErrors(errors);
        return result;
    }

    private Question matchQuestion(String csvHeader, Map<String, Question> normIndex) {
        String norm = inference.normalize(csvHeader);

        Question q = normIndex.get(norm);
        if (q != null) return q;

        String stripped = norm.replaceFirst("^\\d+[.)\\s]+\\s*", "");
        q = normIndex.get(stripped);
        if (q != null) return q;

        int bStart = csvHeader.indexOf('[');
        int bEnd   = csvHeader.lastIndexOf(']');
        if (bStart >= 0 && bEnd > bStart) {
            String inner = inference.normalize(csvHeader.substring(bStart + 1, bEnd));
            q = normIndex.get(inner);
            if (q != null) return q;
        }

        for (Map.Entry<String, Question> entry : normIndex.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 15 && norm.contains(key)) return entry.getValue();
        }

        for (Map.Entry<String, Question> entry : normIndex.entrySet()) {
            String key = entry.getKey();
            if (stripped.length() > 15 && key.contains(stripped)) return entry.getValue();
        }

        return null;
    }

    // ── Helpers de persistencia ──────────────────────────────────────────────

    private void saveMultipleOptions(SubmissionAnswer ans, String raw, Question q,
                                      Map<Long, Map<String, QuestionOption>> optsByQ,
                                      SurveyOrigin origin) {
        List<String> parts = inference.splitMultiOptions(raw, origin);
        Map<String, QuestionOption> opMap = optsByQ.getOrDefault(q.getId(), Map.of());
        for (String part : parts) {
            String normPart = inference.normalize(part);
            QuestionOption opt = opMap.get(normPart);
            SubmissionAnswerOption sao = new SubmissionAnswerOption();
            sao.setAnswer(ans);
            if (opt != null) {
                sao.setOption(opt);
            } else {
                QuestionOption newOpt = createOptionOnDemand(q, part);
                opMap.put(normPart, newOpt);
                sao.setOption(newOpt);
            }
            answerOptionRepo.save(sao);
        }
    }

    private void saveSingleOption(SubmissionAnswer ans, String raw,
                                   Question q, Map<Long, Map<String, QuestionOption>> optsByQ) {
        Map<String, QuestionOption> opMap = optsByQ.getOrDefault(q.getId(), Map.of());
        QuestionOption opt = opMap.get(inference.normalize(raw));
        if (opt == null) return;
        SubmissionAnswerOption sao = new SubmissionAnswerOption();
        sao.setAnswer(ans);
        sao.setOption(opt);
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

}
