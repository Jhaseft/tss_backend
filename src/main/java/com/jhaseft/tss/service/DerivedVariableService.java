package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.model.Question;
import com.jhaseft.tss.model.QuestionType;
import com.jhaseft.tss.model.SubmissionAnswer;
import com.jhaseft.tss.repository.QuestionRepository;
import com.jhaseft.tss.repository.SubmissionAnswerRepository;
import com.jhaseft.tss.repository.SubmissionAnswerOptionRepository;
import com.jhaseft.tss.service.parsing.NumericAnswerParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Variables derivadas (recodificaciones) disponibles para el análisis de asociación, definidas en
 * código (no en BD). Hoy: el <b>segmento de experiencia</b> a partir de "años de vida profesional"
 * (NUMERIC, criterio B): Junior (≤5), Intermedio (6–10), Consolidado (&gt;10). Coincide con los
 * cruces de referencia del Informe 1 (Segmento × … , n = 180).
 *
 * El id de una variable derivada es el <b>negativo</b> del id de su pregunta fuente, para no chocar
 * con los ids reales (positivos) en el mismo selector del frontend.
 */
@Service
public class DerivedVariableService {

    private static final double NUM_MIN = 0, NUM_MAX = 45;

    static final String JUNIOR = "Junior (≤5)";
    static final String INTERMEDIO = "Intermedio (6–10)";
    static final String CONSOLIDADO = "Consolidado (>10)";
    private static final List<String> SEGMENT_ORDER = List.of(JUNIOR, INTERMEDIO, CONSOLIDADO);

    // Medio de convocatoria (empleadores) agrupado en 3 categorías. Id fijo (no es −sourceId).
    static final long MEDIOS_ID = -999L;
    static final String MEDIOS_INTERNET = "Internet y Redes sociales";
    static final String MEDIOS_POSTULACION = "Postulación competitiva (convocatorias públicas y privadas)";
    static final String MEDIOS_OTROS = "Recomendación, oferta personal u otros";
    private static final List<String> MEDIOS_ORDER =
            List.of(MEDIOS_INTERNET, MEDIOS_POSTULACION, MEDIOS_OTROS);

    private final QuestionRepository questionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final SubmissionAnswerOptionRepository answerOptionRepo;
    private final NumericAnswerParser parser;

    public DerivedVariableService(QuestionRepository questionRepo,
                                  SubmissionAnswerRepository answerRepo,
                                  SubmissionAnswerOptionRepository answerOptionRepo,
                                  NumericAnswerParser parser) {
        this.questionRepo = questionRepo;
        this.answerRepo = answerRepo;
        this.answerOptionRepo = answerOptionRepo;
        this.parser = parser;
    }

    /** Descriptor de una variable derivada para el selector del frontend. */
    public record Info(long id, String text, String type, long sourceQuestionId) {}

    public boolean isDerived(long id) { return id < 0; }

    /** Variables derivadas cualitativas disponibles para una encuesta. */
    public List<Info> listForSurvey(Long surveyId) {
        List<Info> out = new ArrayList<>();
        experienceQuestion(surveyId).ifPresent(q ->
                out.add(new Info(-q.getId(), "Segmento de experiencia (" + q.getText() + ")",
                        "SINGLE_ORDINAL", q.getId())));
        mediosQuestion(surveyId).ifPresent(q ->
                out.add(new Info(MEDIOS_ID, "Medio de convocatoria (3 grupos)",
                        "SINGLE_NOMINAL", q.getId())));
        return out;
    }

    /** Resuelve una variable derivada a sus categorías por submission. */
    public ResolvedVariable resolve(Long surveyId, long derivedId, List<Long> subIds) {
        if (derivedId == MEDIOS_ID) return resolveMedios(surveyId, subIds);
        return resolveSegment(derivedId, subIds);
    }

    private ResolvedVariable resolveSegment(long derivedId, List<Long> subIds) {
        long sourceId = -derivedId;
        questionRepo.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Variable derivada sin fuente: " + derivedId));

        Map<Long, String> bySubmission = new HashMap<>();
        for (SubmissionAnswer a : answerRepo.findByQuestionIdAndSubmissionIdIn(sourceId, subIds)) {
            if (a.isNotApplicable()) continue;
            NumericParseResultDTO r = parser.parse(a.getRawValue(), NUM_MIN, NUM_MAX);
            if (r.isValid() && r.getNumericValue() != null)
                bySubmission.put(a.getSubmission().getId(), segment(r.getNumericValue()));
        }
        return new ResolvedVariable("Segmento de experiencia", "SINGLE_ORDINAL",
                SEGMENT_ORDER, bySubmission);
    }

    /**
     * Medio de convocatoria (empleadores) agrupado en 3 categorías. Internet y redes sociales y
     * Postulación competitiva se mantienen; el resto (recomendación de terceros, oferta profesional
     * personal, medios escritos/audiovisuales y otros) se juntan. Cruzado con "Tamaño de la
     * organización" reproduce el χ² = 14,86 del Informe 1.
     */
    private ResolvedVariable resolveMedios(Long surveyId, List<Long> subIds) {
        Long sourceId = mediosQuestion(surveyId)
                .orElseThrow(() -> new NoSuchElementException("No se encontró la pregunta de medios."))
                .getId();

        Map<Long, String> bySubmission = new HashMap<>();
        for (SubmissionAnswer a : answerRepo.findByQuestionIdAndSubmissionIdIn(sourceId, subIds)) {
            if (a.isNotApplicable() || a.getNormalizedValue() == null || a.getNormalizedValue().isBlank())
                continue;
            bySubmission.put(a.getSubmission().getId(), medioGroup(a.getNormalizedValue()));
        }
        return new ResolvedVariable("Medio de convocatoria (3 grupos)", "SINGLE_NOMINAL",
                MEDIOS_ORDER, bySubmission);
    }

    static String medioGroup(String normalized) {
        if (normalized.contains("internet") || normalized.contains("redes sociales"))
            return MEDIOS_INTERNET;
        if (normalized.contains("postulacion competitiva") || normalized.contains("convocatoria"))
            return MEDIOS_POSTULACION;
        return MEDIOS_OTROS;   // recomendación, oferta personal, medios escritos/audiovisuales, otros
    }

    static String segment(double z) {
        if (z <= 5) return JUNIOR;
        if (z <= 10) return INTERMEDIO;
        return CONSOLIDADO;
    }

    // ── Métricas numéricas derivadas (Variable Z del módulo multivariable) ───────────

    /**
     * Métricas numéricas derivadas: la "cantidad de opciones marcadas" de cada pregunta de selección
     * múltiple (p. ej. en la encuesta de empleadores, la cantidad de cargos que desempeñan los
     * titulados — pregunta 9). Se cuentan las opciones de {@code submission_answer_options}.
     */
    public List<Info> listZMetrics(Long surveyId) {
        return questionRepo.findBySurveyIdOrderByPosition(surveyId).stream()
                .filter(q -> q.getType() == QuestionType.MULTIPLE_CHOICE)
                .map(q -> new Info(-q.getId(), zLabel(q), "NUMERIC_COUNT", q.getId()))
                .toList();
    }

    /** Etiqueta de la métrica de conteo; caso especial para la pregunta de cargos. */
    public String zLabel(Question q) {
        String t = q.getText() == null ? "" : q.getText();
        if (t.toLowerCase().contains("cargo")) return "Cantidad de cargos desempeñados";
        String s = t.length() > 40 ? t.substring(0, 40) + "…" : t;
        return "Cantidad de opciones marcadas: " + s;
    }

    /**
     * Valor de la métrica Z derivada por submission = nº de opciones marcadas en la pregunta fuente.
     * Solo incluye submissions con al menos una opción marcada (las no respondidas se excluyen).
     */
    public Map<Long, Double> resolveZMetric(long derivedId, List<Long> subIds) {
        long sourceId = -derivedId;
        List<SubmissionAnswer> answers = answerRepo.findByQuestionIdAndSubmissionIdIn(sourceId, subIds)
                .stream().filter(a -> !a.isNotApplicable()).toList();
        Map<Long, Long> answerToSubmission = answers.stream().collect(Collectors.toMap(
                SubmissionAnswer::getId, a -> a.getSubmission().getId(), (x, y) -> x));

        List<Long> answerIds = new ArrayList<>(answerToSubmission.keySet());
        Map<Long, Double> counts = new HashMap<>();
        if (!answerIds.isEmpty())
            answerOptionRepo.findByAnswerIdIn(answerIds).forEach(o -> {
                Long sub = answerToSubmission.get(o.getAnswer().getId());
                if (sub != null) counts.merge(sub, 1.0, Double::sum);
            });
        return counts;
    }

    private java.util.Optional<Question> experienceQuestion(Long surveyId) {
        return questionRepo.findBySurveyIdOrderByPosition(surveyId).stream()
                .filter(q -> q.getType() == QuestionType.NUMERIC
                        && q.getText() != null
                        && q.getText().toUpperCase().contains("VIDA PROFESIONAL"))
                .findFirst();
    }

    /** Pregunta de "medio de convocatoria" (empleadores): texto con "MEDIO" o "CONVOCA", de opción única. */
    private java.util.Optional<Question> mediosQuestion(Long surveyId) {
        return questionRepo.findBySurveyIdOrderByPosition(surveyId).stream()
                .filter(q -> q.getType() == QuestionType.SINGLE_NOMINAL
                        && q.getText() != null
                        && (q.getText().toUpperCase().contains("CONVOCA")
                            || q.getText().toUpperCase().contains("MEDIO")))
                .findFirst();
    }
}
