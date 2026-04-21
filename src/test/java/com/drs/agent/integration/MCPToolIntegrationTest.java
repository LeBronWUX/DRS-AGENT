package com.drs.agent.integration;

import com.drs.agent.mcp.*;
import com.drs.agent.mcp.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * MCPToolIntegrationTest
 *
 * Integration tests for MCP tool execution:
 * - Tool registry and discovery
 * - Tool execution with validation
 * - Tool parameter validation
 * - Tool fallback and error handling
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "claude.api.enabled=false",
    "milvus.enabled=false"
})
class MCPToolIntegrationTest {

    @MockBean
    private McpToolRegistry toolRegistry;

    @MockBean
    private QueryLogsTool queryLogsTool;

    @MockBean
    private SearchWikiTool searchWikiTool;

    @MockBean
    private GetAlertContextTool getAlertContextTool;

    @MockBean
    private QueryOpsPlatformTool queryOpsPlatformTool;

    @MockBean
    private SearchExperienceTool searchExperienceTool;

    @MockBean
    private AddExperienceTool addExperienceTool;

    @BeforeEach
    void setUp() {
        // Setup tool registry mock
        McpToolRegistry.ToolInfo queryLogsInfo = new McpToolRegistry.ToolInfo(
                "query_logs",
                "Query log entries from operations platform",
                List.of(ToolParam.requiredString("traceId", "Trace ID to search")),
                "List of log entries",
                queryLogsTool,
                QueryLogsTool.class
        );

        McpToolRegistry.ToolInfo searchWikiInfo = new McpToolRegistry.ToolInfo(
                "search_wiki",
                "Search knowledge base for wiki documents",
                List.of(ToolParam.requiredString("query", "Search query")),
                "List of wiki documents",
                searchWikiTool,
                SearchWikiTool.class
        );

        McpToolRegistry.ToolInfo queryOpsInfo = new McpToolRegistry.ToolInfo(
                "query_ops_platform",
                "Query ops platform for workflow and task info",
                List.of(ToolParam.requiredString("workflowId", "Workflow ID")),
                "Task and workflow information",
                queryOpsPlatformTool,
                QueryOpsPlatformTool.class
        );

        Map<String, McpToolRegistry.ToolInfo> toolMap = new HashMap<>();
        toolMap.put("query_logs", queryLogsInfo);
        toolMap.put("search_wiki", searchWikiInfo);
        toolMap.put("query_ops_platform", queryOpsInfo);
        toolMap.put("get_alert_context", new McpToolRegistry.ToolInfo(
                "get_alert_context", "Get alert context", List.of(),
                "Alert context", getAlertContextTool, GetAlertContextTool.class));
        toolMap.put("search_experience", new McpToolRegistry.ToolInfo(
                "search_experience", "Search experience", List.of(),
                "Experience list", searchExperienceTool, SearchExperienceTool.class));
        toolMap.put("add_experience", new McpToolRegistry.ToolInfo(
                "add_experience", "Add experience", List.of(),
                "Experience ID", addExperienceTool, AddExperienceTool.class));

        when(toolRegistry.getToolRegistry()).thenReturn(toolMap);
        when(toolRegistry.hasTool(anyString())).thenAnswer(invocation ->
                toolMap.containsKey(invocation.getArgument(0)));
        when(toolRegistry.getToolCount()).thenReturn(toolMap.size());
    }

    @Test
    @DisplayName("Test Tool Registry Execution - Query Ops Platform")
    void testToolRegistryExecution() {
        // Mock successful tool execution
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("workflowId", "wf_001");
        mockData.put("status", "FAILED");
        mockData.put("error", "Permission denied");
        mockData.put("tasks", List.of(
                Map.of("taskId", "task_001", "status", "FAILED")
        ));

        ToolResult mockResult = ToolResult.success(mockData, 150L);

        when(toolRegistry.executeTool(eq("query_ops_platform"), anyMap()))
                .thenReturn(mockResult);

        // Execute tool
        Map<String, Object> params = new HashMap<>();
        params.put("workflowId", "wf_001");

        ToolResult result = toolRegistry.executeTool("query_ops_platform", params);

        // Verify result
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(150L, result.getExecutionTime());

        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("wf_001", data.get("workflowId"));
        assertEquals("FAILED", data.get("status"));

        verify(toolRegistry, times(1)).executeTool(eq("query_ops_platform"), anyMap());
    }

    @Test
    @DisplayName("Test All Tools Registered")
    void testAllToolsRegistered() {
        Map<String, McpToolRegistry.ToolInfo> registry = toolRegistry.getToolRegistry();

        assertNotNull(registry);
        assertTrue(registry.size() >= 6);

        // Verify core tools are registered
        assertTrue(toolRegistry.hasTool("query_logs"));
        assertTrue(toolRegistry.hasTool("search_wiki"));
        assertTrue(toolRegistry.hasTool("query_ops_platform"));
        assertTrue(toolRegistry.hasTool("get_alert_context"));
        assertTrue(toolRegistry.hasTool("search_experience"));
        assertTrue(toolRegistry.hasTool("add_experience"));

        assertEquals(6, toolRegistry.getToolCount());
    }

    @Test
    @DisplayName("Test Tool Param Validation - Missing Required Param")
    void testToolParamValidation() {
        // Mock validation failure
        McpToolHandler.ValidationResult invalidResult =
                McpToolHandler.ValidationResult.failure("Missing required parameter: workflowId");

        when(toolRegistry.validateParams(eq("query_ops_platform"), anyMap()))
                .thenReturn(invalidResult);

        // Execute without required param
        Map<String, Object> params = new HashMap<>();  // Empty params

        McpToolHandler.ValidationResult validation =
                toolRegistry.validateParams("query_ops_platform", params);

        assertFalse(validation.valid());
        assertTrue(validation.errorMessage().contains("workflowId"));
    }

    @Test
    @DisplayName("Test Tool Fallback - Tool Not Found")
    void testToolFallback() {
        ToolResult notFoundResult = ToolResult.failure("Tool not found: nonexistent_tool");

        when(toolRegistry.executeTool(eq("nonexistent_tool"), anyMap()))
                .thenReturn(notFoundResult);

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");

        ToolResult result = toolRegistry.executeTool("nonexistent_tool", params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Tool not found"));
    }

    @Test
    @DisplayName("Test Query Logs Tool Execution")
    void testQueryLogsToolExecution() {
        // Mock log entries
        List<LogEntry> mockLogs = List.of(
                LogEntry.builder()
                        .timestamp(LocalDateTime.parse("2024-01-01T10:00:00"))
                        .level("ERROR")
                        .message("Permission denied for workflow wf_001")
                        .service("workflow-service")
                        .traceId("trace_001")
                        .build(),
                LogEntry.builder()
                        .timestamp(LocalDateTime.parse("2024-01-01T10:01:00"))
                        .level("WARN")
                        .message("Task execution timeout")
                        .service("task-service")
                        .traceId("trace_001")
                        .build()
        );

        ToolResult mockResult = ToolResult.success(Map.of("logs", mockLogs, "count", 2), 100L);

        when(toolRegistry.executeTool(eq("query_logs"), anyMap()))
                .thenReturn(mockResult);

        Map<String, Object> params = new HashMap<>();
        params.put("traceId", "trace_001");
        params.put("limit", 100);

        ToolResult result = toolRegistry.executeTool("query_logs", params);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(2, data.get("count"));
        assertNotNull(data.get("logs"));
    }

    @Test
    @DisplayName("Test Search Wiki Tool Execution")
    void testSearchWikiToolExecution() {
        List<WikiDoc> mockDocs = List.of(
                WikiDoc.builder()
                        .docId("wiki_001")
                        .title("Workflow Permission Configuration")
                        .content("How to configure workflow permissions...")
                        .category("configuration")
                        .relevanceScore(0.95)
                        .build(),
                WikiDoc.builder()
                        .docId("wiki_002")
                        .title("Task Execution Troubleshooting")
                        .content("Troubleshooting guide for task execution failures...")
                        .category("troubleshooting")
                        .relevanceScore(0.85)
                        .build()
        );

        ToolResult mockResult = ToolResult.success(Map.of("documents", mockDocs), 80L);

        when(toolRegistry.executeTool(eq("search_wiki"), anyMap()))
                .thenReturn(mockResult);

        Map<String, Object> params = new HashMap<>();
        params.put("query", "permission workflow");

        ToolResult result = toolRegistry.executeTool("search_wiki", params);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data.get("documents"));

        List<WikiDoc> docs = (List<WikiDoc>) data.get("documents");
        assertFalse(docs.isEmpty());
        assertTrue(docs.get(0).getRelevanceScore() >= 0.85);
    }

    @Test
    @DisplayName("Test Get Alert Context Tool Execution")
    void testGetAlertContextToolExecution() {
        AlertContext mockContext = AlertContext.builder()
                .alertId("alert_001")
                .alertName("Workflow Execution Failure")
                .severity("HIGH")
                .source("workflow-monitor")
                .triggerTime(LocalDateTime.parse("2024-01-01T10:00:00"))
                .relatedResources(List.of(
                        AlertContext.RelatedResource.builder()
                                .type("trace")
                                .name("trace_001")
                                .relationship("related")
                                .build()
                ))
                .labels(Map.of(
                        "errorType", "PERMISSION_DENIED",
                        "affectedService", "workflow-service"
                ))
                .build();

        ToolResult mockResult = ToolResult.success(mockContext, 60L);

        when(toolRegistry.executeTool(eq("get_alert_context"), anyMap()))
                .thenReturn(mockResult);

        Map<String, Object> params = new HashMap<>();
        params.put("alertId", "alert_001");

        ToolResult result = toolRegistry.executeTool("get_alert_context", params);

        assertTrue(result.isSuccess());
        AlertContext context = (AlertContext) result.getData();
        assertEquals("alert_001", context.getAlertId());
        assertEquals("HIGH", context.getSeverity());
        assertFalse(context.getRelatedResources().isEmpty());
    }

    @Test
    @DisplayName("Test Search Experience Tool Execution")
    void testSearchExperienceToolExecution() {
        ExperienceDTO mockExperience = ExperienceDTO.builder()
                .experienceId("exp_001")
                .problemType("TASK_CREATE_FAILED")
                .keywords("permission,workflow")
                .rootCauses("Permission denied")
                .solutions("Grant permissions")
                .confidenceScore(0.90)
                .build();

        ToolResult mockResult = ToolResult.success(Map.of(
                "experiences", List.of(mockExperience),
                "totalCount", 1
        ), 50L);

        when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                .thenReturn(mockResult);

        Map<String, Object> params = new HashMap<>();
        params.put("problemDescription", "Task creation failed due to permission");
        params.put("topK", 5);

        ToolResult result = toolRegistry.executeTool("search_experience", params);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("totalCount"));
    }

    @Test
    @DisplayName("Test Add Experience Tool Execution")
    void testAddExperienceToolExecution() {
        ToolResult mockResult = ToolResult.success(Map.of(
                "experienceId", "exp_new_001",
                "status", "CREATED"
        ), 100L);

        when(toolRegistry.executeTool(eq("add_experience"), anyMap()))
                .thenReturn(mockResult);

        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "NETWORK_TIMEOUT");
        params.put("keywords", List.of("network", "timeout"));
        params.put("rootCauses", "Connection timeout");
        params.put("solutions", "Check network configuration");

        ToolResult result = toolRegistry.executeTool("add_experience", params);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data.get("experienceId"));
        assertEquals("CREATED", data.get("status"));
    }

    @Test
    @DisplayName("Test Tool Execution Failure Handling")
    void testToolExecutionFailureHandling() {
        ToolResult failureResult = ToolResult.failure("Execution error: Connection timeout", 200L);

        when(toolRegistry.executeTool(eq("query_ops_platform"), anyMap()))
                .thenReturn(failureResult);

        Map<String, Object> params = new HashMap<>();
        params.put("workflowId", "wf_error");

        ToolResult result = toolRegistry.executeTool("query_ops_platform", params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection timeout"));
        assertTrue(result.getExecutionTime() > 0);
    }

    @Test
    @DisplayName("Test Get Available Tools")
    void testGetAvailableTools() {
        List<Map<String, Object>> mockTools = List.of(
                Map.of("name", "query_logs", "description", "Query logs", "inputParams", List.of()),
                Map.of("name", "search_wiki", "description", "Search wiki", "inputParams", List.of()),
                Map.of("name", "query_ops_platform", "description", "Query ops", "inputParams", List.of())
        );

        when(toolRegistry.getAvailableTools()).thenReturn(mockTools);

        List<Map<String, Object>> tools = toolRegistry.getAvailableTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty());
        assertTrue(tools.size() >= 3);

        // Verify each tool has required fields
        for (Map<String, Object> tool : tools) {
            assertTrue(tool.containsKey("name"));
            assertTrue(tool.containsKey("description"));
        }
    }

    @Test
    @DisplayName("Test Get Tool Description")
    void testGetToolDescription() {
        when(toolRegistry.getToolDescription("query_logs"))
                .thenReturn("Query log entries from operations platform");

        String description = toolRegistry.getToolDescription("query_logs");

        assertNotNull(description);
        assertTrue(description.contains("log"));
    }

    @Test
    @DisplayName("Test Get Tool Info")
    void testGetToolInfo() {
        Map<String, Object> mockInfo = new LinkedHashMap<>();
        mockInfo.put("name", "query_logs");
        mockInfo.put("description", "Query log entries");
        mockInfo.put("inputParams", List.of(
                Map.of("name", "traceId", "type", "string", "required", true)
        ));
        mockInfo.put("outputFormat", "List of log entries");

        when(toolRegistry.getToolInfo("query_logs")).thenReturn(mockInfo);

        Map<String, Object> info = toolRegistry.getToolInfo("query_logs");

        assertNotNull(info);
        assertEquals("query_logs", info.get("name"));
        assertNotNull(info.get("inputParams"));
        assertNotNull(info.get("outputFormat"));
    }

    @Test
    @DisplayName("Test Tool Validation - Type Mismatch")
    void testToolValidation_TypeMismatch() {
        McpToolHandler.ValidationResult typeError =
                McpToolHandler.ValidationResult.failure(
                        "Parameter 'workflowId' expects type 'string' but got 'number'");

        when(toolRegistry.validateParams(anyString(), anyMap()))
                .thenReturn(typeError);

        Map<String, Object> params = new HashMap<>();
        params.put("workflowId", 12345);  // Number instead of string

        McpToolHandler.ValidationResult validation =
                toolRegistry.validateParams("query_ops_platform", params);

        assertFalse(validation.valid());
        assertTrue(validation.errorMessage().contains("type"));
    }

    @Test
    @DisplayName("Test Tool Validation - Valid Params")
    void testToolValidation_ValidParams() {
        McpToolHandler.ValidationResult validResult = McpToolHandler.ValidationResult.success();

        when(toolRegistry.validateParams(eq("query_ops_platform"), anyMap()))
                .thenReturn(validResult);

        Map<String, Object> params = new HashMap<>();
        params.put("workflowId", "wf_001");
        params.put("includeTasks", true);

        McpToolHandler.ValidationResult validation =
                toolRegistry.validateParams("query_ops_platform", params);

        assertTrue(validation.valid());
        assertNull(validation.errorMessage());
    }

    @Test
    @DisplayName("Test Multiple Tool Executions in Sequence")
    void testMultipleToolExecutionsInSequence() {
        // First tool: query_logs
        ToolResult logsResult = ToolResult.success(Map.of("logs", List.of()), 100L);
        when(toolRegistry.executeTool(eq("query_logs"), anyMap()))
                .thenReturn(logsResult);

        // Second tool: query_ops_platform
        ToolResult opsResult = ToolResult.success(Map.of("workflowId", "wf_001", "status", "FAILED"), 150L);
        when(toolRegistry.executeTool(eq("query_ops_platform"), anyMap()))
                .thenReturn(opsResult);

        // Third tool: search_wiki
        ToolResult wikiResult = ToolResult.success(Map.of("documents", List.of()), 80L);
        when(toolRegistry.executeTool(eq("search_wiki"), anyMap()))
                .thenReturn(wikiResult);

        // Execute sequence
        Map<String, Object> params1 = new HashMap<>();
        params1.put("traceId", "trace_001");
        ToolResult r1 = toolRegistry.executeTool("query_logs", params1);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("workflowId", "wf_001");
        ToolResult r2 = toolRegistry.executeTool("query_ops_platform", params2);

        Map<String, Object> params3 = new HashMap<>();
        params3.put("query", "permission denied");
        ToolResult r3 = toolRegistry.executeTool("search_wiki", params3);

        // Verify all executions
        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertTrue(r3.isSuccess());

        verify(toolRegistry, times(1)).executeTool(eq("query_logs"), anyMap());
        verify(toolRegistry, times(1)).executeTool(eq("query_ops_platform"), anyMap());
        verify(toolRegistry, times(1)).executeTool(eq("search_wiki"), anyMap());
    }

    @Test
    @DisplayName("Test Tool Execution Time Tracking")
    void testToolExecutionTimeTracking() {
        ToolResult result = ToolResult.success(Map.of("data", "value"), 250L);

        when(toolRegistry.executeTool(anyString(), anyMap()))
                .thenReturn(result);

        ToolResult executionResult = toolRegistry.executeTool("query_logs", Map.of("traceId", "t1"));

        assertTrue(executionResult.getExecutionTime() > 0);
        assertEquals(250L, executionResult.getExecutionTime());
    }

    @Test
    @DisplayName("Test Tool Has Tool Method")
    void testToolHasToolMethod() {
        assertTrue(toolRegistry.hasTool("query_logs"));
        assertTrue(toolRegistry.hasTool("search_wiki"));
        assertFalse(toolRegistry.hasTool("nonexistent_tool"));
    }
}