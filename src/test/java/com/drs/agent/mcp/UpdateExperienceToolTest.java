package com.drs.agent.mcp;

import com.drs.agent.model.Experience;
import com.drs.agent.service.ExperienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UpdateExperienceTool.
 */
@ExtendWith(MockitoExtension.class)
class UpdateExperienceToolTest {

    @Mock
    private ExperienceService experienceService;

    private UpdateExperienceTool tool;

    @BeforeEach
    void setUp() {
        tool = new UpdateExperienceTool(experienceService);
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("update_experience", annotation.name());
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
        Experience existingExperience = Experience.builder()
                .id(1L)
                .experienceId("exp-12345")
                .problemType("database-timeout")
                .keywords("timeout")
                .confidenceScore(0.8)
                .usageCount(5)
                .build();

        Experience updatedExperience = Experience.builder()
                .id(1L)
                .experienceId("exp-12345")
                .problemType("database-timeout")
                .keywords("timeout,connection,pool")
                .confidenceScore(0.9)
                .usageCount(5)
                .build();

        when(experienceService.updateExperience(eq("exp-12345"), any(Map.class)))
                .thenReturn(Optional.of(updatedExperience));

        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "timeout,connection,pool");
        updates.put("confidenceScore", 0.9);

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", updates);

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue((Boolean) data.get("success"));
        assertEquals("Experience updated successfully", data.get("message"));

        verify(experienceService).updateExperience(eq("exp-12345"), any(Map.class));
    }

    @Test
    @DisplayName("Execute with non-existent experienceId should return failure")
    void testExecuteWithNonExistentExperienceId() {
        when(experienceService.updateExperience(eq("exp-nonexistent"), any(Map.class)))
                .thenReturn(Optional.empty());

        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "new keywords");

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-nonexistent");
        params.put("updates", updates);

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    @DisplayName("Execute without experienceId should return failure")
    void testExecuteWithoutExperienceId() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "new keywords");

        Map<String, Object> params = new HashMap<>();
        params.put("updates", updates);

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("experienceId"));
    }

    @Test
    @DisplayName("Execute without updates should return failure")
    void testExecuteWithoutUpdates() {
        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("updates"));
    }

    @Test
    @DisplayName("Execute with empty updates should return failure")
    void testExecuteWithEmptyUpdates() {
        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", new HashMap<>());

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("updates"));
    }

    @Test
    @DisplayName("Execute with invalid confidenceScore should return failure")
    void testExecuteWithInvalidConfidenceScore() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("confidenceScore", 1.5); // Invalid: > 1.0

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", updates);

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("confidenceScore"));
    }

    @Test
    @DisplayName("Execute with negative confidenceScore should return failure")
    void testExecuteWithNegativeConfidenceScore() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("confidenceScore", -0.5); // Invalid: < 0.0

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", updates);

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("confidenceScore"));
    }

    @Test
    @DisplayName("Validate should succeed with valid parameters")
    void testValidateSuccess() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "new keywords");

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", updates);

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without experienceId")
    void testValidateFailureNoExperienceId() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "new keywords");

        Map<String, Object> params = new HashMap<>();
        params.put("updates", updates);

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("experienceId"));
    }

    @Test
    @DisplayName("Validate should fail with blank experienceId")
    void testValidateFailureBlankExperienceId() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("keywords", "new keywords");

        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "");
        params.put("updates", updates);

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("experienceId"));
    }

    @Test
    @DisplayName("Validate should fail without updates")
    void testValidateFailureNoUpdates() {
        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("updates"));
    }

    @Test
    @DisplayName("Validate should fail with empty updates")
    void testValidateFailureEmptyUpdates() {
        Map<String, Object> params = new HashMap<>();
        params.put("experienceId", "exp-12345");
        params.put("updates", new HashMap<>());

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("updates"));
    }
}