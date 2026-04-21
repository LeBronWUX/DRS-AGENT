package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Query Operations Platform Tool
 *
 * Queries the operations platform to get task workflow information.
 * Currently implements a mock version - TODO: integrate with real API.
 */
@Slf4j
@McpTool(
    name = "query_ops_platform",
    description = "Query the operations platform to get task workflow information including status, error codes, and trace IDs. Use this tool to investigate workflow execution issues.",
    inputParams = "[{\"name\":\"workflowId\",\"type\":\"string\",\"required\":true,\"description\":\"The workflow ID to query\"}]",
    outputFormat = "{\"success\":\"boolean\",\"data\":{\"workflowId\":\"string\",\"taskId\":\"string\",\"taskName\":\"string\",\"status\":\"string\",\"errorCode\":\"string\",\"errorMessage\":\"string\",\"traceId\":\"string\"}}"
)
public class QueryOpsPlatformTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing query_ops_platform tool with parameters: {}", parameters);

        try {
            String workflowId = (String) parameters.get("workflowId");

            if (workflowId == null || workflowId.isBlank()) {
                return ToolResult.failure("workflowId is required");
            }

            // TODO: Integrate with real operations platform API
            // Currently returning mock data
            TaskInfo taskInfo = getMockTaskInfo(workflowId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", taskInfo);

            log.info("Query ops platform tool executed successfully for workflow: {}", workflowId);
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing query_ops_platform tool", e);
            return ToolResult.failure("Error querying operations platform: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object workflowId = parameters.get("workflowId");
        if (workflowId == null) {
            return ValidationResult.failure("Missing required parameter: workflowId");
        }
        if (!(workflowId instanceof String)) {
            return ValidationResult.failure("Parameter 'workflowId' must be a string");
        }
        if (((String) workflowId).isBlank()) {
            return ValidationResult.failure("Parameter 'workflowId' cannot be blank");
        }
        return ValidationResult.success();
    }

    /**
     * Generate mock task info for testing.
     * TODO: Replace with real API call.
     */
    private TaskInfo getMockTaskInfo(String workflowId) {
        return TaskInfo.builder()
                .workflowId(workflowId)
                .taskId("task-" + workflowId.hashCode())
                .taskName("Data Processing Task")
                .status("FAILED")
                .errorCode("ERR_TIMEOUT")
                .errorMessage("Connection timeout after 30 seconds")
                .traceId("trace-" + System.currentTimeMillis())
                .startTime(LocalDateTime.now().minusMinutes(30))
                .endTime(LocalDateTime.now().minusMinutes(25))
                .serviceName("data-processor-service")
                .environment("production")
                .metadata("{\"retryCount\": 3, \"maxRetries\": 5}")
                .build();
    }
}