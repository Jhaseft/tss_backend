package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdOrderByPosition(Long questionId);

    List<QuestionOption> findByQuestionIdIn(List<Long> questionIds);
}
