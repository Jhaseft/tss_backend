package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.*;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final QuestionInferenceService inference;

    public StatsService(SurveyRepository surveyRepo,
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
                        optsByQuestion.getOrDefault(q.getId(), List.of()),
                        survey.getOrigin()));
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
                                            SurveyOrigin origin) {
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
                // Usa el mismo separador que el import (respeta comas dentro de
                // paréntesis en Google y ';' en MS Forms) para no partir opciones como
                // "Técnico (desarrollo de software, administración...)" y dar 0%.
                List<String> exploded = values.stream()
                        .flatMap(v -> inference.splitMultiOptions(v, origin).stream())
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                dto.setDistribution(freqTable(exploded, opts, values.size()));
            }
            case LIKERT -> {
                dto.setDistribution(freqTable(values, opts, values.size()));
                dto.setMean(meanFromOpts(values, opts));
                dto.setStdDev(stdDevFromOpts(values, opts));
            }
            case NUMERIC -> buildNumeric(dto, values);
            case TEXT_LONG -> {
                dto.setSampleResponses(values.stream().limit(10).toList());
                dto.setKeywords(extractKeywords(values));
            }
            default -> dto.setSampleResponses(values.stream().limit(10).toList());
        }
        return dto;
    }

    // ── Extracción de palabras clave para preguntas abiertas (TEXT_LONG) ──────
    // Agrupa menciones por tema (sinónimos) y cuenta cuántas RESPUESTAS mencionan
    // cada tema. Sirve para visualizar tendencias en texto libre (áreas, herramientas,
    // competencias) sin leer una por una. Los valores ya vienen normalizados
    // (minúsculas y sin tildes), por eso los patrones se escriben así.
    private static final Map<String, Pattern> KEYWORD_GROUPS = new LinkedHashMap<>();
    static {
        KEYWORD_GROUPS.put("IA / Agentes", Pattern.compile("\\bia\\b|\\bai\\b|inteligencia artif|inteligente artif|machine learning|maching learning|\\bmlops\\b|\\bml\\b|\\bllm|prompt|agentic|agentes|deep learning|redes neuronales"));
        KEYWORD_GROUPS.put("Ciberseguridad", Pattern.compile("ciberseguridad|ciber seguridad|seguridad inform|seguridad de sistemas|seguridad|forense|forence|pentest|hacking"));
        KEYWORD_GROUPS.put("Ciencia de Datos", Pattern.compile("ciencia de datos|analisis de datos|analisis se datos|big data|data warehouse|ingenier[oa]s de datos|analitica|\\bdatos\\b|\\bbi\\b|business intelligence|power bi|tableau"));
        KEYWORD_GROUPS.put("Automatización / IoT", Pattern.compile("automatizacion|\\biot\\b|internet de las cosas|\\brpa\\b|robotica"));
        KEYWORD_GROUPS.put("Cloud", Pattern.compile("\\bcloud\\b|\\bnube\\b|computacion en la nube|\\baws\\b|\\bazure\\b|\\bgcp\\b"));
        KEYWORD_GROUPS.put("DevOps", Pattern.compile("devops|docker|kubernetes|ci/cd|jenkins|terraform"));
        KEYWORD_GROUPS.put("Desarrollo Web/Móvil", Pattern.compile("\\breact\\b|angular|\\bvue\\b|node|flutter|desarrollo web|desarrollo movil|programacion movil|\\bapi\\b|microservicios|laravel|spring|\\bnest\\b"));
        KEYWORD_GROUPS.put("Bases de Datos", Pattern.compile("base de datos|bases de datos|\\bsql\\b|postgres|mongo"));
        KEYWORD_GROUPS.put("Lenguajes de programación", Pattern.compile("\\bpython\\b|\\bjava\\b|javascript|\\bc\\+\\+|\\bc#|\\bgo\\b"));
        KEYWORD_GROUPS.put("Blockchain", Pattern.compile("blockchain|solidity|web3"));
        KEYWORD_GROUPS.put("Trabajo en equipo", Pattern.compile("trabajo en equipo|colaboracion|equipos multidisc"));
        KEYWORD_GROUPS.put("Comunicación", Pattern.compile("comunicacion"));
        KEYWORD_GROUPS.put("Resolución de problemas", Pattern.compile("resolucion de problemas|solucion de problemas|pensamiento critico|analitic"));
        KEYWORD_GROUPS.put("Liderazgo / Gestión", Pattern.compile("liderazgo|gestion de proyectos|\\blider"));
        KEYWORD_GROUPS.put("Aprendizaje continuo", Pattern.compile("aprendizaje continuo|adaptab|actualizacion|autoaprendizaje"));
        KEYWORD_GROUPS.put("Inglés", Pattern.compile("\\bingles\\b|idioma"));
        KEYWORD_GROUPS.put("Habilidades blandas", Pattern.compile("habilidades blandas|soft skills|competencias blandas"));
    }

    private List<FrequencyEntryDTO> extractKeywords(List<String> values) {
        if (values.isEmpty()) return List.of();
        int total = values.size();
        List<FrequencyEntryDTO> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> g : KEYWORD_GROUPS.entrySet()) {
            long c = values.stream().filter(v -> g.getValue().matcher(v).find()).count();
            if (c > 0) {
                FrequencyEntryDTO f = new FrequencyEntryDTO();
                f.setLabel(g.getKey());
                f.setCount(c);
                f.setPercentage(c * 100.0 / total);
                out.add(f);
            }
        }
        out.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
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
