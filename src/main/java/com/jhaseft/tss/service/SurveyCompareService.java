package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.CompareResultDTO;
import com.jhaseft.tss.dto.CompareResultDTO.Row;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import com.jhaseft.tss.service.stats.WilsonInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Comparación descriptiva entre dos encuestas independientes (p. ej. titulados vs empleadores):
 * "qué buscan los empleadores en los titulados". NO es un cruce per-encuestado (poblaciones distintas,
 * sin clave de unión); son dos distribuciones separadas con su IC de Wilson 95 %. Dos modos:
 *  - CATEGORIES: alinea las categorías por etiqueta normalizada (mismo constructo, p. ej. medio de
 *    reclutamiento del empleador vs medio por el que el titulado obtuvo empleo).
 *  - KEYWORDS: compara la prevalencia de temas en texto libre sobre un vocabulario común
 *    (IA, Ciberseguridad, Datos, …), ideal para áreas/competencias que se demandan vs las que se tienen.
 */
@Service
public class SurveyCompareService {

    private static final String DISCLAIMER =
            "Comparación de dos muestras independientes (encuestados distintos): no es un cruce ni una "
            + "prueba χ². Los porcentajes se presentan con IC de Wilson 95 % referencial (muestreo no "
            + "probabilístico). Las diferencias son descriptivas, no inferenciales.";

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final QuestionInferenceService inference;
    private final KeywordExtractor keywordExtractor;

    public SurveyCompareService(QuestionRepository questionRepo,
                                QuestionOptionRepository optionRepo,
                                SubmissionRepository submissionRepo,
                                SubmissionAnswerRepository answerRepo,
                                QuestionInferenceService inference,
                                KeywordExtractor keywordExtractor) {
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.inference = inference;
        this.keywordExtractor = keywordExtractor;
    }

    @Transactional(readOnly = true)
    public CompareResultDTO compare(Long questionIdA, Long questionIdB, String mode) {
        Question qa = question(questionIdA);
        Question qb = question(questionIdB);
        boolean keywords = "KEYWORDS".equalsIgnoreCase(mode);

        Dist a = keywords ? keywordDist(qa) : categoryDist(qa);
        Dist b = keywords ? keywordDist(qb) : categoryDist(qb);

        // Eje común: unión de etiquetas, ordenada por el conteo combinado (desc).
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(a.counts.keySet());
        keys.addAll(b.counts.keySet());
        List<String> ordered = new ArrayList<>(keys);
        ordered.sort((x, y) -> Long.compare(
                b.counts.getOrDefault(y, 0L) + a.counts.getOrDefault(y, 0L),
                b.counts.getOrDefault(x, 0L) + a.counts.getOrDefault(x, 0L)));

        List<Row> rows = new ArrayList<>();
        for (String key : ordered) {
            long ca = a.counts.getOrDefault(key, 0L), cb = b.counts.getOrDefault(key, 0L);
            WilsonInterval.Result wa = WilsonInterval.of(ca, a.n);
            WilsonInterval.Result wb = WilsonInterval.of(cb, b.n);
            Row r = new Row();
            r.label = a.labels.getOrDefault(key, b.labels.getOrDefault(key, key));
            r.countA = ca; r.pctA = wa.pct(); r.ciLowA = wa.low(); r.ciHighA = wa.high();
            r.countB = cb; r.pctB = wb.pct(); r.ciLowB = wb.low(); r.ciHighB = wb.high();
            r.gap = r.pctA - r.pctB;
            rows.add(r);
        }

        CompareResultDTO out = new CompareResultDTO();
        out.setMode(keywords ? "KEYWORDS" : "CATEGORIES");
        out.setSurveyIdA(qa.getSurvey().getId());
        out.setSurveyNameA(qa.getSurvey().getName());
        out.setQuestionIdA(qa.getId());
        out.setQuestionTextA(qa.getText());
        out.setNA(a.n);
        out.setSurveyIdB(qb.getSurvey().getId());
        out.setSurveyNameB(qb.getSurvey().getName());
        out.setQuestionIdB(qb.getId());
        out.setQuestionTextB(qb.getText());
        out.setNB(b.n);
        out.setRows(rows);
        out.setDisclaimer(DISCLAIMER);
        return out;
    }

    // ── Distribuciones ─────────────────────────────────────────────────────────

    /** n de encuestados que respondieron, conteo por clave y etiqueta a mostrar. */
    private record Dist(int n, Map<String, Long> counts, Map<String, String> labels) {}

    private Dist categoryDist(Question q) {
        List<Long> subIds = subIds(q);
        List<QuestionOption> opts = optionRepo.findByQuestionIdOrderByPosition(q.getId());
        Map<String, String> labels = new HashMap<>();
        for (QuestionOption o : opts) labels.put(o.getValue(), o.getDisplayText());

        SurveyOrigin origin = q.getSurvey().getOrigin();
        boolean multiple = q.getType() == QuestionType.MULTIPLE_CHOICE;

        Map<String, Long> counts = new HashMap<>();
        int n = 0;
        for (SubmissionAnswer ans : answerRepo.findByQuestionIdAndSubmissionIdIn(q.getId(), subIds)) {
            if (ans.isNotApplicable() || ans.getNormalizedValue() == null
                    || ans.getNormalizedValue().isBlank()) continue;
            n++;
            Set<String> keys = new LinkedHashSet<>();
            if (multiple) {
                for (String part : inference.splitMultiOptions(ans.getRawValue(), origin)) {
                    String k = inference.normalize(part);
                    if (!k.isBlank()) keys.add(k);
                }
            } else {
                keys.add(ans.getNormalizedValue());
            }
            for (String k : keys) {
                counts.merge(k, 1L, Long::sum);
                labels.putIfAbsent(k, k);   // etiqueta de respaldo si no hay opción declarada
            }
        }
        return new Dist(n, counts, labels);
    }

    private Dist keywordDist(Question q) {
        List<Long> subIds = subIds(q);
        List<String> values = new ArrayList<>();
        for (SubmissionAnswer ans : answerRepo.findByQuestionIdAndSubmissionIdIn(q.getId(), subIds)) {
            if (ans.isNotApplicable()) continue;
            String v = ans.getNormalizedValue();
            if (v != null && !v.isBlank()) values.add(v);
        }
        Map<String, Long> counts = keywordExtractor.counts(values, false);
        Map<String, String> labels = new HashMap<>();
        counts.keySet().forEach(k -> labels.put(k, k));
        return new Dist(values.size(), counts, labels);
    }

    private List<Long> subIds(Question q) {
        return submissionRepo.findBySurveyId(q.getSurvey().getId())
                .stream().map(Submission::getId).toList();
    }

    private Question question(Long id) {
        return questionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + id));
    }
}
