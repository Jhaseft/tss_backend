package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {

    List<SubmissionAnswer> findBySubmissionId(Long submissionId);

    List<SubmissionAnswer> findBySubmissionIdIn(List<Long> submissionIds);

    List<SubmissionAnswer> findByQuestionIdAndSubmissionIdIn(Long questionId, List<Long> submissionIds);
}
