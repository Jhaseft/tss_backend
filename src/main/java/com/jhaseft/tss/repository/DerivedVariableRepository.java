package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.DerivedVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DerivedVariableRepository extends JpaRepository<DerivedVariable, Long> {

    List<DerivedVariable> findBySurveyId(Long surveyId);
}
