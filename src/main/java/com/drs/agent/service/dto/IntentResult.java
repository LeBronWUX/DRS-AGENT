package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Intent Recognition Result
 *
 * Contains the result of analyzing a user's problem description.
 * Used by IntentRecognizer, DiagnosisOrchestrator, and DiagnosisService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    /**
     * Identified problem type.
     * Supported types: 任务创建失败, 鉴权失败, 再编辑丢失对象,
     * 增量同步失败, 全量迁移失败, 性能问题, UNKNOWN
     */
    private String problemType;

    /**
     * Confidence score of the classification (0.0 - 1.0).
     */
    private double confidence;

    /**
     * Extracted context information from the problem description.
     * May contain: workflowId, alertId, taskId, error_code, timestamp, etc.
     */
    private Map<String, String> context;

    /**
     * Keywords extracted from the problem description.
     */
    private List<String> keywords;

    /**
     * Extracted entities (e.g., service names, error codes).
     */
    @Builder.Default
    private List<String> entities = new ArrayList<>();

    /**
     * Suggested tools to invoke based on the problem type.
     */
    private List<String> suggestedTools;

    /**
     * Original user message.
     */
    private String originalMessage;

    /**
     * Classification method used (KEYWORD_MATCH or CLAUDE_CLASSIFY).
     */
    private String classificationMethod;

    /**
     * Additional metadata about the intent recognition process.
     */
    private Map<String, Object> metadata;

    /**
     * Whether the intent was recognized successfully.
     */
    @Builder.Default
    private boolean recognized = true;

    /**
     * Convert to model.IntentResult for compatibility with existing services.
     */
    public com.drs.agent.model.IntentResult toModelIntentResult() {
        return com.drs.agent.model.IntentResult.builder()
                .originalMessage(this.originalMessage)
                .problemType(this.problemType)
                .keywords(this.keywords)
                .entities(this.entities)
                .confidence(this.confidence)
                .context(this.context != null ? convertContextToString() : null)
                .recognized(this.recognized)
                .build();
    }

    /**
     * Convert context map to string representation.
     */
    private String convertContextToString() {
        if (context == null || context.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        context.forEach((k, v) -> sb.append(k).append(":").append(v).append(";"));
        return sb.toString();
    }

    /**
     * Create from model.IntentResult.
     */
    public static IntentResult fromModelIntentResult(com.drs.agent.model.IntentResult modelResult) {
        if (modelResult == null) {
            return null;
        }
        return IntentResult.builder()
                .originalMessage(modelResult.getOriginalMessage())
                .problemType(modelResult.getProblemType())
                .keywords(modelResult.getKeywords())
                .entities(modelResult.getEntities())
                .confidence(modelResult.getConfidence())
                .recognized(modelResult.isRecognized())
                .build();
    }

    /**
     * Create a simple intent result.
     */
    public static IntentResult of(String originalMessage, String problemType, List<String> keywords) {
        return IntentResult.builder()
                .originalMessage(originalMessage)
                .problemType(problemType)
                .keywords(keywords)
                .recognized(true)
                .build();
    }

    /**
     * Create an unrecognized intent result.
     */
    public static IntentResult unrecognized(String originalMessage) {
        return IntentResult.builder()
                .originalMessage(originalMessage)
                .problemType("UNKNOWN")
                .recognized(false)
                .confidence(0.0)
                .build();
    }
}