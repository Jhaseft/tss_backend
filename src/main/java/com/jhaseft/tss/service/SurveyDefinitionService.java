package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.SurveyDefinitionDTO;
import com.jhaseft.tss.dto.SurveyImportResultDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SurveyDefinitionService {

    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final LikertScaleRepository likertScaleRepo;
    private final QuestionInferenceService inference;

    public SurveyDefinitionService(SurveyRepository surveyRepo,
                                    QuestionRepository questionRepo,
                                    QuestionOptionRepository optionRepo,
                                    LikertScaleRepository likertScaleRepo,
                                    QuestionInferenceService inference) {
        this.surveyRepo = surveyRepo;
        this.questionRepo = questionRepo;
        this.optionRepo = optionRepo;
        this.likertScaleRepo = likertScaleRepo;
        this.inference = inference;
    }

    @Transactional
    public SurveyImportResultDTO create(SurveyDefinitionDTO def) {
        Survey survey = new Survey();
        survey.setName(def.getName());
        survey.setOrigin(SurveyOrigin.valueOf(def.getOrigin()));
        survey.setPublicSlug(UUID.randomUUID().toString());
        survey = surveyRepo.save(survey);

        // Crear o reutilizar escalas Likert
        Map<String, LikertScale> scales = new LinkedHashMap<>();
        Map<String, List<SurveyDefinitionDTO.OptionDef>> scaleOptions = new LinkedHashMap<>();
        if (def.getLikertScales() != null) {
            for (SurveyDefinitionDTO.LikertScaleDef sd : def.getLikertScales()) {
                String json = buildScaleJson(sd.getOptions());
                LikertScale scale = likertScaleRepo.findByName(sd.getName()).orElseGet(() -> {
                    LikertScale ls = new LikertScale();
                    ls.setName(sd.getName());
                    ls.setOptionsJson(json);
                    return likertScaleRepo.save(ls);
                });
                scales.put(sd.getName(), scale);
                scaleOptions.put(sd.getName(), sd.getOptions());
            }
        }

        int count = 0;
        for (SurveyDefinitionDTO.QuestionDef qd : def.getQuestions()) {
            Question q = new Question();
            q.setSurvey(survey);
            q.setColumnName(trunc(qd.getColumnName(), 500));
            q.setText(trunc(qd.getText(), 1000));
            q.setType(QuestionType.valueOf(qd.getType()));
            q.setPosition(qd.getPosition());
            q.setPersonalData(qd.isPersonalData());
            q.setSection(qd.getSection());
            q.setMatrixGroup(qd.getMatrixGroup());

            if (qd.getLikertScaleName() != null && scales.containsKey(qd.getLikertScaleName())) {
                q.setLikertScale(scales.get(qd.getLikertScaleName()));
            }

            q = questionRepo.save(q);

            // Opciones explícitas en el JSON; para Likert sin opciones propias usar la escala
            List<SurveyDefinitionDTO.OptionDef> optDefs = qd.getOptions();
            if ((optDefs == null || optDefs.isEmpty()) && qd.getLikertScaleName() != null) {
                optDefs = scaleOptions.get(qd.getLikertScaleName());
            }

            if (optDefs != null) {
                for (SurveyDefinitionDTO.OptionDef od : optDefs) {
                    QuestionOption qo = new QuestionOption();
                    qo.setQuestion(q);
                    qo.setValue(trunc(inference.normalize(od.getDisplayText()), 500));
                    qo.setDisplayText(trunc(od.getDisplayText(), 500));
                    qo.setPosition(od.getPosition());
                    qo.setNumericValue(od.getNumericValue() != null
                            ? BigDecimal.valueOf(od.getNumericValue()) : null);
                    optionRepo.save(qo);
                }
            }
            count++;
        }

        SurveyImportResultDTO result = new SurveyImportResultDTO();
        result.setSurveyId(survey.getId());
        result.setPublicSlug(survey.getPublicSlug());
        result.setSurveyName(survey.getName());
        result.setOrigin(survey.getOrigin().name());
        result.setRowsLoaded(0);
        result.setQuestionsDetected(count);
        result.setErrors(List.of());
        return result;
    }

    private String buildScaleJson(List<SurveyDefinitionDTO.OptionDef> opts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < opts.size(); i++) {
            SurveyDefinitionDTO.OptionDef o = opts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"value\":\"").append(inference.normalize(o.getDisplayText()))
              .append("\",\"numeric\":").append(o.getNumericValue() == null ? "null" : o.getNumericValue())
              .append("}");
        }
        return sb.append("]").toString();
    }

    private String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
