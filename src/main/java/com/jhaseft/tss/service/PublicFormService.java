package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.AnswerInputDTO;
import com.jhaseft.tss.model.*;
import com.jhaseft.tss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PublicFormService {

    private final SurveyRepository surveyRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final SubmissionRepository submissionRepo;
    private final SubmissionAnswerRepository answerRepo;

    public PublicFormService(SurveyRepository surveyRepo,
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

    @Transactional
    public Long saveResponse(String slug, List<AnswerInputDTO> inputs) {
        Survey survey = surveyRepo.findByPublicSlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Encuesta no encontrada"));

        List<Question> questions = questionRepo.findBySurveyIdOrderByPosition(survey.getId());
        Map<Long, Question> qMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Long> qIds = questions.stream().map(Question::getId).toList();
        Map<Long, List<QuestionOption>> optsByQ = qIds.isEmpty() ? Map.of()
                : optionRepo.findByQuestionIdIn(qIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        Submission sub = new Submission();
        sub.setSurvey(survey);
        sub.setSubmittedAt(LocalDateTime.now());
        sub.setSource(SubmissionSource.DIRECT);
        sub.setRowIndex((int) (submissionRepo.countBySurveyId(survey.getId()) + 1));
        sub = submissionRepo.save(sub);

        for (AnswerInputDTO input : inputs) {
            if (input.getQuestionId() == null) continue;
            Question q = qMap.get(input.getQuestionId());
            if (q == null) continue;

            SubmissionAnswer answer = new SubmissionAnswer();
            answer.setSubmission(sub);
            answer.setQuestion(q);

            List<QuestionOption> opts = optsByQ.getOrDefault(q.getId(), List.of());

            boolean hasValues = input.getValues() != null && !input.getValues().isEmpty();
            boolean hasValue = input.getValue() != null && !input.getValue().isBlank();

            if (hasValues) {
                String joined = String.join("; ", input.getValues());
                answer.setRawValue(joined);
                answer.setNormalizedValue(joined.toLowerCase().trim());
            } else if (hasValue) {
                answer.setRawValue(input.getValue());
                String norm = opts.stream()
                        .filter(o -> o.getDisplayText().equalsIgnoreCase(input.getValue().trim())
                                || o.getValue().equalsIgnoreCase(input.getValue().trim()))
                        .map(QuestionOption::getValue)
                        .findFirst()
                        .orElse(input.getValue().trim().toLowerCase());
                answer.setNormalizedValue(norm);
            } else {
                answer.setNotApplicable(true);
            }

            answerRepo.save(answer);
        }

        return sub.getId();
    }
}
