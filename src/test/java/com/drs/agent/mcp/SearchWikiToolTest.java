package com.drs.agent.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchWikiTool.
 */
class SearchWikiToolTest {

    private SearchWikiTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchWikiTool();
    }

    @Test
    @DisplayName("Tool should have correct name via annotation")
    void testToolAnnotation() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation);
        assertEquals("search_wiki", annotation.name());
    }

    @Test
    @DisplayName("Tool should have description")
    void testToolDescription() {
        McpTool annotation = tool.getClass().getAnnotation(McpTool.class);
        assertNotNull(annotation.description());
        assertFalse(annotation.description().isEmpty());
    }

    @Test
    @DisplayName("Execute with valid keywords should return success")
    void testExecuteWithValidKeywords() {
        Map<String, Object> params = Map.of(
                "keywords", Arrays.asList("database", "timeout")
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue((Boolean) data.get("success"));
        assertNotNull(data.get("data"));
    }

    @Test
    @DisplayName("Execute with category filter should return filtered results")
    void testExecuteWithCategoryFilter() {
        Map<String, Object> params = Map.of(
                "keywords", Arrays.asList("timeout"),
                "category", "troubleshooting"
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data.get("data"));
    }

    @Test
    @DisplayName("Execute with limit should respect limit parameter")
    void testExecuteWithLimit() {
        Map<String, Object> params = Map.of(
                "keywords", Arrays.asList("timeout"),
                "limit", 2
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Integer count = ((Number) data.get("count")).intValue();
        assertTrue(count <= 2);
    }

    @Test
    @DisplayName("Execute without keywords should return failure")
    void testExecuteWithoutKeywords() {
        Map<String, Object> params = Map.of();
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("keywords"));
    }

    @Test
    @DisplayName("Execute with empty keywords should return failure")
    void testExecuteWithEmptyKeywords() {
        Map<String, Object> params = Map.of("keywords", Arrays.asList());
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("keywords"));
    }

    @Test
    @DisplayName("Validate should succeed with valid keywords")
    void testValidateSuccess() {
        Map<String, Object> params = Map.of(
                "keywords", Arrays.asList("database", "timeout")
        );
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Validate should fail without keywords")
    void testValidateFailureNoKeywords() {
        Map<String, Object> params = Map.of();
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("keywords"));
    }

    @Test
    @DisplayName("Validate should fail with empty keywords")
    void testValidateFailureEmptyKeywords() {
        Map<String, Object> params = Map.of("keywords", Arrays.asList());
        McpToolHandler.ValidationResult result = tool.validate(params);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("keywords"));
    }
}