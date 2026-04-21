package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alert Information DTO
 *
 * Represents an alert received from monitoring systems.
 * Used for automatic diagnosis triggering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertInfo {

    /**
     * Unique alert ID
     */
    private String alertId;

    /**
     * Alert type (e.g., 任务创建失败, 鉴权失败, 性能问题)
     */
    private String alertType;

    /**
     * Alert severity (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String severity;

    /**
     * Associated workflow ID
     */
    private String workflowId;

    /**
     * Associated task ID
     */
    private String taskId;

    /**
     * Alert message content
     */
    private String message;

    /**
     * Alert timestamp (ISO 8601 format)
     */
    private String timestamp;

    /**
     * Source system that generated the alert
     */
    private String source;

    /**
     * Additional context (JSON format)
     */
    private String context;

    /**
     * Service name related to the alert
     */
    private String serviceName;

    /**
     * Error code if available
     */
    private String errorCode;

    /**
     * Whether this alert should trigger automatic diagnosis
     */
    @Builder.Default
    private boolean autoDiagnose = false;

    /**
     * WeLink group ID to push diagnosis results
     */
    private String weLinkGroupId;

    /**
     * Parse severity string to standardized format
     */
    public String getNormalizedSeverity() {
        if (severity == null) {
            return "MEDIUM";
        }
        String upper = severity.toUpperCase();
        switch (upper) {
            case "CRITICAL":
            case "CRIT":
            case "P0":
            case "P1":
                return "CRITICAL";
            case "HIGH":
            case "WARN":
            case "WARNING":
            case "P2":
                return "HIGH";
            case "LOW":
            case "INFO":
            case "P4":
                return "LOW";
            default:
                return "MEDIUM";
        }
    }

    /**
     * Check if alert is high priority (should auto-diagnose)
     */
    public boolean isHighPriority() {
        String sev = getNormalizedSeverity();
        return "CRITICAL".equals(sev) || "HIGH".equals(sev);
    }
}