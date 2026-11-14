package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.ChiSquareResultDTO;
import com.jhaseft.tss.dto.CrossTabResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.stats.ChiSquareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final ChiSquareService chiSquareService;

    public AnalysisService(QuestionRepository questionRepo,
                           QuestionOptionRepository optionRepo,
                           SubmissionRepository submissionRepo,
                           SubmissionAnswerRepository answerRepo,
                           ChiSquareService chiSquareService) {
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.chiSquareService = chiSquareService;
    }

    @Transactional(readOnly = true)
    public CrossTabResultDTO crossTab(Long surveyId, Long qId1, Long qId2) {
        Question q1 = questionRepo.findById(qId1)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + qId1));
        Question q2 = questionRepo.findById(qId2)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + qId2));

        if (qId1.equals(qId2)) throw new IllegalArgumentException("Las preguntas deben ser distintas.");

        List<Long> subIds = submissionRepo.findBySurveyId(surveyId)
                .stream().map(Submission::getId).toList();

        if (subIds.isEmpty()) throw new IllegalArgumentException("La encuesta no tiene respuestas.");

        // Answers per submission for each question
        Map<Long, String> ans1 = loadAnswerMap(qId1, subIds);
        Map<Long, String> ans2 = loadAnswerMap(qId2, subIds);

        // Labels from known options, fallback to distinct observed values
        List<QuestionOption> opts1 = optionRepo.findByQuestionIdOrderByPosition(qId1);
        List<QuestionOption> opts2 = optionRepo.findByQuestionIdOrderByPosition(qId2);

        List<String> rowLabels = labelsFor(opts1, ans1.values());
        List<String> colLabels = labelsFor(opts2, ans2.values());

        Map<String, Integer> rowIdx = buildIndex(opts1, rowLabels);
        Map<String, Integer> colIdx = buildIndex(opts2, colLabels);

        int R = rowLabels.size(), C = colLabels.size();
        long[][] obs = new long[R][C];

        for (Long sid : ans1.keySet()) {
            String v2 = ans2.get(sid);
            if (v2 == null) continue;
            Integer ri = rowIdx.get(ans1.get(sid));
            Integer ci = colIdx.get(v2);
            if (ri != null && ci != null) obs[ri][ci]++;
        }

        long n = Arrays.stream(obs).flatMapToLong(Arrays::stream).sum();

        // Row/col totals
        long[] rowTotals = new long[R];
        long[] colTotals = new long[C];
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++) {
                rowTotals[r] += obs[r][c];
                colTotals[c] += obs[r][c];
            }

        // Row percentages
        double[][] rowPct = new double[R][C];
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++)
                rowPct[r][c] = rowTotals[r] > 0 ? obs[r][c] * 100.0 / rowTotals[r] : 0.0;

        CrossTabResultDTO result = new CrossTabResultDTO();
        result.setQuestionId1(qId1);
        result.setQuestionText1(q1.getText());
        result.setQuestionId2(qId2);
        result.setQuestionText2(q2.getText());
        result.setRowLabels(rowLabels);
        result.setColLabels(colLabels);
        result.setObserved(obs);
        result.setRowPct(rowPct);
        result.setRowTotals(rowTotals);
        result.setColTotals(colTotals);
        result.setN(n);

        if (R < 2 || C < 2 || n == 0) {
            result.setWarning("Se necesitan al menos 2 categorías con respuestas en cada variable.");
            return result;
        }

        // χ², esperadas, Cochran, Fisher y V de Cramér salen de la misma tabla (punto 7).
        ChiSquareResultDTO chi = chiSquareService.test(obs);
        result.setChiSquare(chi.getChiSquare());
        result.setDegreesOfFreedom(chi.getDegreesOfFreedom());
        result.setPValue(chi.getPValue());
        result.setCramersV(chi.getCramersV());
        result.setCramersK(chi.getCramersK());
        result.setCramersVLevel(chi.getCramersVLevel());
        result.setExpected(chi.getExpected());
        result.setLowExpectedCells(chi.getLowExpectedCells());
        result.setTotalCells(chi.getTotalCells());
        result.setLowExpectedPct(chi.getLowExpectedPct());
        result.setAnyExpectedBelow1(chi.isAnyExpectedBelow1());
        result.setCochranOk(chi.isCochranOk());
        result.setFisherP(chi.getFisherP());
        result.setSignificant(chi.getPValue() < 0.05);
        if (!chi.isCochranOk())
            result.setWarning(String.format(
                    "Supuesto de Cochran no cumplido: %.0f%% de celdas con E < 5%s. Resultado orientativo%s.",
                    chi.getLowExpectedPct(),
                    chi.isAnyExpectedBelow1() ? " (alguna < 1)" : "",
                    chi.getFisherP() != null ? "; usar Fisher" : "; agrupar categorías"));
        return result;
    }

    private Map<Long, String> loadAnswerMap(Long qId, List<Long> subIds) {
        return answerRepo.findByQuestionIdAndSubmissionIdIn(qId, subIds).stream()
                .filter(a -> !a.isNotApplicable()
                        && a.getNormalizedValue() != null
                        && !a.getNormalizedValue().isBlank())
                .collect(Collectors.toMap(
                        a -> a.getSubmission().getId(),
                        SubmissionAnswer::getNormalizedValue,
                        (a, b) -> a));
    }

    private List<String> labelsFor(List<QuestionOption> opts, Collection<String> observed) {
        if (!opts.isEmpty()) return opts.stream().map(QuestionOption::getDisplayText).toList();
        return observed.stream().distinct().sorted().toList();
    }

    private Map<String, Integer> buildIndex(List<QuestionOption> opts, List<String> labels) {
        Map<String, Integer> idx = new HashMap<>();
        if (!opts.isEmpty()) {
            for (int i = 0; i < opts.size(); i++) {
                idx.put(opts.get(i).getValue(), i);
                idx.put(opts.get(i).getDisplayText(), i);
            }
        } else {
            for (int i = 0; i < labels.size(); i++) idx.put(labels.get(i), i);
        }
        return idx;
    }
}
