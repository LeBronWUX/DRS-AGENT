package com.drs.agent.service;

import com.drs.agent.model.AlertInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alert Parser Service
 *
 * Parses alert content from various monitoring systems.
 * Supports multiple alert formats and determines if auto-diagnosis should be triggered.
 */
@Service
public class AlertParser {

    private static final Logger logger = LoggerFactory.getLogger(AlertParser.class);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_DATE_TIME;

    private static final Pattern WORKFLOW_ID_PATTERN =
            Pattern.compile("workflow[_\\-]?id[=:\\s]*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_ID_PATTERN =
            Pattern.compile("task[_\\-]?id[=:\\s]*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile("error[_\\-]?code[=:\\s]*(\\S+)", Pattern.CASE_INSENSITIVE);

    // Alert type keywords mapping (using HashMap because Map.of() has limit of 10 entries)
    private static final Map<String, String> ALERT_TYPE_MAPPING;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("任务创建失败", "任务创建失败");
        map.put("task create fail", "任务创建失败");
        map.put("鉴权失败", "鉴权失败");
        map.put("auth fail", "鉴权失败");
        map.put("authentication fail", "鉴权失败");
        map.put("认证失败", "鉴权失败");
        map.put("对象丢失", "再编辑丢失对象");
        map.put("object lost", "再编辑丢失对象");
        map.put("增量同步失败", "增量同步失败");
        map.put("incremental sync fail", "增量同步失败");
        map.put("全量迁移失败", "全量迁移失败");
        map.put("full migration fail", "全量迁移失败");
        map.put("性能问题", "性能问题");
        map.put("performance issue", "性能问题");
        map.put("性能下降", "性能问题");
        ALERT_TYPE_MAPPING = map;
    }

    // Alert types that should trigger auto-diagnosis
    private static final List<String> AUTO_DIAGNOSE_TYPES = List.of(
            "任务创建失败",
            "鉴权失败",
            "再编辑丢失对象",
            "增量同步失败",
            "全量迁移失败"
    );

    private final ObjectMapper objectMapper;

    public AlertParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse alert content to AlertInfo
     *
     * @param alertContent Alert content (can be JSON or plain text)
     * @return Parsed AlertInfo
     */
    public AlertInfo parse(String alertContent) {
        if (alertContent == null || alertContent.trim().isEmpty()) {
            return AlertInfo.builder()
                    .alertType("UNKNOWN")
                    .severity("MEDIUM")
                    .build();
        }

        // Try JSON format first
        if (alertContent.trim().startsWith("{")) {
            try {
                return parseJsonFormat(alertContent);
            } catch (Exception e) {
                logger.warn("Failed to parse alert as JSON, trying plain text format", e);
            }
        }

        // Parse as plain text
        return parsePlainTextFormat(alertContent);
    }

    /**
     * Determine if auto-diagnosis should be triggered for the alert
     *
     * @param alert Alert information
     * @return true if should auto-diagnose
     */
    public boolean shouldAutoDiagnose(AlertInfo alert) {
        if (alert == null) {
            return false;
        }

        // Check severity - only HIGH and CRITICAL
        if (!alert.isHighPriority()) {
            logger.debug("Alert {} has low priority, skipping auto-diagnosis", alert.getAlertId());
            return false;
        }

        // Check alert type
        if (alert.getAlertType() != null && AUTO_DIAGNOSE_TYPES.contains(alert.getAlertType())) {
            return true;
        }

        // Check explicit flag
        return alert.isAutoDiagnose();
    }

    /**
     * Parse alert in custom format
     *
     * @param format Format name (json, prometheus, grafana, zabbix, etc.)
     * @param content Alert content
     * @return Parsed AlertInfo
     */
    public AlertInfo parseCustomFormat(String format, String content) {
        switch (format.toLowerCase()) {
            case "json":
                return parseJsonFormat(content);
            case "prometheus":
                return parsePrometheusFormat(content);
            case "grafana":
                return parseGrafanaFormat(content);
            case "zabbix":
                return parseZabbixFormat(content);
            default:
                logger.warn("Unknown alert format: {}, using default parser", format);
                return parse(content);
        }
    }

    /**
     * Parse JSON format alert
     */
    @SuppressWarnings("unchecked")
    private AlertInfo parseJsonFormat(String content) {
        try {
            Map<String, Object> jsonMap = objectMapper.readValue(content, Map.class);

            AlertInfo.AlertInfoBuilder builder = AlertInfo.builder();

            // Extract standard fields
            builder.alertId(getStringValue(jsonMap, "alertId", "alert_id", "id"));
            builder.alertType(normalizeAlertType(getStringValue(jsonMap, "alertType", "alert_type", "type")));
            builder.severity(normalizeSeverity(getStringValue(jsonMap, "severity", "level", "priority")));
            builder.message(getStringValue(jsonMap, "message", "msg", "description", "content"));
            builder.timestamp(normalizeTimestamp(getStringValue(jsonMap, "timestamp", "time", "datetime", "created_at")));
            builder.source(getStringValue(jsonMap, "source", "source_system", "origin"));

            // Extract context fields
            builder.workflowId(getStringValue(jsonMap, "workflowId", "workflow_id", "wf_id"));
            builder.taskId(getStringValue(jsonMap, "taskId", "task_id"));
            builder.serviceName(getStringValue(jsonMap, "serviceName", "service_name", "service"));
            builder.errorCode(getStringValue(jsonMap, "errorCode", "error_code", "code"));

            // Extract labels/annotations if present
            Object labels = jsonMap.get("labels");
            if (labels instanceof Map) {
                Map<String, Object> labelsMap = (Map<String, Object>) labels;
                if (builder.build().getWorkflowId() == null) {
                    builder.workflowId(getStringValue(labelsMap, "workflowId", "workflow_id"));
                }
                if (builder.build().getAlertType() == null) {
                    builder.alertType(getStringValue(labelsMap, "alertname", "alert_name"));
                }
            }

            // Check for auto-diagnose flag
            Object autoDiagnose = jsonMap.get("autoDiagnose");
            if (autoDiagnose != null) {
                builder.autoDiagnose(Boolean.TRUE.equals(autoDiagnose));
            }

            // Store raw context
            builder.context(content);

            return builder.build();

        } catch (Exception e) {
            logger.error("Failed to parse JSON alert", e);
            return AlertInfo.builder()
                    .alertType("UNKNOWN")
                    .severity("MEDIUM")
                    .message(content)
                    .build();
        }
    }

    /**
     * Parse plain text format alert
     */
    private AlertInfo parsePlainTextFormat(String content) {
        AlertInfo.AlertInfoBuilder builder = AlertInfo.builder();

        // Generate alert ID
        builder.alertId("alert_" + System.currentTimeMillis());

        // Extract workflow ID
        Matcher workflowMatcher = WORKFLOW_ID_PATTERN.matcher(content);
        if (workflowMatcher.find()) {
            builder.workflowId(workflowMatcher.group(1));
        }

        // Extract task ID
        Matcher taskMatcher = TASK_ID_PATTERN.matcher(content);
        if (taskMatcher.find()) {
            builder.taskId(taskMatcher.group(1));
        }

        // Extract error code
        Matcher errorCodeMatcher = ERROR_CODE_PATTERN.matcher(content);
        if (errorCodeMatcher.find()) {
            builder.errorCode(errorCodeMatcher.group(1));
        }

        // Detect alert type from keywords
        String alertType = detectAlertType(content);
        builder.alertType(alertType);

        // Set severity based on keywords
        String severity = detectSeverity(content);
        builder.severity(severity);

        // Set message
        builder.message(content);

        // Set timestamp
        builder.timestamp(LocalDateTime.now().format(ISO_FORMATTER));

        // Set context
        builder.context(content);

        return builder.build();
    }

    /**
     * Parse Prometheus alert format
     */
    @SuppressWarnings("unchecked")
    private AlertInfo parsePrometheusFormat(String content) {
        // Prometheus Alertmanager webhook format
        try {
            Map<String, Object> jsonMap = objectMapper.readValue(content, Map.class);

            AlertInfo.AlertInfoBuilder builder = AlertInfo.builder();

            // Prometheus format has status, alerts array
            String status = getStringValue(jsonMap, "status");
            builder.severity("firing".equals(status) ? "HIGH" : "LOW");

            // Get first alert from alerts array
            Object alerts = jsonMap.get("alerts");
            if (alerts instanceof List) {
                List<Map<String, Object>> alertsList = (List<Map<String, Object>>) alerts;
                if (!alertsList.isEmpty()) {
                    Map<String, Object> firstAlert = alertsList.get(0);
                    builder.alertId(getStringValue(firstAlert, "fingerprint"));
                    builder.message(getStringValue(firstAlert, "annotations", "summary"));
                    builder.timestamp(getStringValue(firstAlert, "startsAt"));

                    // Extract from labels
                    Object labels = firstAlert.get("labels");
                    if (labels instanceof Map) {
                        Map<String, Object> labelsMap = (Map<String, Object>) labels;
                        builder.alertType(getStringValue(labelsMap, "alertname"));
                        builder.serviceName(getStringValue(labelsMap, "service", "job"));
                    }
                }
            }

            builder.source("prometheus");
            builder.context(content);

            return builder.build();

        } catch (Exception e) {
            logger.error("Failed to parse Prometheus alert", e);
            return parsePlainTextFormat(content);
        }
    }

    /**
     * Parse Grafana alert format
     */
    @SuppressWarnings("unchecked")
    private AlertInfo parseGrafanaFormat(String content) {
        try {
            Map<String, Object> jsonMap = objectMapper.readValue(content, Map.class);

            AlertInfo.AlertInfoBuilder builder = AlertInfo.builder();

            builder.alertId(getStringValue(jsonMap, "alertId", "id"));
            builder.alertType(getStringValue(jsonMap, "ruleName", "rule_name"));
            builder.message(getStringValue(jsonMap, "message", "title"));
            builder.severity(normalizeSeverity(getStringValue(jsonMap, "severity", "priority")));

            // Grafana state: alerting, ok, no_data
            String state = getStringValue(jsonMap, "state");
            if ("alerting".equals(state)) {
                builder.severity("HIGH");
            }

            // Extract tags
            Object tags = jsonMap.get("tags");
            if (tags instanceof Map) {
                Map<String, Object> tagsMap = (Map<String, Object>) tags;
                builder.workflowId(getStringValue(tagsMap, "workflowId", "workflow_id"));
                builder.serviceName(getStringValue(tagsMap, "service"));
            }

            builder.source("grafana");
            builder.timestamp(LocalDateTime.now().format(ISO_FORMATTER));
            builder.context(content);

            return builder.build();

        } catch (Exception e) {
            logger.error("Failed to parse Grafana alert", e);
            return parsePlainTextFormat(content);
        }
    }

    /**
     * Parse Zabbix alert format
     */
    private AlertInfo parseZabbixFormat(String content) {
        // Zabbix sends alerts as plain text with key-value pairs
        AlertInfo.AlertInfoBuilder builder = AlertInfo.builder();

        // Extract from Zabbix format
        String[] lines = content.split("\n");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();

                switch (key) {
                    case "trigger":
                    case "alert":
                        builder.alertType(normalizeAlertType(value));
                        break;
                    case "severity":
                    case "priority":
                        builder.severity(normalizeSeverity(value));
                        break;
                    case "host":
                        builder.serviceName(value);
                        break;
                    case "message":
                        builder.message(value);
                        break;
                }
            }
        }

        builder.alertId("zabbix_" + System.currentTimeMillis());
        builder.source("zabbix");
        builder.timestamp(LocalDateTime.now().format(ISO_FORMATTER));
        builder.context(content);

        // If no message set, use entire content
        if (builder.build().getMessage() == null) {
            builder.message(content);
        }

        return builder.build();
    }

    /**
     * Detect alert type from content keywords
     */
    private String detectAlertType(String content) {
        String lowerContent = content.toLowerCase();
        for (Map.Entry<String, String> entry : ALERT_TYPE_MAPPING.entrySet()) {
            if (lowerContent.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return "UNKNOWN";
    }

    /**
     * Detect severity from content
     */
    private String detectSeverity(String content) {
        String lower = content.toLowerCase();
        if (lower.contains("critical") || lower.contains("严重") || lower.contains("p0") || lower.contains("p1")) {
            return "CRITICAL";
        }
        if (lower.contains("high") || lower.contains("warning") || lower.contains("警告") || lower.contains("p2")) {
            return "HIGH";
        }
        if (lower.contains("low") || lower.contains("info") || lower.contains("信息") || lower.contains("p4")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    /**
     * Normalize alert type
     */
    private String normalizeAlertType(String alertType) {
        if (alertType == null || alertType.isEmpty()) {
            return "UNKNOWN";
        }
        String normalized = ALERT_TYPE_MAPPING.get(alertType);
        return normalized != null ? normalized : alertType;
    }

    /**
     * Normalize severity
     */
    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isEmpty()) {
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
     * Normalize timestamp to ISO format
     */
    private String normalizeTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now().format(ISO_FORMATTER);
        }

        try {
            // Try parsing as ISO format first
            return LocalDateTime.parse(timestamp, ISO_FORMATTER).format(ISO_FORMATTER);
        } catch (DateTimeParseException e) {
            // Try parsing as Unix timestamp
            try {
                long epoch = Long.parseLong(timestamp);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())
                        .format(ISO_FORMATTER);
            } catch (NumberFormatException e2) {
                logger.warn("Unable to parse timestamp: {}", timestamp);
                return LocalDateTime.now().format(ISO_FORMATTER);
            }
        }
    }

    /**
     * Get string value from map with multiple possible keys
     */
    @SuppressWarnings("unchecked")
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                if (value instanceof String) {
                    return (String) value;
                } else if (value instanceof Map) {
                    // Try to get nested value
                    Map<String, Object> nested = (Map<String, Object>) value;
                    for (String nestedKey : keys) {
                        Object nestedValue = nested.get(nestedKey);
                        if (nestedValue instanceof String) {
                            return (String) nestedValue;
                        }
                    }
                }
                return value.toString();
            }
        }
        return null;
    }
}