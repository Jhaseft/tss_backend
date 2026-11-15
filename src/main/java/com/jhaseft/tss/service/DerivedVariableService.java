package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.NumericParseResultDTO;
import com.jhaseft.tss.model.Question;
import com.jhaseft.tss.model.QuestionType;
import com.jhaseft.tss.model.SubmissionAnswer;
import com.jhaseft.tss.repository.QuestionRepository;
import com.jhaseft.tss.repository.SubmissionAnswerRepository;
import com.jhaseft.tss.service.parsing.NumericAnswerParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    private final QuestionRepository questionRepo;
    private final SubmissionAnswerRepository answerRepo;
    private final NumericAnswerParser parser;

    public DerivedVariableService(QuestionRepository questionRepo,
                                  SubmissionAnswerRepository answerRepo,
                                  NumericAnswerParser parser) {
        this.questionRepo = questionRepo;
        this.answerRepo = answerRepo;
        this.parser = parser;
    }

    /** Descriptor de una variable derivada para el selector del frontend. */
    public record Info(long id, String text, String type, long sourceQuestionId) {}

    public boolean isDerived(long id) { return id < 0; }

    /** Variables derivadas disponibles para una encuesta (hoy, el segmento de experiencia si aplica). */
    public List<Info> listForSurvey(Long surveyId) {
        List<Info> out = new ArrayList<>();
        experienceQuestion(surveyId).ifPresent(q ->
                out.add(new Info(-q.getId(), "Segmento de experiencia (" + q.getText() + ")",
                        "SINGLE_ORDINAL", q.getId())));
        return out;
    }

    /** Resuelve una variable derivada a sus categorías por submission. */
    public ResolvedVariable resolve(long derivedId, List<Long> subIds) {
        long sourceId = -derivedId;
        Question source = questionRepo.findById(sourceId)
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

    static String segment(double z) {
        if (z <= 5) return JUNIOR;
        if (z <= 10) return INTERMEDIO;
        return CONSOLIDADO;
    }

    private java.util.Optional<Question> experienceQuestion(Long surveyId) {
        return questionRepo.findBySurveyIdOrderByPosition(surveyId).stream()
                .filter(q -> q.getType() == QuestionType.NUMERIC
                        && q.getText() != null
                        && q.getText().toUpperCase().contains("VIDA PROFESIONAL"))
                .findFirst();
    }
}
