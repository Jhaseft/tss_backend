package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findBySurveyId(Long surveyId);

    long countBySurveyId(Long surveyId);

    boolean existsBySurveyIdAndRowIndex(Long surveyId, Integer rowIndex);
}
