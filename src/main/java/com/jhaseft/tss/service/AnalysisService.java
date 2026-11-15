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
    private final DerivedVariableService derivedVariables;

    public AnalysisService(QuestionRepository questionRepo,
                           QuestionOptionRepository optionRepo,
                           SubmissionRepository submissionRepo,
                           SubmissionAnswerRepository answerRepo,
                           ChiSquareService chiSquareService,
                           DerivedVariableService derivedVariables) {
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.chiSquareService = chiSquareService;
        this.derivedVariables = derivedVariables;
    }

    @Transactional(readOnly = true)
    public CrossTabResultDTO crossTab(Long surveyId, Long id1, Long id2) {
        if (id1.equals(id2)) throw new IllegalArgumentException("Las variables deben ser distintas.");

        List<Long> subIds = submissionRepo.findBySurveyId(surveyId)
                .stream().map(Submission::getId).toList();
        if (subIds.isEmpty()) throw new IllegalArgumentException("La encuesta no tiene respuestas.");

        // Cada lado puede ser una pregunta real (id positivo) o una variable derivada (id negativo).
        ResolvedVariable v1 = resolve(id1, subIds);
        ResolvedVariable v2 = resolve(id2, subIds);

        // Tabla observada sobre las etiquetas en el orden del eje.
        List<String> rowLabels = new ArrayList<>(v1.orderedLabels());
        List<String> colLabels = new ArrayList<>(v2.orderedLabels());
        Map<String, Integer> rowIdx = index(rowLabels);
        Map<String, Integer> colIdx = index(colLabels);

        long[][] raw = new long[rowLabels.size()][colLabels.size()];
        for (Map.Entry<Long, String> e : v1.bySubmission().entrySet()) {
            String b = v2.bySubmission().get(e.getKey());
            if (b == null) continue;
            Integer ri = rowIdx.get(e.getValue());
            Integer ci = colIdx.get(b);
            if (ri != null && ci != null) raw[ri][ci]++;
        }

        // Se descartan las categorías sin ninguna observación (filas/columnas vacías): no aportan al
        // χ² y empeoran el supuesto de Cochran (cada celda vacía tiene E = 0 < 5). Así el sistema
        // coincide con el Informe 1 (p. ej. Género excluye "Prefiero no indicar", que tiene 0).
        long[][] obs = dropEmpty(raw, rowLabels, colLabels);

        int R = rowLabels.size(), C = colLabels.size();
        long n = Arrays.stream(obs).flatMapToLong(Arrays::stream).sum();

        long[] rowTotals = new long[R], colTotals = new long[C];
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++) { rowTotals[r] += obs[r][c]; colTotals[c] += obs[r][c]; }

        double[][] rowPct = new double[R][C];
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++)
                rowPct[r][c] = rowTotals[r] > 0 ? obs[r][c] * 100.0 / rowTotals[r] : 0.0;

        CrossTabResultDTO result = new CrossTabResultDTO();
        result.setQuestionId1(id1);
        result.setQuestionText1(v1.text());
        result.setQuestionId2(id2);
        result.setQuestionText2(v2.text());
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

    // ── Resolución de variables ────────────────────────────────────────────────

    private ResolvedVariable resolve(Long id, List<Long> subIds) {
        if (derivedVariables.isDerived(id)) return derivedVariables.resolve(id, subIds);

        Question q = questionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + id));
        List<QuestionOption> opts = optionRepo.findByQuestionIdOrderByPosition(id);
        Map<Long, String> answers = loadAnswerMap(id, subIds);   // sid → valor normalizado

        List<String> orderedLabels;
        Map<String, String> toLabel = new HashMap<>();   // valor normalizado / display → etiqueta
        if (!opts.isEmpty()) {
            orderedLabels = opts.stream().map(QuestionOption::getDisplayText).distinct().toList();
            for (QuestionOption o : opts) {
                toLabel.put(o.getValue(), o.getDisplayText());
                toLabel.put(o.getDisplayText(), o.getDisplayText());
            }
        } else {
            orderedLabels = answers.values().stream().distinct().sorted().toList();
        }

        Map<Long, String> bySubmission = new HashMap<>();
        boolean hasOpts = !opts.isEmpty();
        answers.forEach((sid, val) -> {
            String label = hasOpts ? toLabel.get(val) : val;
            if (label != null) bySubmission.put(sid, label);
        });
        return new ResolvedVariable(q.getText(), q.getType().name(), orderedLabels, bySubmission);
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

    private Map<String, Integer> index(List<String> labels) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) idx.putIfAbsent(labels.get(i), i);
        return idx;
    }

    /** Elimina filas y columnas enteramente en cero; recorta las etiquetas en consecuencia. */
    private long[][] dropEmpty(long[][] m, List<String> rowLabels, List<String> colLabels) {
        int R = m.length, C = R > 0 ? m[0].length : 0;
        List<Integer> keepRows = new ArrayList<>(), keepCols = new ArrayList<>();
        for (int r = 0; r < R; r++) {
            long s = 0; for (int c = 0; c < C; c++) s += m[r][c];
            if (s > 0) keepRows.add(r);
        }
        for (int c = 0; c < C; c++) {
            long s = 0; for (int r = 0; r < R; r++) s += m[r][c];
            if (s > 0) keepCols.add(c);
        }
        long[][] out = new long[keepRows.size()][keepCols.size()];
        for (int i = 0; i < keepRows.size(); i++)
            for (int j = 0; j < keepCols.size(); j++)
                out[i][j] = m[keepRows.get(i)][keepCols.get(j)];

        List<String> newRows = keepRows.stream().map(rowLabels::get).toList();
        List<String> newCols = keepCols.stream().map(colLabels::get).toList();
        rowLabels.clear(); rowLabels.addAll(newRows);
        colLabels.clear(); colLabels.addAll(newCols);
        return out;
    }
}
