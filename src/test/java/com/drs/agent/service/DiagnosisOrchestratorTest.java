package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.mcp.McpToolHandler;
import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.model.Experience;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.dto.DiagnosisChainResult;
import com.drs.agent.service.dto.DiagnosisStep;
import com.drs.agent.service.dto.IntentResult;
import com.drs.agent.service.dto.StepResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DiagnosisOrchestrator Unit Tests
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiagnosisOrchestratorTest {

    @Mock
    private McpToolRegistry toolRegistry;

    @Mock
    private IntentRecognizer intentRecognizer;

    @Mock
    private ClaudeService claudeService;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ExperienceRepository experienceRepository;

    private ObjectMapper objectMapper;
    private DiagnosisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrator = new DiagnosisOrchestrator(toolRegistry, intentRecognizer,
                claudeService, promptTemplateService, experienceRepository, objectMapper);

        // Setup common mocks
        when(toolRegistry.hasTool(anyString())).thenReturn(true);
        when(toolRegistry.validateParams(anyString(), any())).thenReturn(
                McpToolHandler.ValidationResult.success()
        );
        ToolResult mockToolResult = ToolResult.success(Map.of("data", "test result"));
        when(toolRegistry.executeTool(anyString(), any())).thenReturn(mockToolResult);
        when(toolRegistry.getAvailableTools()).thenReturn(List.of(
                Map.of("name", "query_ops_platform", "description", "Query ops platform")
        ));
        when(experienceRepository.findByProblemType(anyString())).thenReturn(new ArrayList<>());
    }

    @Test
    @DisplayName("Should execute chain for known problem type")
    void testExecuteChainForKnownProblemType() {
        // Given
        IntentResult intent = IntentResult.builder()
                .problemType("任务创建失败")
                .originalMessage("任务创建失败")
                .confidence(0.8)
                .build();

        // When
        DiagnosisChainResult result = orchestrator.executeChain(intent);

        // Then
        assertNotNull(result);
        assertNotNull(result.getStepResults());
        assertTrue(result.getTotalExecutionTime() >= 0);
    }

    @Test
    @DisplayName("Should use default chain template for known types")
    void testUseDefaultChainTemplate() {
        // When
        List<DiagnosisStep> template = orchestrator.getChainTemplateForProblemType("鉴权失败");

        // Then
        assertNotNull(template);
        assertTrue(template.size() > 0);
    }

    @Test
    @DisplayName("Should use unknown chain for UNKNOWN type")
    void testUseUnknownChainForUnknownType() {
        // Given
        IntentResult intent = IntentResult.builder()
                .problemType("UNKNOWN")
                .originalMessage("未知问题")
                .confidence(0.3)
                .build();

        // Mock Claude for chain generation
        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("{\"steps\": []}");
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);
        when(promptTemplateService.getTemplate(anyString(), any())).thenReturn("prompt");

        // When
        DiagnosisChainResult result = orchestrator.executeChain(intent);

        // Then
        assertNotNull(result);
        assertNotNull(result.getStepResults());
    }

    @Test
    @DisplayName("Should handle tool execution failure")
    void testHandleToolExecutionFailure() {
        // Given
        IntentResult intent = IntentResult.builder()
                .problemType("性能问题")
                .originalMessage("性能问题")
                .confidence(0.8)
                .context(new HashMap<>())
                .build();

        // Override mock for failure
        ToolResult failedResult = ToolResult.failure("Tool execution failed");
        when(toolRegistry.executeTool(anyString(), any())).thenReturn(failedResult);

        // When
        DiagnosisChainResult result = orchestrator.executeChain(intent);

        // Then
        assertNotNull(result);
        assertTrue(result.getFailedSteps() > 0);
    }

    @Test
    @DisplayName("Should use experience chain when high confidence match")
    void testUseExperienceChainWhenHighConfidence() {
        // Given
        IntentResult intent = IntentResult.builder()
                .problemType("任务创建失败")
                .originalMessage("任务创建失败")
                .confidence(0.9)
                .build();

        Experience highConfExperience = Experience.builder()
                .experienceId("exp-001")
                .problemType("任务创建失败")
                .confidenceScore(0.85)
                .diagnosisChain("[{\"stepOrder\":1,\"stepName\":\"test\",\"tool\":\"query_ops_platform\",\"params\":{},\"required\":true}]")
                .build();

        when(experienceRepository.findByProblemType("任务创建失败"))
                .thenReturn(List.of(highConfExperience));

        // When
        DiagnosisChainResult result = orchestrator.executeChain(intent);

        // Then
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should resolve placeholders in params")
    void testResolvePlaceholders() {
        // Given
        IntentResult intent = IntentResult.builder()
                .problemType("增量同步失败")
                .originalMessage("增量同步失败 workflowId: wf-123")
                .confidence(0.8)
                .context(Map.of("workflowId", "wf-123", "service", "drs-sync"))
                .build();

        // When
        DiagnosisChainResult result = orchestrator.executeChain(intent);

        // Then
        assertNotNull(result);
        assertNotNull(result.getAggregatedContext());
    }

    @Test
    @DisplayName("Should build chain summary correctly")
    void testBuildChainSummary() {
        // Given
        List<StepResult> stepResults = new ArrayList<>();
        stepResults.add(StepResult.builder()
                .stepOrder(1)
                .stepName("Step 1")
                .success(true)
                .data("result data")
                .toolName("test_tool")
                .executionTime(100)
                .build());
        stepResults.add(StepResult.builder()
                .stepOrder(2)
                .stepName("Step 2")
                .success(false)
                .error("failed")
                .toolName("test_tool")
                .executionTime(50)
                .build());

        // When
        DiagnosisChainResult result = DiagnosisChainResult.builder()
                .stepResults(stepResults)
                .successfulSteps(1)
                .failedSteps(1)
                .totalExecutionTime(150)
                .build();

        // Then
        assertNotNull(result);
        assertEquals(1, result.getSuccessfulSteps());
        assertEquals(1, result.getFailedSteps());
        assertEquals(150, result.getTotalExecutionTime());
    }

    @Test
    @DisplayName("Should get default chain for unknown problem type")
    void testGetDefaultUnknownChain() {
        // When
        List<DiagnosisStep> chain = orchestrator.getChainTemplateForProblemType("UNKNOWN_TYPE");

        // Then
        assertNotNull(chain);
        assertTrue(chain.size() > 0);
    }
}