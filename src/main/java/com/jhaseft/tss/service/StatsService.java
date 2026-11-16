package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.*;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.parsing.NumericAnswerParser;
import com.jhaseft.tss.service.stats.DescriptiveStatsService;
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
    private final QuestionInferenceService inference;
    private final NumericAnswerParser numericParser;
    private final DescriptiveStatsService descriptiveStats;
    private final KeywordExtractor keywordExtractor;

    // Rango aceptado para las preguntas numéricas (vida profesional / desempleo): 0–45 años.
    private static final double NUM_MIN = 0, NUM_MAX = 45;

    public StatsService(SurveyRepository surveyRepo,
                        QuestionRepository questionRepo,
                        QuestionOptionRepository optionRepo,
                        SubmissionRepository submissionRepo,
                        SubmissionAnswerRepository answerRepo,
                        QuestionInferenceService inference,
                        NumericAnswerParser numericParser,
                        DescriptiveStatsService descriptiveStats,
                        KeywordExtractor keywordExtractor) {
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.inference = inference;
        this.numericParser = numericParser;
        this.descriptiveStats = descriptiveStats;
        this.keywordExtractor = keywordExtractor;
    }

    @Transactional(readOnly = true)
    public SurveyStatsDTO compute(Long surveyId) {
        Survey survey = surveyRepo.findById(surveyId)
                .orElseThrow(() -> new NoSuchElementException("Survey not found: " + surveyId));

        List<Submission> submissions = submissionRepo.findBySurveyId(surveyId);
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();

        // Se incluyen TODAS las preguntas de la encuesta en el orden de la encuesta
        // (por posición), sin ocultar las marcadas como datos personales, para que el
        // dashboard muestre la encuesta completa.
        List<Question> questions = questionRepo
                .findBySurveyIdOrderByPosition(surveyId);
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
                        optsByQuestion.getOrDefault(q.getId(), List.of()),
                        survey.getOrigin(),
                        submissions.size()));
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
                                            List<QuestionOption> opts,
                                            SurveyOrigin origin,
                                            int totalSubmissions) {
        QuestionStatsDTO dto = new QuestionStatsDTO();
        dto.setQuestionId(q.getId());
        dto.setText(q.getText());
        dto.setType(q.getType().name());
        dto.setSection(q.getSection());
        dto.setPosition(q.getPosition());

        List<String> values = answered(answers);
        dto.setTotalAnswered(values.size());
        // Sin respuesta: quienes no dieron ningún valor. La separación fina entre filtro
        // condicional y "debía responder y no lo hizo" corresponde al reporte de depuración
        // (punto 3); aquí es el total de no respondidas de la pregunta.
        dto.setNoResponseCount(Math.max(0, totalSubmissions - values.size()));

        switch (q.getType()) {
            case SINGLE_NOMINAL, SINGLE_ORDINAL ->
                // % sobre el n válido (respuestas que caen en una opción) → suman 100%.
                dto.setDistribution(freqTable(values, opts, matchedTotal(values, opts)));
            case MULTIPLE_CHOICE -> {
                // Usa el mismo separador que el import (respeta comas dentro de
                // paréntesis en Google y ';' en MS Forms) para no partir opciones como
                // "Técnico (desarrollo de software, administración...)" y dar 0%.
                // El denominador es el nº de encuestados (no la suma de opciones), porque
                // una persona puede marcar varias → los % no suman 100.
                List<String> exploded = values.stream()
                        .flatMap(v -> inference.splitMultiOptions(v, origin).stream())
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                dto.setDistribution(freqTable(exploded, opts, values.size()));
            }
            case LIKERT -> {
                dto.setDistribution(freqTable(values, opts, matchedTotal(values, opts)));
                dto.setMean(meanFromOpts(values, opts));
                dto.setStdDev(stdDevFromOpts(values, opts));
            }
            case NUMERIC -> buildNumeric(dto, rawAnswered(answers));
            case TEXT_LONG -> {
                dto.setSampleResponses(values.stream().limit(10).toList());
                dto.setKeywords(extractKeywords(values));
            }
            default -> dto.setSampleResponses(values.stream().limit(10).toList());
        }
        return dto;
    }

    // Palabras clave de preguntas abiertas (TEXT_LONG): se delega en KeywordExtractor, que
    // mantiene el vocabulario común y permite comparar el texto libre entre encuestas.
    private List<FrequencyEntryDTO> extractKeywords(List<String> values) {
        return keywordExtractor.extract(values, 12);
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
            item.setDistribution(freqTable(values, opts, matchedTotal(values, opts)));
            return item;
        }).toList();

        dto.setMatrixItems(items);
        dto.setTotalAnswered(items.stream()
                .mapToInt(i -> i.getDistribution() == null ? 0 :
                        i.getDistribution().stream().mapToInt(e -> e.getCount().intValue()).sum())
                .max().orElse(0));
        return dto;
    }

    // Estadística numérica robusta: cada crudo pasa por el NumericAnswerParser (convierte
    // "5 años", "6 meses", "1,5", "Nada"; descarta "Gjvu", vacíos y fuera de rango). n = nº
    // de valores válidos; varianza y DE MUESTRALES (n − 1) como pide el informe.
    private void buildNumeric(QuestionStatsDTO dto, List<String> rawValues) {
        List<Double> nums = new ArrayList<>();
        int invalid = 0;
        for (String raw : rawValues) {
            NumericParseResultDTO r = numericParser.parse(raw, NUM_MIN, NUM_MAX);
            if (r.isValid() && r.getNumericValue() != null) nums.add(r.getNumericValue());
            else invalid++;
        }
        Collections.sort(nums);

        dto.setTotalAnswered(nums.size());                 // n = válidos (p.ej. 180)
        dto.setNoResponseCount(invalid);                    // crudos no interpretables / fuera de rango
        if (nums.isEmpty()) return;

        // Estadística descriptiva completa (percentiles, Tukey, asimetría/curtosis, Shapiro-Wilk).
        DescriptiveStatsDTO d = descriptiveStats.compute(nums);
        dto.setDescriptive(d);

        // Campos resumidos que ya consume el dashboard; la mediana por interpolación lineal
        // (QUARTILE.INC) es la que usa el informe (criterio B).
        dto.setMean(d.getMean());
        dto.setStdDev(d.getStdDev());
        dto.setMin(d.getMin());
        dto.setMax(d.getMax());
        dto.setMedian(d.getMedian());
    }

    private List<String> answered(List<SubmissionAnswer> answers) {
        return answers.stream()
                .filter(a -> !a.isNotApplicable()
                        && a.getNormalizedValue() != null
                        && !a.getNormalizedValue().isBlank())
                .map(SubmissionAnswer::getNormalizedValue)
                .toList();
    }

    // Valores CRUDOS de las respuestas dadas (sin normalizar), para que el parser numérico
    // vea el texto original ("1,5", "5 años") antes de cualquier limpieza.
    private List<String> rawAnswered(List<SubmissionAnswer> answers) {
        return answers.stream()
                .filter(a -> !a.isNotApplicable()
                        && a.getRawValue() != null
                        && !a.getRawValue().isBlank())
                .map(SubmissionAnswer::getRawValue)
                .toList();
    }

    // n válido de una pregunta de opción: respuestas que caen en alguna opción conocida.
    // Es el denominador correcto para que los % de opción única sumen 100%.
    private int matchedTotal(List<String> values, List<QuestionOption> opts) {
        if (opts.isEmpty()) return values.size();
        Set<String> keys = new HashSet<>();
        for (QuestionOption o : opts) { keys.add(o.getValue()); keys.add(o.getDisplayText()); }
        return (int) values.stream().filter(keys::contains).count();
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
