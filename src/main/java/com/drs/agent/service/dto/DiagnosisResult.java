package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DiagnosisResult DTO
 *
 * Represents the complete diagnosis result for experience generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Problem type
     */
    private String problemType;

    /**
     * User problem description
     */
    private String userProblem;

    /**
     * Diagnosis chain result
     */
    private DiagnosisChainResult diagnosisChainResult;

    /**
     * Root cause analysis result
     */
    private RootCauseResult rootCauseResult;

    /**
     * Intent recognition result
     */
    private IntentResult intentResult;

    /**
     * Solution applied
     */
    private String solutionApplied;

    /**
     * Verification result
     */
    private String verificationResult;

    /**
     * Similar experiences found
     */
    private List<ExperienceMatch> similarExperiences;

    /**
     * Wiki content retrieved
     */
    private String wikiContent;

    /**
     * Confidence score
     */
    private double confidence;

    /**
     * Status
     */
    private String status;

    /**
     * User feedback (if provided)
     */
    private UserFeedback userFeedback;

    /**
     * Experience match
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceMatch {
        private String experienceId;
        private String problemType;
        private double similarity;
        private String summary;
    }
}