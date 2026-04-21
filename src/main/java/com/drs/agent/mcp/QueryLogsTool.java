package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Query Logs Tool
 *
 * Queries logs from specified services based on trace ID and other filters.
 * Currently implements a mock version - TODO: integrate with real logging system.
 */
@Slf4j
@McpTool(
    name = "query_logs",
    description = "Query logs from specified services based on trace ID, time range, and keywords. Use this tool to investigate service issues and trace errors.",
    inputParams = "[{\"name\":\"service\",\"type\":\"string\",\"required\":true,\"description\":\"The service name to query logs from\"},{\"name\":\"traceId\",\"type\":\"string\",\"required\":true,\"description\":\"The trace ID to filter logs\"},{\"name\":\"timeRange\",\"type\":\"object\",\"required\":false,\"description\":\"Time range with start and end fields\"},{\"name\":\"keywords\",\"type\":\"array\",\"required\":false,\"description\":\"Keywords to search in logs\"},{\"name\":\"limit\",\"type\":\"number\",\"required\":false,\"description\":\"Maximum number of log entries to return\",\"defaultValue\":100}]",
    outputFormat = "{\"success\":\"boolean\",\"count\":\"number\",\"service\":\"string\",\"traceId\":\"string\",\"data\":\"array\"}"
)
public class QueryLogsTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing query_logs tool with parameters: {}", parameters);

        try {
            String service = (String) parameters.get("service");
            String traceId = (String) parameters.get("traceId");

            if (service == null || service.isBlank()) {
                return ToolResult.failure("service is required");
            }
            if (traceId == null || traceId.isBlank()) {
                return ToolResult.failure("traceId is required");
            }

            @SuppressWarnings("unchecked")
            Map<String, String> timeRange = (Map<String, String>) parameters.get("timeRange");

            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) parameters.get("keywords");

            Integer limit = parameters.containsKey("limit")
                    ? ((Number) parameters.get("limit")).intValue()
                    : 100;

            // TODO: Integrate with real logging system (ELK, Loki, etc.)
            List<LogEntry> logs = getMockLogs(service, traceId, keywords, limit);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("count", logs.size());
            result.put("service", service);
            result.put("traceId", traceId);
            result.put("data", logs);

            log.info("Query logs tool executed successfully, found {} logs", logs.size());
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing query_logs tool", e);
            return ToolResult.failure("Error querying logs: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        if (parameters.get("service") == null) {
            return ValidationResult.failure("Missing required parameter: service");
        }
        if (parameters.get("traceId") == null) {
            return ValidationResult.failure("Missing required parameter: traceId");
        }
        return ValidationResult.success();
    }

    /**
     * Generate mock log entries for testing.
     * TODO: Replace with real logging system integration.
     */
    @SuppressWarnings("unchecked")
    private List<LogEntry> getMockLogs(String service, String traceId, List<String> keywords, int limit) {
        List<LogEntry> logs = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(30);

        String[] levels = {"INFO", "WARN", "ERROR", "DEBUG"};
        String[] messages = {
                "Processing request for trace: " + traceId,
                "Connection established to database",
                "Executing query with parameters",
                "Connection timeout detected",
                "Retrying connection attempt",
                "Maximum retries exceeded",
                "Request failed with timeout error",
                "Rolling back transaction"
        };

        if (keywords != null && !keywords.isEmpty()) {
            // Filter messages by keywords
            for (String keyword : keywords) {
                logs.add(LogEntry.builder()
                        .id("log-" + UUID.randomUUID().toString().substring(0, 8))
                        .service(service)
                        .level("ERROR")
                        .message("Found keyword match: " + keyword + " in processing")
                        .traceId(traceId)
                        .timestamp(baseTime.plusMinutes(1))
                        .thread("worker-1")
                        .logger("com.drs." + service + ".Processor")
                        .exception("java.net.SocketTimeoutException: Connection timed out")
                        .build());
            }
        } else {
            // Generate sample logs
            for (int i = 0; i < Math.min(limit, 10); i++) {
                logs.add(LogEntry.builder()
                        .id("log-" + UUID.randomUUID().toString().substring(0, 8))
                        .service(service)
                        .level(levels[i % levels.length])
                        .message(messages[i % messages.length])
                        .traceId(traceId)
                        .timestamp(baseTime.plusSeconds(i * 30))
                        .thread("worker-" + (i % 3 + 1))
                        .logger("com.drs." + service + ".Processor")
                        .exception(i % 4 == 3 ? "java.net.SocketTimeoutException: Connection timed out" : null)
                        .build());
            }
        }

        return logs;
    }
}