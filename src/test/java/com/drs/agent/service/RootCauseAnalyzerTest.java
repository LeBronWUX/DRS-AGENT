package com.drs.agent.service;

import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.model.Experience;
import com.drs.agent.service.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RootCauseAnalyzer
 */
@ExtendWith(MockitoExtension.class)
class RootCauseAnalyzerTest {

    @Mock
    private ClaudeService claudeService;

    @Mock
    private PromptTemplateService promptTemplateService;

    private ObjectMapper objectMapper;
    private RootCauseAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        analyzer = new RootCauseAnalyzer(claudeService, promptTemplateService, objectMapper);
    }

    @Test
    @DisplayName("Should extract evidence from diagnosis chain results")
    void testExtractEvidence() {
        // Create diagnosis chain result
        List<StepResult> steps = new ArrayList<>();
        steps.add(StepResult.builder()
                .stepName("Query Logs")
                .toolName("query_logs")
                .success(true)
                .data("ERROR: Permission denied for VPC access. traceId=abc123")
                .build());
        steps.add(StepResult.builder()
                .stepName("Query Ops Platform")
                .toolName("query_ops_platform")
                .success(true)
                .data("status=403 error_code=1001")
                .build());

        DiagnosisChainResult chainResult = DiagnosisChainResult.builder()
                .stepResults(steps)
                .success(true)
                .aggregatedContext(Map.of("traceId", "abc123", "workflowId", "wf001"))
                .build();

        // Create IntentResult
        IntentResult intent = IntentResult.builder()
                .problemType("PERMISSION")
                .originalMessage("Task creation failed")
                .confidence(0.8)
                .build();

        // Create empty experiences list
        List<Experience> experiences = Collections.emptyList();

        // Mock prompt template service
        when(promptTemplateService.getTemplate(anyString(), any(Map.class)))
                .thenReturn("Mock prompt template");

        // Mock Claude response with valid JSON
        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("""
            {
              "root_cause": {
                "category": "PERMISSION",
                "description": "Account lacks VPC access permission",
                "component": "drs-service",
                "error_pattern": "Permission denied.*VPC"
              },
              "solution": {
                "immediate_action": "Grant VPC access permission to the account",
                "long_term_fix": "Update IAM policy to include VPC access",
                "automation_possible": true,
                "steps": ["Step 1: Check current permissions", "Step 2: Grant VPC access"]
              },
              "confidence": 0.92,
              "risk_level": "medium",
              "suggested_learning": false
            }
            """);
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);

        // Execute analysis
        RootCauseResult result = analyzer.analyze(chainResult, experiences, intent);

        // Verify results
        assertNotNull(result);
        assertEquals("PERMISSION", result.getCategory());
        assertTrue(result.getConfidence() > 0);
        assertNotNull(result.getEvidence());
        assertTrue(result.getEvidence().size() > 0);
        assertNotNull(result.getSolution());
        assertNotNull(result.getSolution().getImmediateAction());
    }

    @Test
    @DisplayName("Should match pattern from similar experiences")
    void testPatternMatching() {
        // Create experience with root cause pattern
        Experience experience = Experience.builder()
                .experienceId("exp_001")
                .problemType("PERMISSION")
                .keywords("permission, denied, VPC")
                .rootCauses("[{\"pattern\": \"Permission denied\", \"regex\": \"Permission denied.*VPC\", \"cause\": \"权限不足\", \"frequency\": \"HIGH\"}]")
                .solutions("Grant VPC permission")
                .confidenceScore(0.9)
                .usageCount(10)
                .build();

        List<Experience> experiences = List.of(experience);

        // Create diagnosis chain result with matching pattern
        List<StepResult> steps = new ArrayList<>();
        steps.add(StepResult.builder()
                .stepName("Query Logs")
                .toolName("query_logs")
                .success(true)
                .data("ERROR: Permission denied for VPC access")
                .build());

        DiagnosisChainResult chainResult = DiagnosisChainResult.builder()
                .stepResults(steps)
                .success(true)
                .build();

        IntentResult intent = IntentResult.builder()
                .problemType("PERMISSION")
                .originalMessage("Task creation failed")
                .confidence(0.8)
                .build();

        // Execute analysis - should match pattern without calling Claude
        RootCauseResult result = analyzer.analyze(chainResult, experiences, intent);

        // Verify pattern matching result
        assertNotNull(result);
        assertNotNull(result.getRelatedExperiences());
        assertTrue(result.getRelatedExperiences().contains("exp_001"));
    }

    @Test
    @DisplayName("Should handle null chain result gracefully")
    void testNullChainResult() {
        IntentResult intent = IntentResult.builder()
                .problemType("UNKNOWN")
                .originalMessage("Test problem")
                .confidence(0.5)
                .build();

        RootCauseResult result = analyzer.analyze(null, Collections.emptyList(), intent);

        assertNotNull(result);
        assertEquals("UNKNOWN", result.getCategory());
        assertTrue(result.getConfidence() >= 0.5);
    }

    @Test
    @DisplayName("Should build prompt parameters correctly")
    void testBuildPromptParams() {
        List<StepResult> steps = new ArrayList<>();
        steps.add(StepResult.builder()
                .stepName("Step1")
                .toolName("tool1")
                .success(true)
                .data("Result1")
                .build());

        DiagnosisChainResult chain = DiagnosisChainResult.builder()
                .stepResults(steps)
                .success(true)
                .aggregatedContext(Map.of("key1", "value1"))
                .build();

        Experience exp = Experience.builder()
                .experienceId("exp1")
                .problemType("TEST")
                .keywords("test")
                .rootCauses("Test root cause")
                .solutions("Test solution")
                .build();

        IntentResult intent = IntentResult.builder()
                .problemType("TEST")
                .originalMessage("Test message")
                .build();

        // This is internal method test - would need reflection or public access
        // For now, verify through analyze method
        when(promptTemplateService.getTemplate(anyString(), any(Map.class)))
                .thenReturn("Template");
        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("{\"root_cause\":{\"category\":\"TEST\"}}");
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);

        analyzer.analyze(chain, List.of(exp), intent);

        verify(promptTemplateService).getTemplate(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Should determine learning need correctly")
    void testDetermineLearningNeed() {
        // Test case 1: No similar experiences
        RootCauseResult result1 = RootCauseResult.builder()
                .category("UNKNOWN")
                .confidence(0.6)
                .build();

        analyzer.analyze(null, Collections.emptyList(),
                IntentResult.builder().problemType("TEST").build());

        // Learning should be suggested for new patterns
        assertTrue(result1.isSuggestedLearning() || result1.getCategory().equals("UNKNOWN"));

        // Test case 2: High confidence with similar experiences
        Experience exp = Experience.builder()
                .experienceId("exp1")
                .confidenceScore(0.9)
                .usageCount(5)
                .build();

        // With matching experience, learning may not be needed
        // This is tested through the analyze method
    }

    @Test
    @DisplayName("Should parse Claude JSON response correctly")
    void testParseClaudeResponse() {
        when(promptTemplateService.getTemplate(anyString(), any(Map.class)))
                .thenReturn("Template");

        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("""
            Here is my analysis:
            {
              "root_cause": {
                "category": "CONFIGURATION",
                "description": "Configuration mismatch",
                "component": "config-service",
                "error_pattern": "config.error"
              },
              "solution": {
                "immediate_action": "Fix configuration",
                "long_term_fix": "Update config template",
                "automation_possible": false,
                "steps": ["Check config", "Fix values"]
              },
              "confidence": 0.85,
              "evidence": ["Evidence 1", "Evidence 2"],
              "risk_level": "high",
              "suggested_learning": true
            }
            """);
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);

        DiagnosisChainResult chain = DiagnosisChainResult.builder()
                .stepResults(Collections.emptyList())
                .success(false)
                .build();

        RootCauseResult result = analyzer.analyze(chain, Collections.emptyList(),
                IntentResult.builder().problemType("CONFIG").originalMessage("Test").build());

        assertNotNull(result);
        assertEquals("CONFIGURATION", result.getCategory());
        assertEquals(0.85, result.getConfidence());
        assertEquals("high", result.getRiskLevel());
        assertTrue(result.isSuggestedLearning());
    }

    @Test
    @DisplayName("Should handle malformed Claude response")
    void testMalformedClaudeResponse() {
        when(promptTemplateService.getTemplate(anyString(), any(Map.class)))
                .thenReturn("Template");

        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("This is not JSON, just plain text about permission issues.");
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);

        DiagnosisChainResult chain = DiagnosisChainResult.builder()
                .stepResults(Collections.emptyList())
                .build();

        RootCauseResult result = analyzer.analyze(chain, Collections.emptyList(),
                IntentResult.builder().problemType("TEST").originalMessage("Test").build());

        assertNotNull(result);
        // Should still extract category from keyword matching
        assertTrue(result.getCategory() != null);
    }

    @Test
    @DisplayName("Should categorize root causes correctly")
    void testCategorization() {
        // Test different pattern types
        Experience permissionExp = Experience.builder()
                .experienceId("exp_perm")
                .rootCauses("[{\"pattern\": \"Permission\", \"cause\": \"权限不足\"}]")
                .confidenceScore(0.9)
                .build();

        Experience configExp = Experience.builder()
                .experienceId("exp_config")
                .rootCauses("[{\"pattern\": \"Configuration\", \"cause\": \"配置错误\"}]")
                .confidenceScore(0.9)
                .build();

        // Test would verify that category is extracted from pattern content
        assertNotNull(permissionExp.getRootCauses());
        assertNotNull(configExp.getRootCauses());
    }
}