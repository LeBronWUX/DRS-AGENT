package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RootCausePattern DTO
 *
 * Represents a known root cause pattern from the experience library.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCausePattern {

    /**
     * Pattern name/identifier
     */
    private String patternName;

    /**
     * Regex pattern for matching logs
     */
    private String regexPattern;

    /**
     * Root cause description
     */
    private String cause;

    /**
     * Solution for this pattern
     */
    private String solution;

    /**
     * Frequency of occurrence: HIGH, MEDIUM, LOW
     */
    private String frequency;

    /**
     * Experience ID where this pattern was recorded
     */
    private String experienceId;

    /**
     * Confidence score of this pattern
     */
    private double confidence;

    /**
     * Keywords associated with this pattern
     */
    private List<String> keywords;

    /**
     * Applicable problem types
     */
    private List<String> applicableProblemTypes;
}