package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Diagnosis Intent DTO
 *
 * Represents parsed diagnosis intent from user message.
 * Used by WeLinkService to parse user commands.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisIntent {

    /**
     * Intent type (DIAGNOSE, QUERY, FEEDBACK, HELP, UNKNOWN)
     */
    private IntentType intentType;

    /**
     * Problem type extracted from message
     */
    private String problemType;

    /**
     * Problem description
     */
    private String problem;

    /**
     * Workflow ID extracted from message
     */
    private String workflowId;

    /**
     * Task ID extracted from message
     */
    private String taskId;

    /**
     * Alert ID extracted from message
     */
    private String alertId;

    /**
     * Session ID for continuing previous diagnosis
     */
    private String sessionId;

    /**
     * Confidence score of intent recognition
     */
    @Builder.Default
    private double confidence = 0.0;

    /**
     * Original user message
     */
    private String originalMessage;

    /**
     * Additional context extracted from message
     */
    private Map<String, String> context;

    /**
     * Keywords extracted from message
     */
    private List<String> keywords;

    /**
     * Intent type enumeration
     */
    public enum IntentType {
        /**
         * Request to diagnose a problem
         */
        DIAGNOSE,

        /**
         * Query about diagnosis result or history
         */
        QUERY,

        /**
         * Feedback on previous diagnosis
         */
        FEEDBACK,

        /**
         * Help request
         */
        HELP,

        /**
         * Unknown or unrecognizable intent
         */
        UNKNOWN
    }

    /**
     * Check if the intent is valid for diagnosis
     */
    public boolean isValidForDiagnosis() {
        return intentType == IntentType.DIAGNOSE && problem != null && !problem.isEmpty();
    }

    /**
     * Create a diagnosis request from this intent
     */
    public DiagnosisRequest toDiagnosisRequest(String userId) {
        StringBuilder problemBuilder = new StringBuilder();
        if (problem != null) {
            problemBuilder.append(problem);
        }
        if (workflowId != null) {
            problemBuilder.append(" workflowId=").append(workflowId);
        }
        if (taskId != null) {
            problemBuilder.append(" taskId=").append(taskId);
        }

        return DiagnosisRequest.builder()
                .userId(userId)
                .problem(problemBuilder.toString())
                .context(context != null ? context.toString() : null)
                .priority(isHighPriority() ? "HIGH" : "MEDIUM")
                .build();
    }

    /**
     * Check if the problem is high priority
     */
    private boolean isHighPriority() {
        if (problemType != null) {
            String lowerType = problemType.toLowerCase();
            return lowerType.contains("失败") || lowerType.contains("error") || lowerType.contains("critical");
        }
        return false;
    }

    /**
     * Create an unknown intent
     */
    public static DiagnosisIntent unknown(String message) {
        return DiagnosisIntent.builder()
                .intentType(IntentType.UNKNOWN)
                .originalMessage(message)
                .confidence(0.0)
                .build();
    }

    /**
     * Create a help intent
     */
    public static DiagnosisIntent help(String message) {
        return DiagnosisIntent.builder()
                .intentType(IntentType.HELP)
                .originalMessage(message)
                .confidence(1.0)
                .build();
    }
}