package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.ExperienceDTO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddExperienceTool.
 */
@ExtendWith(MockitoExtension.class)
class AddExperienceToolTest {

    @Mock
    private ExperienceService experienceService;

    private AddExperienceTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddExperienceTool(experienceService);
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("add_experience", annotation.name());
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
        // Mock experience
        Experience mockExperience = Experience.builder()
                .experienceId("exp-12345")
                .problemType("database-timeout")
                .keywords("timeout,connection,database")
                .diagnosisChain("Check logs -> Check metrics")
                .rootCauses("Connection pool exhausted")
                .solutions("Increase pool size")
                .confidenceScore(1.0)
                .usageCount(0)
                .build();

        when(experienceService.addExperience(
                eq("database-timeout"),
                eq("timeout,connection,database"),
                eq("Check logs -> Check metrics"),
                eq("Connection pool exhausted"),
                eq("Increase pool size")
        )).thenReturn(mockExperience);

        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "database-timeout");
        params.put("keywords", "timeout,connection,database");
        params.put("diagnosisChain", "Check logs -> Check metrics");
        params.put("rootCauses", "Connection pool exhausted");
        params.put("solutions", "Increase pool size");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) result.getData();
        assertTrue((Boolean) dataMap.get("success"));
        assertEquals("Experience added successfully", dataMap.get("message"));

        ExperienceDTO experienceDTO = (ExperienceDTO) dataMap.get("data");
        assertEquals("exp-12345", experienceDTO.getExperienceId());
        assertEquals("database-timeout", experienceDTO.getProblemType());

        verify(experienceService).addExperience(
                "database-timeout",
                "timeout,connection,database",
                "Check logs -> Check metrics",
                "Connection pool exhausted",
                "Increase pool size"
        );
    }

    @Test
    @DisplayName("Execute with minimal parameters should return success")
    void testExecuteWithMinimalParameters() {
        Experience mockExperience = Experience.builder()
                .experienceId("exp-12345")
                .problemType("database-timeout")
                .keywords("timeout")
                .confidenceScore(1.0)
                .usageCount(0)
                .build();

        when(experienceService.addExperience(
                eq("database-timeout"),
                eq("timeout"),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(mockExperience);

        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "database-timeout");
        params.put("keywords", "timeout");

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        verify(experienceService).addExperience(
                "database-timeout",
                "timeout",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Execute without problemType should return failure")
    void testExecuteWithoutProblemType() {
        Map<String, Object> params = new HashMap<>();
        params.put("keywords", "timeout");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("problemType"));
    }

    @Test
    @DisplayName("Execute without keywords should return failure")
    void testExecuteWithoutKeywords() {
        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "database-timeout");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("keywords"));
    }

    @Test
    @DisplayName("Validate should succeed with valid parameters")
    void testValidateSuccess() {
        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "database-timeout");
        params.put("keywords", "timeout");

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without problemType")
    void testValidateFailureNoProblemType() {
        Map<String, Object> params = new HashMap<>();
        params.put("keywords", "timeout");

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("problemType"));
    }

    @Test
    @DisplayName("Validate should fail with blank problemType")
    void testValidateFailureBlankProblemType() {
        Map<String, Object> params = new HashMap<>();
        params.put("problemType", "");
        params.put("keywords", "timeout");

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("problemType"));
    }
}