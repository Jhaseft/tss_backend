package com.jhaseft.tss.service;

import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final QuestionInferenceService inference;

    public ExportService(SurveyRepository surveyRepo,
                          QuestionRepository questionRepo,
                          QuestionOptionRepository optionRepo,
                          SubmissionRepository submissionRepo,
                          SubmissionAnswerRepository answerRepo,
                          QuestionInferenceService inference) {
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.inference = inference;
    }

    @Transactional(readOnly = true)
    public byte[] generateExcel(Long surveyId) throws IOException {
        Survey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new NoSuchElementException("Survey not found"));

        List<Question> questions = questionRepo.findBySurveyIdOrderByPosition(surveyId);
        List<Submission> submissions = submissionRepo.findBySurveyId(surveyId);
        List<Long> subIds = submissions.stream().map(Submission::getId).toList();
        List<Long> qIds = questions.stream().map(Question::getId).toList();

        Map<Long, List<SubmissionAnswer>> answersByQ = subIds.isEmpty() ? Map.of()
                : answerRepo.findBySubmissionIdIn(subIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getQuestion().getId()));

        Map<Long, List<QuestionOption>> optsByQ = qIds.isEmpty() ? Map.of()
                : optionRepo.findByQuestionIdIn(qIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        // Answer lookup: questionId → submissionId → value
        Map<Long, Map<Long, String>> qSubValue = new HashMap<>();
        for (Long qId : qIds) {
            Map<Long, String> bySubmission = new HashMap<>();
            for (SubmissionAnswer a : answersByQ.getOrDefault(qId, List.of())) {
                bySubmission.put(a.getSubmission().getId(), a.getRawValue() != null ? a.getRawValue() : "");
            }
            qSubValue.put(qId, bySubmission);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle titleStyle = titleStyle(wb);
            CellStyle numStyle = numStyle(wb);

            buildRawSheet(wb, survey, questions, submissions, qSubValue, headerStyle, titleStyle);
            buildFreqSheet(wb, survey, questions, answersByQ, optsByQ, headerStyle, titleStyle, numStyle, survey.getOrigin());
            buildLikertSheet(wb, survey, questions, answersByQ, optsByQ, headerStyle, titleStyle, numStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildRawSheet(XSSFWorkbook wb, Survey survey,
                                 List<Question> questions, List<Submission> submissions,
                                 Map<Long, Map<Long, String>> qSubValue,
                                 CellStyle header, CellStyle title) {
        Sheet sheet = wb.createSheet("Datos brutos");
        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        cell(titleRow, 0, survey.getName(), title);
        cell(titleRow, 1, "Origen: " + survey.getOrigin().name(), null);
        cell(titleRow, 2, "Respuestas: " + submissions.size(), null);
        rowNum++;

        Row hdr = sheet.createRow(rowNum++);
        cell(hdr, 0, "ID", header);
        cell(hdr, 1, "Fuente", header);
        cell(hdr, 2, "Fecha", header);
        for (int i = 0; i < questions.size(); i++) {
            String txt = questions.get(i).getText();
            cell(hdr, 3 + i, txt.length() > 80 ? txt.substring(0, 80) + "…" : txt, header);
        }

        for (Submission sub : submissions) {
            Row row = sheet.createRow(rowNum++);
            cell(row, 0, String.valueOf(sub.getId()), null);
            cell(row, 1, sub.getSource().name(), null);
            cell(row, 2, sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString().replace("T", " ") : "", null);
            for (int i = 0; i < questions.size(); i++) {
                String val = qSubValue.getOrDefault(questions.get(i).getId(), Map.of())
                        .getOrDefault(sub.getId(), "");
                cell(row, 3 + i, val, null);
            }
        }

        sheet.setColumnWidth(0, 1500);
        sheet.setColumnWidth(1, 2500);
        sheet.setColumnWidth(2, 5000);
        for (int i = 0; i < questions.size(); i++) sheet.setColumnWidth(3 + i, 7000);
    }

    private void buildFreqSheet(XSSFWorkbook wb, Survey survey,
                                  List<Question> questions,
                                  Map<Long, List<SubmissionAnswer>> answersByQ,
                                  Map<Long, List<QuestionOption>> optsByQ,
                                  CellStyle header, CellStyle title, CellStyle num,
                                  SurveyOrigin origin) {
        Set<QuestionType> catTypes = Set.of(
                QuestionType.SINGLE_NOMINAL, QuestionType.SINGLE_ORDINAL,
                QuestionType.MULTIPLE_CHOICE, QuestionType.LIKERT, QuestionType.MATRIX_LIKERT);

        Sheet sheet = wb.createSheet("Frecuencias");
        int rowNum = 0;

        Row t = sheet.createRow(rowNum++);
        cell(t, 0, "Distribución de frecuencias · " + survey.getName(), title);
        rowNum++;

        for (Question q : questions) {
            if (!catTypes.contains(q.getType())) continue;

            List<SubmissionAnswer> answers = answersByQ.getOrDefault(q.getId(), List.of());
            List<String> values = answers.stream()
                    .filter(a -> !a.isNotApplicable() && a.getNormalizedValue() != null
                            && !a.getNormalizedValue().isBlank())
                    .map(SubmissionAnswer::getNormalizedValue).toList();
            if (values.isEmpty()) continue;

            Row qRow = sheet.createRow(rowNum++);
            String txt = q.getText();
            cell(qRow, 0, txt.length() > 100 ? txt.substring(0, 100) + "…" : txt, title);

            Row hdr = sheet.createRow(rowNum++);
            cell(hdr, 0, "Opción", header);
            cell(hdr, 1, "Frecuencia", header);
            cell(hdr, 2, "Porcentaje (%)", header);

            List<QuestionOption> opts = optsByQ.getOrDefault(q.getId(), List.of());
            // En selección múltiple hay que separar cada respuesta en sus opciones
            // (respetando comas dentro de paréntesis) antes de contar.
            List<String> countValues = q.getType() == QuestionType.MULTIPLE_CHOICE
                    ? values.stream().flatMap(v -> inference.splitMultiOptions(v, origin).stream())
                            .map(String::trim).filter(s -> !s.isBlank()).toList()
                    : values;
            Map<String, Long> counts = countValues.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            List<Map.Entry<String, Long>> entries;
            if (!opts.isEmpty()) {
                entries = opts.stream()
                        .map(o -> Map.entry(o.getDisplayText(),
                                counts.getOrDefault(o.getValue(), 0L)
                                        + counts.getOrDefault(o.getDisplayText(), 0L)))
                        .collect(Collectors.toList());
            } else {
                entries = counts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(20).collect(Collectors.toList());
            }

            int total = values.size();
            for (Map.Entry<String, Long> e : entries) {
                Row r = sheet.createRow(rowNum++);
                cell(r, 0, e.getKey(), null);
                numCell(r, 1, e.getValue(), null);
                numCell(r, 2, total > 0 ? e.getValue() * 100.0 / total : 0.0, num);
            }
            Row totalRow = sheet.createRow(rowNum++);
            cell(totalRow, 0, "Total", header);
            numCell(totalRow, 1, (long) total, header);
            numCell(totalRow, 2, 100.0, num);
            rowNum++;
        }

        sheet.setColumnWidth(0, 10000);
        sheet.setColumnWidth(1, 3500);
        sheet.setColumnWidth(2, 4000);
    }

    private void buildLikertSheet(XSSFWorkbook wb, Survey survey,
                                    List<Question> questions,
                                    Map<Long, List<SubmissionAnswer>> answersByQ,
                                    Map<Long, List<QuestionOption>> optsByQ,
                                    CellStyle header, CellStyle title, CellStyle num) {
        Set<QuestionType> likertTypes = Set.of(QuestionType.LIKERT, QuestionType.MATRIX_LIKERT);
        Sheet sheet = wb.createSheet("Análisis Likert");
        int rowNum = 0;

        Row t = sheet.createRow(rowNum++);
        cell(t, 0, "Análisis Likert · " + survey.getName(), title);
        rowNum++;

        Row hdr = sheet.createRow(rowNum++);
        cell(hdr, 0, "Pregunta / Ítem", header);
        cell(hdr, 1, "N", header);
        cell(hdr, 2, "Media", header);
        cell(hdr, 3, "Desv. Est.", header);
        cell(hdr, 4, "Mín", header);
        cell(hdr, 5, "Máx", header);

        for (Question q : questions) {
            if (!likertTypes.contains(q.getType())) continue;

            List<QuestionOption> opts = optsByQ.getOrDefault(q.getId(), List.of());
            Map<String, Double> numericByVal = opts.stream()
                    .filter(o -> o.getNumericValue() != null)
                    .collect(Collectors.toMap(QuestionOption::getValue,
                            o -> o.getNumericValue().doubleValue(), (a, b) -> a));
            Map<String, Double> numericByDisp = opts.stream()
                    .filter(o -> o.getNumericValue() != null)
                    .collect(Collectors.toMap(QuestionOption::getDisplayText,
                            o -> o.getNumericValue().doubleValue(), (a, b) -> a));

            List<Double> nums = answersByQ.getOrDefault(q.getId(), List.of()).stream()
                    .filter(a -> !a.isNotApplicable() && a.getNormalizedValue() != null)
                    .map(a -> {
                        Double v = numericByVal.get(a.getNormalizedValue());
                        return v != null ? v : numericByDisp.get(a.getNormalizedValue());
                    })
                    .filter(Objects::nonNull).toList();

            if (nums.isEmpty()) continue;

            double mean = nums.stream().mapToDouble(d -> d).average().orElse(0);
            double std = Math.sqrt(nums.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0));
            double min = nums.stream().mapToDouble(d -> d).min().orElse(0);
            double max = nums.stream().mapToDouble(d -> d).max().orElse(0);

            Row row = sheet.createRow(rowNum++);
            String txt = q.getText();
            cell(row, 0, txt.length() > 100 ? txt.substring(0, 100) + "…" : txt, null);
            numCell(row, 1, (long) nums.size(), null);
            numCell(row, 2, mean, num);
            numCell(row, 3, std, num);
            numCell(row, 4, min, num);
            numCell(row, 5, max, num);
        }

        sheet.setColumnWidth(0, 14000);
        for (int i = 1; i <= 5; i++) sheet.setColumnWidth(i, 3000);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    private void numCell(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private void numCell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        s.setFont(f);
        return s;
    }

    private CellStyle numStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("0.00"));
        return s;
    }
}
