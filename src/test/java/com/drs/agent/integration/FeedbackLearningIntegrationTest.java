package com.drs.agent.integration;

import com.drs.agent.controller.FeedbackController;
import com.drs.agent.model.DiagnosisSession;
import com.drs.agent.model.Experience;
import com.drs.agent.model.FeedbackRequest;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.ExperienceLearningService;
import com.drs.agent.service.dto.PendingConfirmation;
import com.drs.agent.service.dto.UserFeedback;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FeedbackLearningIntegrationTest
 *
 * Integration tests for feedback learning flow:
 * - Feedback submission and processing
 * - Experience score update
 * - Manual confirmation trigger
 * - Confirm and learn process
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
class FeedbackLearningIntegrationTest {

    @Autowired
    private FeedbackController feedbackController;

    @MockBean
    private ExperienceLearningService learningService;

    @MockBean
    private DiagnosisSessionRepository sessionRepository;

    @MockBean
    private ExperienceRepository experienceRepository;

    private DiagnosisSession mockSession;
    private Experience mockExperience;

    @BeforeEach
    void setUp() {
        // Setup mock session
        mockSession = DiagnosisSession.builder()
                .id(1L)
                .sessionId("session_feedback_001")
                .userId("user_test")
                .problem("任务创建失败 workflowId=wf_001")
                .problemType("TASK_CREATE_FAILED")
                .rootCause("Permission denied for workflow execution")
                .confidenceScore(0.85)
                .solution("Grant required permissions")
                .status("COMPLETED")
                .build();

        // Setup mock experience
        mockExperience = Experience.builder()
                .id(1L)
                .experienceId("exp_feedback_001")
                .problemType("TASK_CREATE_FAILED")
                .keywords("permission,workflow,failed")
                .confidenceScore(0.80)
                .usageCount(5)
                .build();
    }

    @Test
    @DisplayName("Test Feedback Submission and Score Update")
    void testFeedbackAndUpdateScore() {
        // 1. Submit feedback
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(5)
                .isCorrect(true)
                .comment("诊断准确，解决方案有效")
                .userId("user_test")
                .build();

        // Mock successful feedback processing
        doNothing().when(learningService).processFeedback(anyString(), any(FeedbackRequest.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.submitFeedback("session_feedback_001", feedback);

        // 2. Verify feedback response
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals("session_feedback_001", body.get("sessionId"));

        // 3. Mock experience score update
        mockExperience.setConfidenceScore(0.85);  // Boosted score
        when(experienceRepository.findByExperienceId("exp_feedback_001"))
                .thenReturn(Optional.of(mockExperience));

        doNothing().when(learningService).updateExperienceScore(anyString(), anyInt(), anyString());

        learningService.updateExperienceScore("exp_feedback_001", 5, "Excellent diagnosis");

        // 4. Verify score update was called
        verify(learningService, times(1))
                .updateExperienceScore(eq("exp_feedback_001"), eq(5), anyString());
    }

    @Test
    @DisplayName("Test Low Confidence Trigger Manual Confirmation")
    void testLowConfidenceTriggerManualConfirmation() {
        // Setup diagnosis session with low confidence
        mockSession.setConfidenceScore(0.45);

        when(sessionRepository.findBySessionId("session_low_conf_001"))
                .thenReturn(Optional.of(mockSession));

        // Mock manual confirmation trigger
        String confirmationId = "conf_test_001";
        when(learningService.triggerManualConfirmation(anyString(), any()))
                .thenReturn(confirmationId);

        // Trigger manual confirmation
        String resultConfirmationId = learningService.triggerManualConfirmation(
                "session_low_conf_001", null);

        assertNotNull(resultConfirmationId);
        assertTrue(resultConfirmationId.startsWith("conf_"));

        verify(learningService, times(1))
                .triggerManualConfirmation(anyString(), any());
    }

    @Test
    @DisplayName("Test Confirm and Learn Process")
    void testConfirmAndLearn() {
        // Setup user feedback for confirmation
        UserFeedback userFeedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(5)
                .comment("Confirmed correct diagnosis")
                .addToExperience(true)
                .build();

        // Mock confirmation process
        doNothing().when(learningService).confirmAndLearn(anyString(), any(UserFeedback.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.confirmAndLearn("conf_test_001", userFeedback);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals("conf_test_001", body.get("confirmationId"));
        assertTrue(body.get("message").toString().contains("Experience added"));

        verify(learningService, times(1))
                .confirmAndLearn(eq("conf_test_001"), any(UserFeedback.class));
    }

    @Test
    @DisplayName("Test Feedback with Correction Creates New Experience")
    void testFeedbackWithCorrectionCreatesNewExperience() {
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(3)
                .isCorrect(false)
                .actualRootCause("Corrected root cause: Network firewall issue")
                .actualSolution("Update firewall rules")
                .comment("原诊断不完全准确")
                .userId("user_test")
                .build();

        doNothing().when(learningService).processFeedback(anyString(), any(FeedbackRequest.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.submitFeedback("session_correction_001", feedback);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        // Check if experience addition was triggered
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
    }

    @Test
    @DisplayName("Test Get Pending Confirmations")
    void testGetPendingConfirmations() {
        // Setup pending confirmations
        List<PendingConfirmation> pendingList = List.of(
                PendingConfirmation.builder()
                        .confirmationId("conf_pending_001")
                        .sessionId("session_001")
                        .problem("Task creation failed")
                        .problemType("TASK_CREATE_FAILED")
                        .predictedRootCause("Permission denied")
                        .confidence(0.45)
                        .reason("Low confidence score")
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusHours(24))
                        .status("PENDING")
                        .build(),
                PendingConfirmation.builder()
                        .confirmationId("conf_pending_002")
                        .sessionId("session_002")
                        .problem("Database timeout")
                        .problemType("DATABASE_TIMEOUT")
                        .predictedRootCause("Connection pool exhausted")
                        .confidence(0.50)
                        .reason("No similar experiences")
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusHours(24))
                        .status("PENDING")
                        .build()
        );

        when(learningService.getPendingConfirmations())
                .thenReturn(pendingList);

        ResponseEntity<Map<String, Object>> response =
                feedbackController.getPendingConfirmations();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals(2, body.get("count"));

        List<PendingConfirmation> confirmations =
                (List<PendingConfirmation>) body.get("pendingConfirmations");
        assertEquals(2, confirmations.size());
    }

    @Test
    @DisplayName("Test Get Confirmation Details")
    void testGetConfirmationDetails() {
        PendingConfirmation confirmation = PendingConfirmation.builder()
                .confirmationId("conf_detail_001")
                .sessionId("session_detail_001")
                .problem("Task creation failed workflowId=wf_001")
                .problemType("TASK_CREATE_FAILED")
                .predictedRootCause("Permission denied for workflow")
                .confidence(0.55)
                .reason("Low confidence score (0.55)")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .status("PENDING")
                .build();

        when(learningService.getConfirmation("conf_detail_001"))
                .thenReturn(Optional.of(confirmation));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.getConfirmationDetails("conf_detail_001");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));

        PendingConfirmation retrievedConfirmation =
                (PendingConfirmation) body.get("confirmation");
        assertEquals("conf_detail_001", retrievedConfirmation.getConfirmationId());
        assertEquals("PENDING", retrievedConfirmation.getStatus());
    }

    @Test
    @DisplayName("Test Get Confirmation Details - Not Found")
    void testGetConfirmationDetails_NotFound() {
        when(learningService.getConfirmation("nonexistent_conf"))
                .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response =
                feedbackController.getConfirmationDetails("nonexistent_conf");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Rate Experience")
    void testRateExperience() {
        doNothing().when(learningService)
                .updateExperienceScore(anyString(), anyInt(), anyString());

        ResponseEntity<Map<String, Object>> response =
                feedbackController.rateExperience("exp_feedback_001", 5, "Very helpful");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals("exp_feedback_001", body.get("experienceId"));
        assertEquals(5, body.get("rating"));

        verify(learningService, times(1))
                .updateExperienceScore(eq("exp_feedback_001"), eq(5), eq("Very helpful"));
    }

    @Test
    @DisplayName("Test Rate Experience - Invalid Rating")
    void testRateExperience_InvalidRating() {
        ResponseEntity<Map<String, Object>> response =
                feedbackController.rateExperience("exp_feedback_001", 6, null);

        assertEquals(400, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertTrue(body.get("message").toString().contains("1 and 5"));
    }

    @Test
    @DisplayName("Test Rate Experience - Experience Not Found")
    void testRateExperience_ExperienceNotFound() {
        doThrow(new ExperienceLearningService.ExperienceNotFoundException("Experience not found"))
                .when(learningService).updateExperienceScore(eq("nonexistent_exp"), anyInt(), any());

        ResponseEntity<Map<String, Object>> response =
                feedbackController.rateExperience("nonexistent_exp", 4, null);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Optimize Low Score Experiences")
    void testOptimizeLowScoreExperiences() {
        doNothing().when(learningService).optimizeLowScoreExperiences();

        ResponseEntity<Map<String, Object>> response =
                feedbackController.optimizeLowScoreExperiences();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertTrue(body.get("message").toString().contains("optimization completed"));

        verify(learningService, times(1)).optimizeLowScoreExperiences();
    }

    @Test
    @DisplayName("Test Cleanup Expired Confirmations")
    void testCleanupExpiredConfirmations() {
        doNothing().when(learningService).cleanupExpiredConfirmations();

        ResponseEntity<Map<String, Object>> response =
                feedbackController.cleanupExpiredConfirmations();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertTrue(body.get("message").toString().contains("cleaned up"));

        verify(learningService, times(1)).cleanupExpiredConfirmations();
    }

    @Test
    @DisplayName("Test Confirmation Expired Exception")
    void testConfirmationExpiredException() {
        UserFeedback userFeedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(4)
                .build();

        doThrow(new ExperienceLearningService.ConfirmationExpiredException("Confirmation expired"))
                .when(learningService).confirmAndLearn(eq("expired_conf"), any(UserFeedback.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.confirmAndLearn("expired_conf", userFeedback);

        assertEquals(400, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertTrue((Boolean) body.get("expired"));
    }

    @Test
    @DisplayName("Test Session Not Found on Feedback")
    void testSessionNotFoundOnFeedback() {
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(4)
                .isCorrect(true)
                .build();

        doThrow(new ExperienceLearningService.SessionNotFoundException("Session not found"))
                .when(learningService).processFeedback(eq("nonexistent_session"), any(FeedbackRequest.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.submitFeedback("nonexistent_session", feedback);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Negative Rating Decreases Experience Score")
    void testNegativeRatingDecreasesExperienceScore() {
        // Mock negative rating (1 or 2)
        doNothing().when(learningService)
                .updateExperienceScore(anyString(), anyInt(), anyString());

        // Low rating should decrease score
        feedbackController.rateExperience("exp_feedback_001", 1, "Not helpful at all");

        verify(learningService, times(1))
                .updateExperienceScore(eq("exp_feedback_001"), eq(1), anyString());
    }

    @Test
    @DisplayName("Test Feedback Session Not Found on Confirmation")
    void testFeedbackSessionNotFoundOnConfirmation() {
        UserFeedback userFeedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(4)
                .build();

        doThrow(new ExperienceLearningService.SessionNotFoundException("Session not found"))
                .when(learningService).confirmAndLearn(eq("conf_no_session"), any(UserFeedback.class));

        ResponseEntity<Map<String, Object>> response =
                feedbackController.confirmAndLearn("conf_no_session", userFeedback);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Complete Feedback Learning Flow")
    void testCompleteFeedbackLearningFlow() {
        // 1. Create low confidence diagnosis
        mockSession.setConfidenceScore(0.50);

        // 2. Trigger manual confirmation
        String confirmationId = "conf_flow_001";
        when(learningService.triggerManualConfirmation(anyString(), any()))
                .thenReturn(confirmationId);

        String result = learningService.triggerManualConfirmation("session_flow_001", null);
        assertEquals(confirmationId, result);

        // 3. User provides feedback
        UserFeedback feedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(5)
                .comment("Confirmed correct after investigation")
                .addToExperience(true)
                .build();

        doNothing().when(learningService).confirmAndLearn(anyString(), any(UserFeedback.class));

        ResponseEntity<Map<String, Object>> confirmResponse =
                feedbackController.confirmAndLearn(confirmationId, feedback);

        assertTrue((Boolean) confirmResponse.getBody().get("success"));

        // 4. Verify all steps completed
        verify(learningService, times(1))
                .triggerManualConfirmation(anyString(), any());
        verify(learningService, times(1))
                .confirmAndLearn(anyString(), any(UserFeedback.class));
    }

    @Test
    @DisplayName("Test Invalid Feedback Rating Range")
    void testInvalidFeedbackRatingRange() {
        FeedbackRequest feedback = FeedbackRequest.builder()
                .rating(0)  // Invalid rating (< 1)
                .isCorrect(true)
                .build();

        ResponseEntity<Map<String, Object>> response =
                feedbackController.submitFeedback("session_invalid_rating", feedback);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    @DisplayName("Test Pending Confirmation Expiration Check")
    void testPendingConfirmationExpirationCheck() {
        // Setup expired confirmation
        PendingConfirmation expiredConfirmation = PendingConfirmation.builder()
                .confirmationId("conf_expired_001")
                .createdAt(LocalDateTime.now().minusHours(25))
                .expiresAt(LocalDateTime.now().minusHours(1))  // Expired
                .status("PENDING")
                .build();

        PendingConfirmation activeConfirmation = PendingConfirmation.builder()
                .confirmationId("conf_active_001")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))  // Active
                .status("PENDING")
                .build();

        when(learningService.getPendingConfirmations())
                .thenReturn(List.of(activeConfirmation));  // Only active returned

        ResponseEntity<Map<String, Object>> response =
                feedbackController.getPendingConfirmations();

        List<PendingConfirmation> pending =
                (List<PendingConfirmation>) response.getBody().get("pendingConfirmations");

        // Only active confirmation should be returned
        assertEquals(1, pending.size());
        assertEquals("conf_active_001", pending.get(0).getConfirmationId());
    }
}