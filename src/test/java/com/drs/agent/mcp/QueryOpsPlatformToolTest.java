package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryOpsPlatformTool.
 */
class QueryOpsPlatformToolTest {

    private QueryOpsPlatformTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryOpsPlatformTool();
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("query_ops_platform", annotation.name());
    }

    @Test
    @DisplayName("Tool should have description")
    void testToolDescription() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation.description());
        assertFalse(annotation.description().isEmpty());
    }

    @Test
    @DisplayName("Execute with valid workflowId should return success")
    void testExecuteWithValidWorkflowId() {
        Map<String, Object> params = Map.of("workflowId", "wf-12345");
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) result.getData();
        assertTrue((Boolean) dataMap.get("success"));

        TaskInfo taskInfo = (TaskInfo) dataMap.get("data");
        assertNotNull(taskInfo);
        assertEquals("wf-12345", taskInfo.getWorkflowId());
        assertNotNull(taskInfo.getStatus());
        assertNotNull(taskInfo.getTraceId());
    }

    @Test
    @DisplayName("Execute without workflowId should return failure")
    void testExecuteWithoutWorkflowId() {
        Map<String, Object> params = Map.of();
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("workflowId"));
    }

    @Test
    @DisplayName("Execute with blank workflowId should return failure")
    void testExecuteWithBlankWorkflowId() {
        Map<String, Object> params = Map.of("workflowId", "");
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @DisplayName("Validate should succeed with valid parameters")
    void testValidateSuccess() {
        Map<String, Object> params = Map.of("workflowId", "wf-12345");
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("Validate should fail without workflowId")
    void testValidateFailure() {
        Map<String, Object> params = Map.of();
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("workflowId"));
    }
}