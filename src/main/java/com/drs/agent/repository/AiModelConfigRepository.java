package com.drs.agent.repository;

import com.drs.agent.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI Model Configuration Repository
 */
@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {

    Optional<AiModelConfig> findByModelName(String modelName);

    List<AiModelConfig> findByProvider(String provider);

    List<AiModelConfig> findByEnabled(Boolean enabled);

    Optional<AiModelConfig> findByIsDefaultTrue();

    List<AiModelConfig> findAllByIsDefaultTrue();

    boolean existsByModelName(String modelName);

    void deleteByModelName(String modelName);

    List<AiModelConfig> findByIsDefaultTrueAndEnabledTrue();
}