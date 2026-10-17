package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.SubmissionAnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionAnswerOptionRepository extends JpaRepository<SubmissionAnswerOption, Long> {

    List<SubmissionAnswerOption> findByAnswerId(Long answerId);

    List<SubmissionAnswerOption> findByAnswerIdIn(List<Long> answerIds);
}
