package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Diagnosis Response DTO
 *
 * Represents the diagnosis result returned to the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResponse {

    /**
     * Session ID for this diagnosis
     */
    private String sessionId;

    /**
     * Problem type identified (e.g., NETWORK, DATABASE, APPLICATION, SYSTEM)
     */
    private String problemType;

    /**
     * Root cause analysis result
     */
    private String rootCause;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidence;

    /**
     * Recommended solution
     */
    private String solution;

    /**
     * Diagnosis chain/steps executed
     */
    private List<DiagnosisStep> diagnosisChain;

    /**
     * Similar experiences found
     */
    private List<ExperienceMatch> similarExperiences;

    /**
     * Current status of diagnosis
     */
    private String status;

    /**
     * Error message if diagnosis failed
     */
    private String errorMessage;

    /**
     * Diagnosis step detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisStep {
        private String stepId;
        private String stepName;
        private String description;
        private String result;
        private String status;
        private Long executionTimeMs;
    }

    /**
     * Similar experience match
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceMatch {
        private String experienceId;
        private String problemType;
        private Double similarity;
        private String summary;
    }
}