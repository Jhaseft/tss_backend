package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.LikertScale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikertScaleRepository extends JpaRepository<LikertScale, Long> {

    Optional<LikertScale> findByName(String name);
}
