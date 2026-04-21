package com.drs.agent.repository;

import com.drs.agent.entity.McpToolConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP Tool Configuration Repository
 *
 * Provides data access for dynamic MCP tool configurations.
 */
@Repository
public interface McpToolConfigRepository extends JpaRepository<McpToolConfig, Long> {

    Optional<McpToolConfig> findByToolName(String toolName);

    List<McpToolConfig> findByEnabled(Boolean enabled);

    List<McpToolConfig> findByToolType(String toolType);

    boolean existsByToolName(String toolName);

    void deleteByToolName(String toolName);
}