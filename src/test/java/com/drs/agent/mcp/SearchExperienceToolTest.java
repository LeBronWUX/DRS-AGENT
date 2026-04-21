package com.drs.agent.mcp;

import com.drs.agent.model.Experience;
import com.drs.agent.service.ExperienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchExperienceTool.
 */
@ExtendWith(MockitoExtension.class)
class SearchExperienceToolTest {

    @Mock
    private ExperienceService experienceService;

    private SearchExperienceTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchExperienceTool(experienceService);
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("search_experience", annotation.name());
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
        Experience exp1 = Experience.builder()
                .experienceId("exp-001")
                .problemType("database-timeout")
                .keywords("timeout,connection")
                .rootCauses("Pool exhausted")
                .solutions("Increase pool")
                .confidenceScore(0.9)
                .usageCount(5)
                .build();

        Experience exp2 = Experience.builder()
                .experienceId("exp-002")
                .problemType("service-crash")
                .keywords("crash,out-of-memory")
                .rootCauses("OOM")
                .solutions("Add memory limit")
                .confidenceScore(0.85)
                .usageCount(3)
                .build();

        List<Experience> mockExperiences = Arrays.asList(exp1, exp2);
        when(experienceService.searchSimilarExperiences(anyString(), anyInt()))
                .thenReturn(mockExperiences);

        Map<String, Object> params = Map.of(
                "problemDescription", "Database connection timeout error",
                "topK", 5
        );

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue((Boolean) data.get("success"));
        assertEquals(2, ((Number) data.get("count")).intValue());
        assertEquals("Database connection timeout error", data.get("query"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("data");
        assertEquals(2, results.size());

        verify(experienceService).searchSimilarExperiences("Database connection timeout error", 5);
    }

    @Test
    @DisplayName("Execute with default topK should use 5")
    void testExecuteWithDefaultTopK() {
        when(experienceService.searchSimilarExperiences(anyString(), eq(5)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> params = Map.of(
                "problemDescription", "Some problem description"
        );

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        verify(experienceService).searchSimilarExperiences("Some problem description", 5);
    }

    @Test
    @DisplayName("Execute with topK exceeding limit should cap to 20")
    void testExecuteTopKCapped() {
        when(experienceService.searchSimilarExperiences(anyString(), eq(20)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> params = Map.of(
                "problemDescription", "Some problem",
                "topK", 100
        );

        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        verify(experienceService).searchSimilarExperiences("Some problem", 20);
    }

    @Test
    @DisplayName("Execute without problemDescription should return failure")
    void testExecuteWithoutProblemDescription() {
        Map<String, Object> params = Map.of("topK", 5);

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("problemDescription"));
    }

    @Test
    @DisplayName("Execute with blank problemDescription should return failure")
    void testExecuteWithBlankProblemDescription() {
        Map<String, Object> params = Map.of("problemDescription", "");

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("problemDescription"));
    }

    @Test
    @DisplayName("Validate should succeed with valid parameters")
    void testValidateSuccess() {
        Map<String, Object> params = Map.of(
                "problemDescription", "Database timeout error"
        );

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without problemDescription")
    void testValidateFailureNoProblemDescription() {
        Map<String, Object> params = Map.of();

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("problemDescription"));
    }

    @Test
    @DisplayName("Validate should fail with blank problemDescription")
    void testValidateFailureBlankProblemDescription() {
        Map<String, Object> params = Map.of("problemDescription", "");

        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("problemDescription"));
    }
}