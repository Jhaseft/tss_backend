package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Optional<Survey> findByPublicSlug(String publicSlug);

    boolean existsBySourceHash(String sourceHash);

    Optional<Survey> findBySourceHash(String sourceHash);
}
