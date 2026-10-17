package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.HistogramBinDTO;
import com.jhaseft.tss.dto.SimulationResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;

    public SimulationService(QuestionRepository questionRepo,
                              QuestionOptionRepository optionRepo,
                              SubmissionRepository submissionRepo,
                              SubmissionAnswerRepository answerRepo) {
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
    }

    @Transactional(readOnly = true)
    public SimulationResultDTO bootstrap(Long surveyId, Long questionId, int iterations) {
        iterations = Math.min(Math.max(iterations, 200), 10000);

        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Question not found"));

        List<Long> subIds = submissionRepo.findBySurveyId(surveyId)
                .stream().map(Submission::getId).toList();

        List<String> values = answerRepo.findByQuestionIdAndSubmissionIdIn(questionId, subIds)
                .stream()
                .filter(a -> !a.isNotApplicable() && a.getNormalizedValue() != null
                        && !a.getNormalizedValue().isBlank())
                .map(SubmissionAnswer::getNormalizedValue)
                .toList();

        if (values.isEmpty()) throw new IllegalArgumentException("No hay respuestas para esta pregunta.");

        SimulationResultDTO result = new SimulationResultDTO();
        result.setQuestionText(q.getText());
        result.setQuestionType(q.getType().name());
        result.setIterations(iterations);
        result.setN(values.size());

        boolean isNumeric = q.getType() == QuestionType.NUMERIC
                || q.getType() == QuestionType.LIKERT
                || q.getType() == QuestionType.MATRIX_LIKERT;

        List<QuestionOption> opts = optionRepo.findByQuestionIdOrderByPosition(questionId);

        if (isNumeric || isLikertNumeric(values, opts)) {
            double[] nums = toNumericArray(values, opts);
            if (nums.length == 0) throw new IllegalArgumentException("No hay valores numéricos.");
            bootstrapMean(result, nums, iterations);
        } else {
            bootstrapProportions(result, values, opts, iterations);
        }

        return result;
    }

    // ── Bootstrap mean ────────────────────────────────────────────────────────

    private void bootstrapMean(SimulationResultDTO result, double[] data, int B) {
        int n = data.length;
        double[] boots = new double[B];
        Random rng = new Random();

        for (int b = 0; b < B; b++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += data[rng.nextInt(n)];
            boots[b] = sum / n;
        }
        Arrays.sort(boots);

        double mean = Arrays.stream(data).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(data).map(d -> Math.pow(d - mean, 2)).average().orElse(0));

        result.setSimulationType("BOOTSTRAP_MEAN");
        result.setEstimate(mean);
        result.setCiLow95(boots[(int) (0.025 * B)]);
        result.setCiHigh95(boots[(int) (0.975 * B)]);
        result.setStdError(std / Math.sqrt(n));
        result.setHistogram(toHistogram(boots, 20));
    }

    // ── Bootstrap proportions ─────────────────────────────────────────────────

    private void bootstrapProportions(SimulationResultDTO result,
                                       List<String> values,
                                       List<QuestionOption> opts,
                                       int B) {
        List<String> labels = opts.isEmpty()
                ? values.stream().distinct().sorted().limit(10).toList()
                : opts.stream().map(QuestionOption::getDisplayText).toList();

        Map<String, String> dispToVal = opts.stream()
                .collect(Collectors.toMap(QuestionOption::getDisplayText,
                        QuestionOption::getValue, (a, b2) -> a));

        int n = values.size();
        Random rng = new Random();
        List<SimulationResultDTO.OptionBootstrapDTO> optResults = new ArrayList<>();

        for (String label : labels) {
            String targetVal = dispToVal.getOrDefault(label, label.toLowerCase().trim());
            double[] obs = values.stream()
                    .mapToDouble(v -> (v.equalsIgnoreCase(targetVal) || v.equalsIgnoreCase(label)) ? 1.0 : 0.0)
                    .toArray();

            double[] boots = new double[B];
            for (int b = 0; b < B; b++) {
                double sum = 0;
                for (int i = 0; i < n; i++) sum += obs[rng.nextInt(n)];
                boots[b] = sum / n;
            }
            Arrays.sort(boots);

            double est = Arrays.stream(obs).average().orElse(0);
            optResults.add(new SimulationResultDTO.OptionBootstrapDTO(
                    label, est,
                    boots[(int) (0.025 * B)],
                    boots[(int) (0.975 * B)],
                    toHistogram(boots, 15)));
        }

        result.setSimulationType("BOOTSTRAP_PROPORTION");
        result.setOptionResults(optResults);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isLikertNumeric(List<String> values, List<QuestionOption> opts) {
        return opts.stream().anyMatch(o -> o.getNumericValue() != null);
    }

    private double[] toNumericArray(List<String> values, List<QuestionOption> opts) {
        Map<String, Double> byVal = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getValue,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));
        Map<String, Double> byDisp = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getDisplayText,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));

        return values.stream().mapToDouble(v -> {
            if (byVal.containsKey(v)) return byVal.get(v);
            if (byDisp.containsKey(v)) return byDisp.get(v);
            try { return Double.parseDouble(v.replace(",", ".")); } catch (Exception e) { return Double.NaN; }
        }).filter(d -> !Double.isNaN(d)).toArray();
    }

    private List<HistogramBinDTO> toHistogram(double[] sorted, int bins) {
        if (sorted.length == 0) return List.of();
        double min = sorted[0], max = sorted[sorted.length - 1];
        if (max == min) return List.of(new HistogramBinDTO(min, sorted.length));
        double width = (max - min) / bins;
        int[] counts = new int[bins];
        for (double v : sorted) {
            int idx = Math.min((int) ((v - min) / width), bins - 1);
            counts[idx]++;
        }
        List<HistogramBinDTO> hist = new ArrayList<>();
        for (int i = 0; i < bins; i++) {
            if (counts[i] > 0)
                hist.add(new HistogramBinDTO(min + width * (i + 0.5), counts[i]));
        }
        return hist;
    }
}
