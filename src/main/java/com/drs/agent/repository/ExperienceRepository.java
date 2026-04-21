package com.drs.agent.repository;

import com.drs.agent.model.Experience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Experience Repository
 *
 * Provides data access for operational experiences.
 */
@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    Optional<Experience> findByExperienceId(String experienceId);

    List<Experience> findByProblemType(String problemType);

    Page<Experience> findByProblemType(String problemType, Pageable pageable);

    List<Experience> findByKeywordsContaining(String keyword);

    void deleteByExperienceId(String experienceId);
}