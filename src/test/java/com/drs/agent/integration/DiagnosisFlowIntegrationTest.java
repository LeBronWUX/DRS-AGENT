package com.drs.agent.integration;

import com.drs.agent.controller.DiagnosisController;
import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.model.FeedbackRequest;
import com.drs.agent.service.DiagnosisService;
import com.drs.agent.service.ExperienceLearningService;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.repository.ExperienceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DiagnosisFlowIntegrationTest
 *
 * Integration tests for the complete diagnosis flow:
 * - Full diagnosis request/response cycle
 * - High confidence experience matching
 * - Low confidence triggering learning
 * - Feedback submission and processing
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "claude.api.enabled=false",
    "milvus.enabled=false"
})
class DiagnosisFlowIntegrationTest {

    @Autowired
    private DiagnosisController diagnosisController;

    @MockBean
    private DiagnosisService diagnosisService;

    @MockBean
    private ExperienceLearningService learningService;

    @MockBean
    private DiagnosisSessionRepository sessionRepository;

    @MockBean
    private ExperienceRepository experienceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private DiagnosisResponse mockResponse;

    @BeforeEach
    void setUp() {
        // Setup mock diagnosis response
        mockResponse = DiagnosisResponse.builder()
                .sessionId("session_test_001")
                .problemType("TASK_CREATE_FAILED")
                .rootCause("Permission denied for workflow execution")
                .confidence(0.85)
                .solution("Check user permissions and retry workflow")
                .status("COMPLETED")
                .diagnosisChain(List.of(
                        DiagnosisResponse.DiagnosisStep.builder()
                                .stepId("step_0")
                                .stepName("Query Logs")
                                .description("Query recent logs for workflow wf_001")
                                .result("Found permission error in logs")
                                .status("COMPLETED")
                                .executionTimeMs(150L)
                                .build(),
                        DiagnosisResponse.DiagnosisStep.builder()
                                .stepId("step_1")
                                .stepName("Query Ops Platform")
                                .description("Query ops platform for workflow details")
                                .result("Workflow status: FAILED, error: permission denied")
                                .status("COMPLETED")
                                .executionTimeMs(200L)
                                .build()
                ))
                .similarExperiences(List.of(
                        DiagnosisResponse.ExperienceMatch.builder()
                                .experienceId("exp_001")
                                .problemType("TASK_CREATE_FAILED")
                                .similarity(0.92)
                                .summary("permission, workflow, failed")
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("Test Full Diagnosis Flow - Task Create Failed")
    void testFullDiagnosisFlow_TaskCreateFailed() {
        // 1. Create diagnosis request
        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("任务创建失败 workflowId=wf_001")
                .context("{\"workflowId\": \"wf_001\", \"error\": \"permission denied\"}")
                .userId("user_test")
                .priority("HIGH")
                .build();

        // 2. Mock service response
        when(diagnosisService.diagnose(any(DiagnosisRequest.class)))
                .thenReturn(mockResponse);

        // 3. Call diagnosis endpoint
        ResponseEntity<DiagnosisResponse> response = diagnosisController.diagnose(request);

        // 4. Verify response
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        DiagnosisResponse body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.getSessionId());
        assertEquals("TASK_CREATE_FAILED", body.getProblemType());
        assertNotNull(body.getRootCause());
        assertNotNull(body.getSolution());
        assertTrue(body.getConfidence() > 0.7);
        assertEquals("COMPLETED", body.getStatus());

        // 5. Verify diagnosis chain
        assertNotNull(body.getDiagnosisChain());
        assertFalse(body.getDiagnosisChain().isEmpty());

        // 6. Verify similar experiences
        assertNotNull(body.getSimilarExperiences());
        assertFalse(body.getSimilarExperiences().isEmpty());

        // 7. Verify service was called
        verify(diagnosisService, times(1)).diagnose(any(DiagnosisRequest.class));
    }

    @Test
    @DisplayName("Test Diagnosis with High Confidence Experience Match")
    void testDiagnosisWithHighConfidenceExperience() {
        // Setup response with high confidence (> 0.85)
        DiagnosisResponse highConfidenceResponse = DiagnosisResponse.builder()
                .sessionId("session_high_conf_001")
                .problemType("DATABASE_TIMEOUT")
                .rootCause("Database connection pool exhausted")
                .confidence(0.95)
                .solution("Increase connection pool size and monitor usage")
                .status("COMPLETED")
                .similarExperiences(List.of(
                        DiagnosisResponse.ExperienceMatch.builder()
                                .experienceId("exp_002")
                                .problemType("DATABASE_TIMEOUT")
                                .similarity(0.98)
                                .summary("database, timeout, connection pool")
                                .build()
                ))
                .build();

        when(diagnosisService.diagnose(any(DiagnosisRequest.class)))
                .thenReturn(highConfidenceResponse);

        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("数据库连接超时 error_code=500")
                .build();

        ResponseEntity<DiagnosisResponse> response = diagnosisController.diagnose(request);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getConfidence() >= 0.85);
        assertFalse(response.getBody().getSimilarExperiences().isEmpty());
        assertTrue(response.getBody().getSimilarExperiences().get(0).getSimilarity() >= 0.85);
    }

    @Test
    @DisplayName("Test Diagnosis with Low Confidence Needs Learning")
    void testDiagnosisWithLowConfidenceNeedLearning() {
        // Setup response with low confidence (< 0.70)
        DiagnosisResponse lowConfidenceResponse = DiagnosisResponse.builder()
                .sessionId("session_low_conf_001")
                .problemType("UNKNOWN")
                .rootCause("Unable to determine root cause - new problem pattern")
                .confidence(0.45)
                .solution("Manual investigation required")
                .status("COMPLETED")
                .similarExperiences(List.of())  // No matching experiences
                .build();

        when(diagnosisService.diagnose(any(DiagnosisRequest.class)))
                .thenReturn(lowConfidenceResponse);

        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("未知错误类型 new_error_pattern")
                .build();

        ResponseEntity<DiagnosisResponse> response = diagnosisController.diagnose(request);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getConfidence() < 0.70);
        assertTrue(response.getBody().getSimilarExperiences().isEmpty());
        assertEquals("UNKNOWN", response.getBody().getProblemType());
    }

    @Test
    @DisplayName("Test Diagnosis Feedback Flow")
    void testDiagnosisFeedbackFlow() {
        // 1. First get a diagnosis result
        when(diagnosisService.diagnose(any(DiagnosisRequest.class)))
                .thenReturn(mockResponse);

        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("任务创建失败 workflowId=wf_001")
                .build();

        ResponseEntity<DiagnosisResponse> diagnosisResponse = diagnosisController.diagnose(request);
        String sessionId = diagnosisResponse.getBody().getSessionId();

        // 2. Submit feedback
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(5)
                .isCorrect(true)
                .comment("诊断准确，解决方案有效")
                .userId("user_test")
                .build();

        // Mock feedback submission
        when(diagnosisService.submitFeedback(eq(sessionId), any(FeedbackRequest.class)))
                .thenReturn(Optional.of(mockResponse));

        ResponseEntity<Map<String, Object>> feedbackResponse =
                diagnosisController.submitFeedback(sessionId, feedback);

        // 3. Verify feedback response
        assertNotNull(feedbackResponse);
        assertEquals(200, feedbackResponse.getStatusCode().value());

        Map<String, Object> body = feedbackResponse.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals(sessionId, body.get("sessionId"));

        // 4. Verify feedback was submitted
        verify(diagnosisService, times(1))
                .submitFeedback(eq(sessionId), any(FeedbackRequest.class));
    }

    @Test
    @DisplayName("Test Diagnosis Feedback with Correction")
    void testDiagnosisFeedbackWithCorrection() {
        // Setup mock for feedback with correction
        DiagnosisResponse correctedResponse = DiagnosisResponse.builder()
                .sessionId("session_correction_001")
                .problemType("NETWORK_FAILURE")
                .rootCause("Corrected: Network timeout due to firewall rules")
                .confidence(0.90)
                .solution("Corrected: Update firewall rules to allow traffic")
                .status("COMPLETED")
                .build();

        when(diagnosisService.submitFeedback(anyString(), any(FeedbackRequest.class)))
                .thenReturn(Optional.of(correctedResponse));

        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(3)
                .isCorrect(false)
                .actualRootCause("Network timeout due to firewall rules")
                .actualSolution("Update firewall rules to allow traffic")
                .comment("原诊断不完全准确，已修正")
                .build();

        ResponseEntity<Map<String, Object>> response =
                diagnosisController.submitFeedback("session_correction_001", feedback);

        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    @DisplayName("Test Invalid Diagnosis Request - Empty Problem")
    void testInvalidDiagnosisRequest_EmptyProblem() {
        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("")  // Empty problem
                .build();

        ResponseEntity<DiagnosisResponse> response = diagnosisController.diagnose(request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().getStatus());
        assertNotNull(response.getBody().getErrorMessage());
    }

    @Test
    @DisplayName("Test Get Diagnosis Result by Session ID")
    void testGetDiagnosisResultBySessionId() {
        when(diagnosisService.getDiagnosisResult("session_test_001"))
                .thenReturn(Optional.of(mockResponse));

        ResponseEntity<DiagnosisResponse> response =
                diagnosisController.getDiagnosisResult("session_test_001");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("session_test_001", response.getBody().getSessionId());
    }

    @Test
    @DisplayName("Test Get Diagnosis Result - Not Found")
    void testGetDiagnosisResult_NotFound() {
        when(diagnosisService.getDiagnosisResult("nonexistent_session"))
                .thenReturn(Optional.empty());

        ResponseEntity<DiagnosisResponse> response =
                diagnosisController.getDiagnosisResult("nonexistent_session");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Invalid Feedback - Rating Out of Range")
    void testInvalidFeedback_RatingOutOfRange() {
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(6)  // Invalid rating (> 5)
                .isCorrect(true)
                .build();

        ResponseEntity<Map<String, Object>> response =
                diagnosisController.submitFeedback("session_test_001", feedback);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    @DisplayName("Test Complete Diagnosis Workflow with Multiple Steps")
    void testCompleteDiagnosisWorkflow_MultipleSteps() {
        // Create comprehensive diagnosis chain
        List<DiagnosisResponse.DiagnosisStep> steps = List.of(
                DiagnosisResponse.DiagnosisStep.builder()
                        .stepId("step_0")
                        .stepName("Intent Recognition")
                        .description("Recognize problem intent")
                        .result("Problem type: TASK_CREATE_FAILED")
                        .status("COMPLETED")
                        .executionTimeMs(50L)
                        .build(),
                DiagnosisResponse.DiagnosisStep.builder()
                        .stepId("step_1")
                        .stepName("Experience Retrieval")
                        .description("Search for similar experiences")
                        .result("Found 3 matching experiences")
                        .status("COMPLETED")
                        .executionTimeMs(100L)
                        .build(),
                DiagnosisResponse.DiagnosisStep.builder()
                        .stepId("step_2")
                        .stepName("Query Logs")
                        .description("Query error logs")
                        .result("Found permission denied error")
                        .status("COMPLETED")
                        .executionTimeMs(150L)
                        .build(),
                DiagnosisResponse.DiagnosisStep.builder()
                        .stepId("step_3")
                        .stepName("Root Cause Analysis")
                        .description("Analyze root cause")
                        .result("Root cause: Permission denied")
                        .status("COMPLETED")
                        .executionTimeMs(200L)
                        .build()
        );

        DiagnosisResponse comprehensiveResponse = DiagnosisResponse.builder()
                .sessionId("session_comprehensive_001")
                .problemType("TASK_CREATE_FAILED")
                .rootCause("Permission denied for workflow execution")
                .confidence(0.88)
                .solution("Grant required permissions to user")
                .diagnosisChain(steps)
                .status("COMPLETED")
                .build();

        when(diagnosisService.diagnose(any(DiagnosisRequest.class)))
                .thenReturn(comprehensiveResponse);

        DiagnosisRequest request = DiagnosisRequest.builder()
                .problem("任务创建失败 workflowId=wf_001 userId=user_test")
                .context("{\"workflowId\": \"wf_001\", \"userId\": \"user_test\"}")
                .priority("HIGH")
                .build();

        ResponseEntity<DiagnosisResponse> response = diagnosisController.diagnose(request);

        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().getDiagnosisChain().size());

        // Verify all steps completed
        for (DiagnosisResponse.DiagnosisStep step : response.getBody().getDiagnosisChain()) {
            assertEquals("COMPLETED", step.getStatus());
            assertNotNull(step.getExecutionTimeMs());
            assertTrue(step.getExecutionTimeMs() > 0);
        }
    }
}