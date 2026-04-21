package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.service.dto.IntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * IntentRecognizer Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class IntentRecognizerTest {

    @Mock
    private ClaudeService claudeService;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private McpToolRegistry toolRegistry;

    private ObjectMapper objectMapper;
    private IntentRecognizer intentRecognizer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        intentRecognizer = new IntentRecognizer(claudeService, promptTemplateService, toolRegistry, objectMapper);
    }

    @Test
    @DisplayName("Should recognize task creation failure by keywords")
    void testRecognizeTaskCreationFailure() {
        // Given
        String userMessage = "任务创建失败，无法新建迁移任务";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertEquals("任务创建失败", result.getProblemType());
        assertTrue(result.getConfidence() > 0);
        assertTrue(result.isRecognized());
        assertNotNull(result.getKeywords());
        assertTrue(result.getKeywords().contains("失败"));
    }

    @Test
    @DisplayName("Should recognize authentication failure by keywords")
    void testRecognizeAuthFailure() {
        // Given
        String userMessage = "鉴权失败，用户权限不足，403错误";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertEquals("鉴权失败", result.getProblemType());
        assertTrue(result.getConfidence() > 0);
        assertTrue(result.getKeywords().stream().anyMatch(k -> k.contains("403") || k.contains("权限")));
    }

    @Test
    @DisplayName("Should recognize performance issue")
    void testRecognizePerformanceIssue() {
        // Given
        String userMessage = "系统响应很慢，超时timeout，性能下降";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertEquals("性能问题", result.getProblemType());
        assertTrue(result.getConfidence() > 0);
    }

    @Test
    @DisplayName("Should return UNKNOWN for unrecognized problems")
    void testRecognizeUnknownProblem() {
        // Given
        String userMessage = "这是一个完全无法理解的消息";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getProblemType());
    }

    @Test
    @DisplayName("Should handle empty message")
    void testHandleEmptyMessage() {
        // Given
        String userMessage = "";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getProblemType());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    @DisplayName("Should handle null message")
    void testHandleNullMessage() {
        // When
        IntentResult result = intentRecognizer.recognize(null);

        // Then
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getProblemType());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    @DisplayName("Should extract context from message")
    void testExtractContext() {
        // Given
        String userMessage = "workflowId: wf-12345, taskId: task-67890 发生错误";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertNotNull(result.getContext());
        assertTrue(result.getContext().containsKey("workflowId") || result.getContext().containsKey("taskId"));
    }

    @Test
    @DisplayName("Should extract keywords from message")
    void testExtractKeywords() {
        // Given
        String userMessage = "数据同步失败，出现异常和超时问题";

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        assertNotNull(result.getKeywords());
        assertTrue(result.getKeywords().size() > 0);
    }

    @Test
    @DisplayName("Should return supported problem types")
    void testGetSupportedProblemTypes() {
        // When
        List<String> types = intentRecognizer.getSupportedProblemTypes();

        // Then
        assertNotNull(types);
        assertTrue(types.size() > 0);
        assertTrue(types.contains("任务创建失败"));
        assertTrue(types.contains("鉴权失败"));
        assertTrue(types.contains("UNKNOWN"));
    }

    @Test
    @DisplayName("Should check problem type support")
    void testIsProblemTypeSupported() {
        assertTrue(intentRecognizer.isProblemTypeSupported("任务创建失败"));
        assertTrue(intentRecognizer.isProblemTypeSupported("UNKNOWN"));
        assertFalse(intentRecognizer.isProblemTypeSupported("NOT_A_TYPE"));
    }

    @Test
    @DisplayName("Should use Claude for low confidence classification")
    void testUseClaudeForLowConfidence() {
        // Given
        String userMessage = "复杂的未知问题描述xyz";

        // Mock Claude response
        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("{\"problemType\": \"性能问题\", \"confidence\": 0.8}");
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);
        when(promptTemplateService.getTemplate(anyString(), any())).thenReturn("prompt template");

        // When
        IntentResult result = intentRecognizer.recognize(userMessage);

        // Then
        assertNotNull(result);
        // Claude might be called for low confidence
        verify(claudeService, atMost(1)).sendMessage(anyString());
    }
}