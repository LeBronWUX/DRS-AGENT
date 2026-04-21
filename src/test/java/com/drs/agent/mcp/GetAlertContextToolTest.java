package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.AlertContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GetAlertContextTool.
 */
class GetAlertContextToolTest {

    private GetAlertContextTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetAlertContextTool();
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("get_alert_context", annotation.name());
    }

    @Test
    @DisplayName("Tool should have description")
    void testToolDescription() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation.description());
        assertFalse(annotation.description().isEmpty());
    }

    @Test
    @DisplayName("Execute with valid alertId should return success")
    void testExecuteWithValidAlertId() {
        Map<String, Object> params = Map.of("alertId", "alert-12345");
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) result.getData();
        assertTrue((Boolean) dataMap.get("success"));

        AlertContext alertContext = (AlertContext) dataMap.get("data");
        assertEquals("alert-12345", alertContext.getAlertId());
        assertNotNull(alertContext.getAlertName());
        assertNotNull(alertContext.getSeverity());
        assertNotNull(alertContext.getStatus());
        assertNotNull(alertContext.getRelatedResources());
    }

    @Test
    @DisplayName("Execute should return alert with related resources")
    void testExecuteReturnsRelatedResources() {
        Map<String, Object> params = Map.of("alertId", "alert-12345");
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) result.getData();
        AlertContext alertContext = (AlertContext) dataMap.get("data");

        assertNotNull(alertContext.getRelatedResources());
        assertNotNull(alertContext.getRunbookLinks());

        assertFalse(alertContext.getRelatedResources().isEmpty());

        for (AlertContext.RelatedResource resource : alertContext.getRelatedResources()) {
            assertNotNull(resource.getType());
            assertNotNull(resource.getName());
        }
    }

    @Test
    @DisplayName("Execute without alertId should return failure")
    void testExecuteWithoutAlertId() {
        Map<String, Object> params = Map.of();
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("alertId"));
    }

    @Test
    @DisplayName("Execute with blank alertId should return failure")
    void testExecuteWithBlankAlertId() {
        Map<String, Object> params = Map.of("alertId", "");
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("alertId"));
    }

    @Test
    @DisplayName("Validate should succeed with valid alertId")
    void testValidateSuccess() {
        Map<String, Object> params = Map.of("alertId", "alert-12345");
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without alertId")
    void testValidateFailure() {
        Map<String, Object> params = Map.of();
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("alertId"));
    }

    @Test
    @DisplayName("Validate should fail with blank alertId")
    void testValidateFailureBlank() {
        Map<String, Object> params = Map.of("alertId", "");
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("blank"));
    }
}