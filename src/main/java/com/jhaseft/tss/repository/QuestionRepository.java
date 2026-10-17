package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySurveyIdOrderByPosition(Long surveyId);

    List<Question> findBySurveyIdAndPersonalDataFalseOrderByPosition(Long surveyId);
}
