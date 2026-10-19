package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.*;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;

    public StatsService(SurveyRepository surveyRepo,
                        QuestionRepository questionRepo,
                        QuestionOptionRepository optionRepo,
                        SubmissionRepository submissionRepo,
                        SubmissionAnswerRepository answerRepo) {
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
    }

    @Transactional(readOnly = true)
    public SurveyStatsDTO compute(Long surveyId) {
        Survey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new NoSuchElementException("Survey not found: " + surveyId));

        List<Submission> submissions = submissionRepo.findBySurveyId(surveyId);
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();

        List<Question> questions = questionRepo
                .findBySurveyIdAndPersonalDataFalseOrderByPosition(surveyId);
        List<Long> questionIds = questions.stream().map(Question::getId).toList();

        Map<Long, List<SubmissionAnswer>> answersByQuestion = submissionIds.isEmpty()
                ? Map.of()
                : answerRepo.findBySubmissionIdIn(submissionIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getQuestion().getId()));

        Map<Long, List<QuestionOption>> optsByQuestion = questionIds.isEmpty()
                ? Map.of()
                : optionRepo.findByQuestionIdIn(questionIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        Map<String, List<Question>> matrixGroups = questions.stream()
                .filter(q -> q.getType() == QuestionType.MATRIX_LIKERT && q.getMatrixGroup() != null)
                .collect(Collectors.groupingBy(Question::getMatrixGroup,
                        LinkedHashMap::new, Collectors.toList()));

        Set<Long> processed = new HashSet<>();
        List<QuestionStatsDTO> stats = new ArrayList<>();

        for (Question q : questions) {
            if (processed.contains(q.getId())) continue;

            if (q.getType() == QuestionType.MATRIX_LIKERT && q.getMatrixGroup() != null) {
                List<Question> group = matrixGroups.getOrDefault(q.getMatrixGroup(), List.of(q));
                group.forEach(gq -> processed.add(gq.getId()));
                stats.add(buildMatrix(q.getMatrixGroup(), group, answersByQuestion, optsByQuestion));
            } else {
                processed.add(q.getId());
                stats.add(buildQuestion(q,
                        answersByQuestion.getOrDefault(q.getId(), List.of()),
                        optsByQuestion.getOrDefault(q.getId(), List.of())));
            }
        }

        SurveyStatsDTO dto = new SurveyStatsDTO();
        dto.setSurveyId(survey.getId());
        dto.setSurveyName(survey.getName());
        dto.setOrigin(survey.getOrigin().name());
        dto.setTotalResponses((long) submissions.size());
        dto.setQuestions(stats);
        return dto;
    }

    private QuestionStatsDTO buildQuestion(Question q,
                                            List<SubmissionAnswer> answers,
                                            List<QuestionOption> opts) {
        QuestionStatsDTO dto = new QuestionStatsDTO();
        dto.setQuestionId(q.getId());
        dto.setText(q.getText());
        dto.setType(q.getType().name());
        dto.setSection(q.getSection());
        dto.setPosition(q.getPosition());

        List<String> values = answered(answers);
        dto.setTotalAnswered(values.size());

        switch (q.getType()) {
            case SINGLE_NOMINAL, SINGLE_ORDINAL ->
                dto.setDistribution(freqTable(values, opts, values.size()));
            case MULTIPLE_CHOICE -> {
                List<String> exploded = values.stream()
                        .flatMap(v -> Arrays.stream(v.split("[;,]\\s*")))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                dto.setDistribution(freqTable(exploded, opts, values.size()));
            }
            case LIKERT -> {
                dto.setDistribution(freqTable(values, opts, values.size()));
                dto.setMean(meanFromOpts(values, opts));
                dto.setStdDev(stdDevFromOpts(values, opts));
            }
            case NUMERIC -> buildNumeric(dto, values);
            default -> dto.setSampleResponses(values.stream().limit(10).toList());
        }
        return dto;
    }

    private QuestionStatsDTO buildMatrix(String groupName,
                                          List<Question> group,
                                          Map<Long, List<SubmissionAnswer>> answersByQ,
                                          Map<Long, List<QuestionOption>> optsByQ) {
        QuestionStatsDTO dto = new QuestionStatsDTO();
        dto.setType(QuestionType.MATRIX_LIKERT.name());
        dto.setText(groupName);
        dto.setSection(group.get(0).getSection());
        dto.setPosition(group.get(0).getPosition());
        dto.setQuestionId(group.get(0).getId());

        List<MatrixItemStatsDTO> items = group.stream().map(q -> {
            List<String> values = answered(answersByQ.getOrDefault(q.getId(), List.of()));
            List<QuestionOption> opts = optsByQ.getOrDefault(q.getId(), List.of());
            MatrixItemStatsDTO item = new MatrixItemStatsDTO();
            item.setQuestionId(q.getId());
            item.setText(q.getText());
            item.setMean(meanFromOpts(values, opts));
            item.setStdDev(stdDevFromOpts(values, opts));
            item.setDistribution(freqTable(values, opts, values.size()));
            return item;
        }).toList();

        dto.setMatrixItems(items);
        dto.setTotalAnswered(items.stream()
                .mapToInt(i -> i.getDistribution() == null ? 0 :
                        i.getDistribution().stream().mapToInt(e -> e.getCount().intValue()).sum())
                .max().orElse(0));
        return dto;
    }

    private void buildNumeric(QuestionStatsDTO dto, List<String> values) {
        List<Double> nums = values.stream()
                .map(v -> { try { return Double.parseDouble(v.replace(",", ".")); } catch (Exception e) { return null; } })
                .filter(Objects::nonNull).sorted().toList();
        if (nums.isEmpty()) return;
        double mean = nums.stream().mapToDouble(d -> d).average().orElse(0);
        dto.setMean(mean);
        dto.setStdDev(Math.sqrt(nums.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0)));
        dto.setMin(nums.get(0));
        dto.setMax(nums.get(nums.size() - 1));
        int mid = nums.size() / 2;
        dto.setMedian(nums.size() % 2 == 0 ? (nums.get(mid - 1) + nums.get(mid)) / 2.0 : nums.get(mid));
    }

    private List<String> answered(List<SubmissionAnswer> answers) {
        return answers.stream()
                .filter(a -> !a.isNotApplicable()
                        && a.getNormalizedValue() != null
                        && !a.getNormalizedValue().isBlank())
                .map(SubmissionAnswer::getNormalizedValue)
                .toList();
    }

    private List<FrequencyEntryDTO> freqTable(List<String> values,
                                               List<QuestionOption> opts,
                                               int total) {
        Map<String, Long> counts = values.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (!opts.isEmpty()) {
            return opts.stream().map(opt -> {
                long c = counts.getOrDefault(opt.getValue(), 0L);
                if (!opt.getDisplayText().equals(opt.getValue()))
                    c += counts.getOrDefault(opt.getDisplayText(), 0L);
                FrequencyEntryDTO e = new FrequencyEntryDTO();
                e.setLabel(opt.getDisplayText());
                e.setCount(c);
                e.setPercentage(total > 0 ? c * 100.0 / total : 0.0);
                if (opt.getNumericValue() != null)
                    e.setNumericValue(opt.getNumericValue().doubleValue());
                return e;
            }).toList();
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(en -> {
                    FrequencyEntryDTO e = new FrequencyEntryDTO();
                    e.setLabel(en.getKey());
                    e.setCount(en.getValue());
                    e.setPercentage(total > 0 ? en.getValue() * 100.0 / total : 0.0);
                    return e;
                }).toList();
    }

    private Double meanFromOpts(List<String> values, List<QuestionOption> opts) {
        if (opts.isEmpty() || values.isEmpty()) return null;
        Map<String, Double> byVal = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getValue,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));
        Map<String, Double> byDisp = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getDisplayText,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));
        OptionalDouble avg = values.stream()
                .map(v -> byVal.containsKey(v) ? byVal.get(v) : byDisp.get(v))
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    // Desviación estándar poblacional de los valores Likert mapeados a su número.
    private Double stdDevFromOpts(List<String> values, List<QuestionOption> opts) {
        if (opts.isEmpty() || values.isEmpty()) return null;
        Map<String, Double> byVal = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getValue,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));
        Map<String, Double> byDisp = opts.stream().filter(o -> o.getNumericValue() != null)
                .collect(Collectors.toMap(QuestionOption::getDisplayText,
                        o -> o.getNumericValue().doubleValue(), (a, b) -> a));
        double[] nums = values.stream()
                .map(v -> byVal.containsKey(v) ? byVal.get(v) : byDisp.get(v))
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        if (nums.length < 2) return null;
        double mean = Arrays.stream(nums).average().orElse(0);
        double var = Arrays.stream(nums).map(d -> Math.pow(d - mean, 2)).average().orElse(0);
        return Math.sqrt(var);
    }
}
