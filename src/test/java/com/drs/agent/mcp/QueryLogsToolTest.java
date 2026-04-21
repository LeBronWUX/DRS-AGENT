package com.drs.agent.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryLogsTool.
 */
class QueryLogsToolTest {

    private QueryLogsTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryLogsTool();
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("query_logs", annotation.name());
    }

    @Test
    @DisplayName("Tool should have description")
    void testToolDescription() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation.description());
        assertFalse(annotation.description().isEmpty());
    }

    @Test
    @DisplayName("Execute with valid parameters should return success")
    void testExecuteWithValidParameters() {
        Map<String, Object> params = Map.of(
                "service", "data-processor",
                "traceId", "trace-abc123"
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue((Boolean) data.get("success"));
        assertEquals("data-processor", data.get("service"));
        assertEquals("trace-abc123", data.get("traceId"));
    }

    @Test
    @DisplayName("Execute with keywords should return filtered logs")
    void testExecuteWithKeywords() {
        Map<String, Object> params = Map.of(
                "service", "data-processor",
                "traceId", "trace-abc123",
                "keywords", Arrays.asList("timeout", "error")
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("Execute with limit should respect limit parameter")
    void testExecuteWithLimit() {
        Map<String, Object> params = Map.of(
                "service", "data-processor",
                "traceId", "trace-abc123",
                "limit", 5
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Integer count = ((Number) data.get("count")).intValue();
        assertTrue(count <= 5);
    }

    @Test
    @DisplayName("Execute without service should return failure")
    void testExecuteWithoutService() {
        Map<String, Object> params = Map.of("traceId", "trace-abc123");
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("service"));
    }

    @Test
    @DisplayName("Execute without traceId should return failure")
    void testExecuteWithoutTraceId() {
        Map<String, Object> params = Map.of("service", "data-processor");
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("traceId"));
    }

    @Test
    @DisplayName("Validate should succeed with valid parameters")
    void testValidateSuccess() {
        Map<String, Object> params = Map.of(
                "service", "data-processor",
                "traceId", "trace-abc123"
        );
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without service")
    void testValidateFailureNoService() {
        Map<String, Object> params = Map.of("traceId", "trace-abc123");
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("service"));
    }
}