package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RootCauseResult DTO
 *
 * Contains the root cause analysis result from the RootCauseAnalyzer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseResult {

    /**
     * Root cause category: PERMISSION, CONFIGURATION, RESOURCE, NETWORK, CODE_BUG, UNKNOWN
     */
    private String category;

    /**
     * Detailed description of the root cause
     */
    private String description;

    /**
     * Service component involved (e.g., drs-service, database, network)
     */
    private String component;

    /**
     * Error pattern matched (regex pattern)
     */
    private String errorPattern;

    /**
     * Solution recommendation
     */
    private Solution solution;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Evidence chain supporting the root cause
     */
    private List<String> evidence;

    /**
     * Related experience IDs from the experience library
     */
    private List<String> relatedExperiences;

    /**
     * Risk level: LOW, MEDIUM, HIGH
     */
    private String riskLevel;

    /**
     * Whether this case should be added to the experience library
     */
    private boolean suggestedLearning;

    /**
     * Summary of the root cause
     */
    private String summary;

    /**
     * Timestamp when the analysis was performed
     */
    private long analysisTimeMs;
}