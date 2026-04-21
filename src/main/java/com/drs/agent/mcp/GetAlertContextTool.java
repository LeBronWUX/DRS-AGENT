package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.AlertContext;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Get Alert Context Tool
 *
 * Retrieves alert details and related resources for diagnosis.
 * Currently implements a mock version - TODO: integrate with real alerting system.
 */
@Slf4j
@McpTool(
    name = "get_alert_context",
    description = "Get alert context including alert details and related resources for incident diagnosis. Use this tool when investigating alerts and incidents.",
    inputParams = "[{\"name\":\"alertId\",\"type\":\"string\",\"required\":true,\"description\":\"The alert ID to query\"}]",
    outputFormat = "{\"success\":\"boolean\",\"data\":{\"alertId\":\"string\",\"alertName\":\"string\",\"severity\":\"string\",\"status\":\"string\",\"service\":\"string\",\"summary\":\"string\"}}"
)
public class GetAlertContextTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing get_alert_context tool with parameters: {}", parameters);

        try {
            String alertId = (String) parameters.get("alertId");

            if (alertId == null || alertId.isBlank()) {
                return ToolResult.failure("alertId is required");
            }

            // TODO: Integrate with real alerting system (Prometheus AlertManager, PagerDuty, etc.)
            AlertContext alertContext = getMockAlertContext(alertId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("data", alertContext);

            log.info("Get alert context tool executed successfully for alert: {}", alertId);
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing get_alert_context tool", e);
            return ToolResult.failure("Error getting alert context: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object alertId = parameters.get("alertId");
        if (alertId == null) {
            return ValidationResult.failure("Missing required parameter: alertId");
        }
        if (!(alertId instanceof String)) {
            return ValidationResult.failure("Parameter 'alertId' must be a string");
        }
        if (((String) alertId).isBlank()) {
            return ValidationResult.failure("Parameter 'alertId' cannot be blank");
        }
        return ValidationResult.success();
    }

    /**
     * Generate mock alert context for testing.
     * TODO: Replace with real alerting system integration.
     */
    private AlertContext getMockAlertContext(String alertId) {
        List<AlertContext.RelatedResource> relatedResources = Arrays.asList(
                AlertContext.RelatedResource.builder()
                        .type("service")
                        .name("data-processor-service")
                        .relationship("alert_source")
                        .properties(Map.of(
                                "namespace", "production",
                                "replicas", "3",
                                "version", "v1.2.3"
                        ))
                        .build(),
                AlertContext.RelatedResource.builder()
                        .type("database")
                        .name("mysql-primary")
                        .relationship("dependency")
                        .properties(Map.of(
                                "host", "mysql-primary.internal",
                                "port", "3306",
                                "database", "drs_production"
                        ))
                        .build(),
                AlertContext.RelatedResource.builder()
                        .type("node")
                        .name("worker-node-03")
                        .relationship("host")
                        .properties(Map.of(
                                "cpu_usage", "85%",
                                "memory_usage", "78%",
                                "zone", "cn-north-1a"
                        ))
                        .build()
        );

        List<String> runbookLinks = Arrays.asList(
                "https://wiki.internal/runbooks/database-timeout",
                "https://wiki.internal/runbooks/service-escalation"
        );

        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("severity", "critical");
        labels.put("team", "platform");
        labels.put("service", "data-processor");
        labels.put("environment", "production");

        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("summary", "Database connection timeout detected");
        annotations.put("description", "The data-processor-service is experiencing database connection timeouts. " +
                "Last successful connection was 5 minutes ago.");
        annotations.put("playbook", "https://wiki.internal/playbooks/db-timeout");

        return AlertContext.builder()
                .alertId(alertId)
                .alertName("DatabaseConnectionTimeout")
                .severity("critical")
                .status("firing")
                .source("prometheus")
                .service("data-processor-service")
                .environment("production")
                .triggerTime(LocalDateTime.now().minusMinutes(5))
                .summary("Database connection timeout in data-processor-service")
                .description("The data-processor-service has failed to connect to the database multiple times. " +
                        "Connection timeout errors indicate potential network issues, database overload, " +
                        "or resource constraints.")
                .labels(labels)
                .annotations(annotations)
                .relatedResources(relatedResources)
                .runbookLinks(runbookLinks)
                .build();
    }
}