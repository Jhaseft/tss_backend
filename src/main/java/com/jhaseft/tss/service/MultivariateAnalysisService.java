package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.MultivariateRequestDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO;
import com.jhaseft.tss.dto.MultivariateResultDTO.Cell;
import com.jhaseft.tss.dto.MultivariateResultDTO.Marginal;
import com.jhaseft.tss.dto.MultivariateResultDTO.Point;
import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.parsing.NumericAnswerParser;
import com.jhaseft.tss.service.stats.DescriptiveStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Análisis exploratorio multivariable del docente: dos cualitativas X, Y y una cuantitativa Z.
 * Por cada combinación X×Y informa n y los estadísticos de Z; también los marginales, el total y
 * los puntos individuales para la dispersión categorizada. El núcleo {@link #aggregate} es puro y
 * se prueba sin BD (caso del docente); {@link #analyze} arma los registros desde la encuesta.
 */
@Service
public class MultivariateAnalysisService {

    private static final double NUM_MIN = 0, NUM_MAX = 45;

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final NumericAnswerParser numericParser;

    public MultivariateAnalysisService(QuestionRepository questionRepo,
                                       QuestionOptionRepository optionRepo,
                                       SubmissionRepository submissionRepo,
                                       SubmissionAnswerRepository answerRepo,
                                       NumericAnswerParser numericParser) {
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.numericParser = numericParser;
    }

    /** Registro depurado: categoría de X, categoría de Y y valor cuantitativo Z. */
    public record Rec(String x, String y, double z) {}

    @Transactional(readOnly = true)
    public MultivariateResultDTO analyze(Long surveyId, MultivariateRequestDTO req) {
        Question qx = question(req.getQuestionIdX());
        Question qy = question(req.getQuestionIdY());
        Question qz = question(req.getQuestionIdZ());
        if (qz.getType() != QuestionType.NUMERIC)
            throw new IllegalArgumentException("La variable Z debe ser numérica.");

        List<Long> subIds = submissionRepo.findBySurveyId(surveyId)
                .stream().map(Submission::getId).toList();
        if (subIds.isEmpty()) throw new IllegalArgumentException("La encuesta no tiene respuestas.");

        Map<Long, SubmissionAnswer> ax = answersBySubmission(req.getQuestionIdX(), subIds);
        Map<Long, SubmissionAnswer> ay = answersBySubmission(req.getQuestionIdY(), subIds);
        Map<Long, SubmissionAnswer> az = answersBySubmission(req.getQuestionIdZ(), subIds);
        Map<Long, SubmissionAnswer> af = req.getFilterQuestionId() != null
                ? answersBySubmission(req.getFilterQuestionId(), subIds) : Map.of();

        Map<String, String> gX = req.getGroupingX() != null ? req.getGroupingX() : Map.of();
        Map<String, String> gY = req.getGroupingY() != null ? req.getGroupingY() : Map.of();
        Set<String> filterVals = req.getFilterValues() != null
                ? new HashSet<>(req.getFilterValues()) : null;

        List<Rec> recs = new ArrayList<>();
        Map<String, Integer> excluded = new LinkedHashMap<>();
        for (Long sid : subIds) {
            if (filterVals != null) {
                SubmissionAnswer fa = af.get(sid);
                if (fa == null || fa.getNormalizedValue() == null
                        || !filterVals.contains(fa.getNormalizedValue())) {
                    excluded.merge("FILTRADO", 1, Integer::sum);
                    continue;
                }
            }
            String x = category(ax.get(sid), gX);
            String y = category(ay.get(sid), gY);
            if (x == null) { excluded.merge("X_SIN_DATO", 1, Integer::sum); continue; }
            if (y == null) { excluded.merge("Y_SIN_DATO", 1, Integer::sum); continue; }
            SubmissionAnswer za = az.get(sid);
            NumericParseResultDTO z = za == null ? null
                    : numericParser.parse(za.getRawValue(), NUM_MIN, NUM_MAX);
            if (z == null || !z.isValid() || z.getNumericValue() == null) {
                excluded.merge("Z_INVALIDO", 1, Integer::sum);
                continue;
            }
            recs.add(new Rec(x, y, z.getNumericValue()));
        }

        MultivariateResultDTO out = aggregate(recs, scaleOrder(qx), scaleOrder(qy));
        out.setQuestionIdX(qx.getId()); out.setQuestionTextX(qx.getText());
        out.setQuestionIdY(qy.getId()); out.setQuestionTextY(qy.getText());
        out.setQuestionIdZ(qz.getId()); out.setQuestionTextZ(qz.getText());
        out.setExcluded(excluded);
        return out;
    }

    /** Núcleo puro: agrega los registros en celdas, marginales, total y puntos. */
    public MultivariateResultDTO aggregate(List<Rec> recs, List<String> xOrder, List<String> yOrder) {
        Map<String, Map<String, List<Double>>> byXY = new HashMap<>();
        Map<String, List<Double>> byX = new HashMap<>();
        Map<String, List<Double>> byY = new HashMap<>();
        List<Double> all = new ArrayList<>();
        for (Rec r : recs) {
            byXY.computeIfAbsent(r.x(), k -> new HashMap<>())
                    .computeIfAbsent(r.y(), k -> new ArrayList<>()).add(r.z());
            byX.computeIfAbsent(r.x(), k -> new ArrayList<>()).add(r.z());
            byY.computeIfAbsent(r.y(), k -> new ArrayList<>()).add(r.z());
            all.add(r.z());
        }
        int total = recs.size();

        List<String> xs = order(xOrder, byX);
        List<String> ys = order(yOrder, byY);

        List<Cell> cells = new ArrayList<>();
        for (String x : xs)
            for (String y : ys) {
                List<Double> v = byXY.getOrDefault(x, Map.of()).get(y);
                if (v == null || v.isEmpty()) continue;   // solo combinaciones observadas
                Cell c = new Cell();
                c.x = x; c.y = y; c.n = v.size(); c.pct = total > 0 ? v.size() * 100.0 / total : 0;
                fillZ(v, c);
                cells.add(c);
            }

        List<Marginal> mx = xs.stream().map(x -> marginal(x, byX.get(x), total)).toList();
        List<Marginal> my = ys.stream().map(y -> marginal(y, byY.get(y), total)).toList();

        List<Point> points = recs.stream().map(r -> {
            Point p = new Point(); p.x = r.x(); p.y = r.y(); p.z = r.z(); return p;
        }).toList();

        MultivariateResultDTO out = new MultivariateResultDTO();
        out.setXLabels(xs);
        out.setYLabels(ys);
        out.setCells(cells);
        out.setMarginalX(mx);
        out.setMarginalY(my);
        out.setTotal(marginal("Total", all, total));
        out.setPoints(points);
        out.setUsedN(total);
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Question question(Long id) {
        return questionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + id));
    }

    private Map<Long, SubmissionAnswer> answersBySubmission(Long qId, List<Long> subIds) {
        return answerRepo.findByQuestionIdAndSubmissionIdIn(qId, subIds).stream()
                .filter(a -> !a.isNotApplicable())
                .collect(Collectors.toMap(a -> a.getSubmission().getId(), a -> a, (a, b) -> a));
    }

    /** Categoría mostrada: etiqueta de grupo si el valor normalizado está mapeado; si no, el crudo. */
    private String category(SubmissionAnswer a, Map<String, String> grouping) {
        if (a == null) return null;
        String norm = a.getNormalizedValue();
        if (norm == null || norm.isBlank()) return null;
        String grouped = grouping.get(norm);
        if (grouped != null) return grouped;
        String raw = a.getRawValue();
        return raw != null && !raw.isBlank() ? raw.trim() : norm;
    }

    /** Orden de categorías: las ordinales siguen la escala (posición de opción); las nominales, por frecuencia. */
    private List<String> scaleOrder(Question q) {
        if (q.getType() == QuestionType.SINGLE_ORDINAL || q.getType() == QuestionType.LIKERT) {
            List<QuestionOption> opts = optionRepo.findByQuestionIdOrderByPosition(q.getId());
            if (!opts.isEmpty())
                return opts.stream().map(QuestionOption::getDisplayText).toList();
        }
        return null;   // nominal → se ordena por frecuencia en aggregate()
    }

    private List<String> order(List<String> preferred, Map<String, List<Double>> byCat) {
        List<String> present = new ArrayList<>(byCat.keySet());
        if (preferred != null && !preferred.isEmpty()) {
            List<String> out = preferred.stream().filter(present::contains).collect(Collectors.toList());
            present.stream().filter(s -> !out.contains(s))
                    .sorted(Comparator.comparingInt((String s) -> -byCat.get(s).size()).thenComparing(s -> s))
                    .forEach(out::add);
            return out;
        }
        present.sort(Comparator.comparingInt((String s) -> -byCat.get(s).size()).thenComparing(s -> s));
        return present;
    }

    private Marginal marginal(String label, List<Double> v, int total) {
        Marginal m = new Marginal();
        m.label = label;
        if (v == null || v.isEmpty()) { m.n = 0; return m; }
        m.n = v.size();
        m.pct = total > 0 ? v.size() * 100.0 / total : 0;
        List<Double> s = new ArrayList<>(v); Collections.sort(s);
        m.meanZ = s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        m.medianZ = DescriptiveStatsService.percentile(s, 0.5);
        m.stdDevZ = sampleSd(s, m.meanZ);
        return m;
    }

    private void fillZ(List<Double> v, Cell c) {
        List<Double> s = new ArrayList<>(v); Collections.sort(s);
        c.meanZ = s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        c.medianZ = DescriptiveStatsService.percentile(s, 0.5);
        c.minZ = s.get(0);
        c.maxZ = s.get(s.size() - 1);
        c.stdDevZ = sampleSd(s, c.meanZ);
    }

    private Double sampleSd(List<Double> v, double mean) {
        if (v.size() < 2) return null;
        double ss = 0;
        for (double x : v) ss += (x - mean) * (x - mean);
        return Math.sqrt(ss / (v.size() - 1));
    }
}
