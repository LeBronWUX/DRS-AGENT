package com.drs.agent.service;

import com.drs.agent.model.*;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExperienceLearningService
 */
@ExtendWith(MockitoExtension.class)
class ExperienceLearningServiceTest {

    @Mock
    private ExperienceService experienceService;

    @Mock
    private ClaudeService claudeService;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private DiagnosisSessionRepository sessionRepository;

    @Mock
    private ExperienceRepository experienceRepository;

    private ObjectMapper objectMapper;
    private ExperienceLearningService learningService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        learningService = new ExperienceLearningService(
                experienceService,
                claudeService,
                promptTemplateService,
                sessionRepository,
                experienceRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should add new experience successfully")
    void testAddExperience() {
        // Create request
        ExperienceRequest request = ExperienceRequest.builder()
                .problemType("PERMISSION")
                .keywords(List.of("permission", "denied"))
                .diagnosisChain("[]")
                .rootCauses("Permission denied")
                .solutions("Grant permission")
                .confidenceScore(0.9)
                .build();

        // Mock experience service
        Experience savedExperience = Experience.builder()
                .experienceId("exp_001")
                .problemType("PERMISSION")
                .keywords("permission,denied")
                .confidenceScore(0.9)
                .build();

        when(experienceService.addExperience(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(savedExperience);
        when(experienceRepository.save(any(Experience.class))).thenReturn(savedExperience);

        // Execute
        String experienceId = learningService.addExperience(request);

        // Verify
        assertNotNull(experienceId);
        assertEquals("exp_001", experienceId);
        verify(experienceService).addExperience(
                "PERMISSION",
                "permission,denied",
                "[]",
                "Permission denied",
                "Grant permission"
        );
    }

    @Test
    @DisplayName("Should generate experience template with Claude")
    void testGenerateTemplate() {
        // Create diagnosis result
        DiagnosisChainResult chainResult = DiagnosisChainResult.builder()
                .stepResults(List.of(
                        StepResult.builder()
                                .stepName("Query Logs")
                                .toolName("query_logs")
                                .success(true)
                                .data("Found error: Permission denied")
                                .build()
                ))
                .success(true)
                .build();

        RootCauseResult rootCause = RootCauseResult.builder()
                .category("PERMISSION")
                .description("Account lacks permission")
                .confidence(0.9)
                .solution(Solution.builder()
                        .immediateAction("Grant permission")
                        .build())
                .build();

        DiagnosisResult diagnosisResult = DiagnosisResult.builder()
                .sessionId("session_001")
                .problemType("PERMISSION")
                .userProblem("Task creation failed")
                .diagnosisChainResult(chainResult)
                .rootCauseResult(rootCause)
                .solutionApplied("Grant permission")
                .confidence(0.9)
                .build();

        // Mock prompt template service
        when(promptTemplateService.getTemplate(anyString(), any(Map.class)))
                .thenReturn("Mock template");

        // Mock Claude response
        ClaudeResponse mockResponse = mock(ClaudeResponse.class);
        when(mockResponse.getTextContent()).thenReturn("""
            {
              "problem_type": "PERMISSION",
              "keywords": ["permission", "denied", "task"],
              "summary": "Task creation failed due to permission issue",
              "diagnosis_chain": [
                {
                  "step": 1,
                  "action": "Query Logs",
                  "tool": "query_logs",
                  "key_outputs": ["traceId"]
                }
              ],
              "root_causes": [
                {
                  "pattern": "Permission denied",
                  "regex": "Permission denied.*",
                  "cause": "Account lacks required permission",
                  "frequency": "HIGH"
                }
              ],
              "success_rate": 0.95,
              "expert_tips": ["Always check permissions first"]
            }
            """);
        when(claudeService.sendMessage(anyString())).thenReturn(mockResponse);

        // Execute
        ExperienceTemplate template = learningService.generateTemplate(diagnosisResult);

        // Verify
        assertNotNull(template);
        assertEquals("PERMISSION", template.getProblemType());
        assertNotNull(template.getKeywords());
        assertTrue(template.getKeywords().contains("permission"));
        assertNotNull(template.getDiagnosisChain());
        assertNotNull(template.getRootCauses());
        assertEquals(0.95, template.getEstimatedSuccessRate());
    }

    @Test
    @DisplayName("Should trigger manual confirmation for low confidence")
    void testTriggerManualConfirmation() {
        DiagnosisResult result = DiagnosisResult.builder()
                .sessionId("session_001")
                .problemType("UNKNOWN")
                .userProblem("Unknown issue")
                .confidence(0.5)
                .rootCauseResult(RootCauseResult.builder()
                        .category("UNKNOWN")
                        .description("Unknown root cause")
                        .confidence(0.5)
                        .build())
                .build();

        String confirmationId = learningService.triggerManualConfirmation("session_001", result);

        assertNotNull(confirmationId);
        assertTrue(confirmationId.startsWith("conf_"));

        // Verify confirmation is stored
        Optional<PendingConfirmation> confirmation = learningService.getConfirmation(confirmationId);
        assertTrue(confirmation.isPresent());
        assertEquals("session_001", confirmation.get().getSessionId());
        assertEquals("PENDING", confirmation.get().getStatus());
    }

    @Test
    @DisplayName("Should confirm and learn from user feedback")
    void testConfirmAndLearn() {
        // Create confirmation first
        DiagnosisResult diagnosisResult = DiagnosisResult.builder()
                .sessionId("session_001")
                .problemType("PERMISSION")
                .confidence(0.5)
                .rootCauseResult(RootCauseResult.builder()
                        .description("Test root cause")
                        .build())
                .build();

        String confirmationId = learningService.triggerManualConfirmation("session_001", diagnosisResult);

        // Mock session
        DiagnosisSession session = DiagnosisSession.builder()
                .sessionId("session_001")
                .problemType("PERMISSION")
                .rootCause("Test root cause")
                .solution("Test solution")
                .diagnosisChain("[]")
                .build();
        when(sessionRepository.findBySessionId("session_001"))
                .thenReturn(Optional.of(session));

        // Mock experience service
        Experience savedExp = Experience.builder()
                .experienceId("exp_new")
                .problemType("PERMISSION")
                .confidenceScore(0.9)
                .build();
        when(experienceService.addExperience(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(savedExp);
        when(sessionRepository.save(any(DiagnosisSession.class))).thenReturn(session);
        when(experienceRepository.save(any(Experience.class))).thenReturn(savedExp);

        // Create user feedback
        UserFeedback feedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(5)
                .comment("Very helpful")
                .addToExperience(true)
                .build();

        // Execute confirm and learn
        learningService.confirmAndLearn(confirmationId, feedback);

        // Verify experience was added
        verify(experienceService).addExperience(
                eq("PERMISSION"),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(experienceRepository).save(any(Experience.class));

        // Verify session was updated
        verify(sessionRepository).save(any(DiagnosisSession.class));

        // Verify confirmation status
        Optional<PendingConfirmation> updatedConfirmation = learningService.getConfirmation(confirmationId);
        assertTrue(updatedConfirmation.isPresent());
        assertEquals("CONFIRMED", updatedConfirmation.get().getStatus());
    }

    @Test
    @DisplayName("Should update experience score based on rating")
    void testUpdateExperienceScore() {
        Experience experience = Experience.builder()
                .experienceId("exp_001")
                .confidenceScore(0.8)
                .usageCount(5)
                .build();

        when(experienceRepository.findByExperienceId("exp_001"))
                .thenReturn(Optional.of(experience));
        when(experienceRepository.save(any(Experience.class))).thenReturn(experience);

        // Execute with rating 5 (should boost score)
        learningService.updateExperienceScore("exp_001", 5, "Excellent!");

        // Verify score was boosted
        verify(experienceRepository).save(argThat(exp ->
                exp.getConfidenceScore() > 0.8 &&
                exp.getUsageCount() == 6
        ));
    }

    @Test
    @DisplayName("Should penalize low rating")
    void testLowRatingPenalty() {
        Experience experience = Experience.builder()
                .experienceId("exp_001")
                .confidenceScore(0.8)
                .usageCount(5)
                .build();

        when(experienceRepository.findByExperienceId("exp_001"))
                .thenReturn(Optional.of(experience));
        when(experienceRepository.save(any(Experience.class))).thenReturn(experience);

        // Execute with rating 1 (should penalize)
        learningService.updateExperienceScore("exp_001", 1, "Not helpful");

        // Verify score was reduced
        verify(experienceRepository).save(argThat(exp ->
                exp.getConfidenceScore() < 0.8
        ));
    }

    @Test
    @DisplayName("Should get pending confirmations list")
    void testGetPendingConfirmations() {
        // Create multiple confirmations
        DiagnosisResult result1 = DiagnosisResult.builder()
                .sessionId("session_001")
                .confidence(0.5)
                .rootCauseResult(RootCauseResult.builder().description("Test").build())
                .build();

        DiagnosisResult result2 = DiagnosisResult.builder()
                .sessionId("session_002")
                .confidence(0.6)
                .rootCauseResult(RootCauseResult.builder().description("Test2").build())
                .build();

        learningService.triggerManualConfirmation("session_001", result1);
        learningService.triggerManualConfirmation("session_002", result2);

        // Get pending list
        List<PendingConfirmation> pendingList = learningService.getPendingConfirmations();

        assertNotNull(pendingList);
        assertEquals(2, pendingList.size());
    }

    @Test
    @DisplayName("Should handle expired confirmation")
    void testExpiredConfirmation() {
        // Create confirmation
        DiagnosisResult result = DiagnosisResult.builder()
                .sessionId("session_001")
                .confidence(0.5)
                .rootCauseResult(RootCauseResult.builder().description("Test").build())
                .build();

        String confirmationId = learningService.triggerManualConfirmation("session_001", result);

        // Manually set expiration to past (simulate expired)
        PendingConfirmation confirmation = learningService.getConfirmation(confirmationId).orElseThrow();
        confirmation.setExpiresAt(LocalDateTime.now().minusHours(1));
        confirmation.setStatus("EXPIRED");

        // Try to confirm expired confirmation
        UserFeedback feedback = UserFeedback.builder()
                .isCorrect(true)
                .rating(5)
                .build();

        assertThrows(ExperienceLearningService.ConfirmationExpiredException.class,
                () -> learningService.confirmAndLearn(confirmationId, feedback));
    }

    @Test
    @DisplayName("Should optimize low score experiences")
    void testOptimizeLowScoreExperiences() {
        List<Experience> experiences = new ArrayList<>();

        // Add low score experience with low usage
        experiences.add(Experience.builder()
                .experienceId("exp_low_usage")
                .confidenceScore(0.3)
                .usageCount(1)
                .metadata(null)
                .build());

        // Add low score experience with high usage
        experiences.add(Experience.builder()
                .experienceId("exp_high_usage")
                .confidenceScore(0.4)
                .usageCount(10)
                .metadata(null)
                .build());

        // Add high score experience (should not be affected)
        experiences.add(Experience.builder()
                .experienceId("exp_high_score")
                .confidenceScore(0.9)
                .usageCount(5)
                .build());

        when(experienceRepository.findAll()).thenReturn(experiences);
        when(experienceRepository.save(any(Experience.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute optimization
        learningService.optimizeLowScoreExperiences();

        // Verify save was called for low score experiences
        verify(experienceRepository, atLeast(2)).save(any(Experience.class));
    }

    @Test
    @DisplayName("Should process feedback from session")
    void testProcessFeedback() {
        DiagnosisSession session = DiagnosisSession.builder()
                .sessionId("session_001")
                .problemType("PERMISSION")
                .problem("Task creation failed")
                .rootCause("Permission denied")
                .solution("Grant permission")
                .confidenceScore(0.7)
                .diagnosisChain("[]")
                .status("COMPLETED")
                .build();

        when(sessionRepository.findBySessionId("session_001"))
                .thenReturn(Optional.of(session));

        Experience savedExp = Experience.builder()
                .experienceId("exp_new")
                .build();
        when(experienceService.addExperience(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(savedExp);
        when(sessionRepository.save(any(DiagnosisSession.class))).thenReturn(session);
        when(experienceRepository.save(any(Experience.class))).thenReturn(savedExp);

        FeedbackRequest feedback = FeedbackRequest.builder()
                .isCorrect(false)
                .actualRootCause("Corrected root cause")
                .actualSolution("Corrected solution")
                .rating(4)
                .comment("Good suggestion but wrong root cause")
                .build();

        // Execute
        learningService.processFeedback("session_001", feedback);

        // Verify session was updated
        verify(sessionRepository).save(argThat(s ->
                s.getFeedbackRating() == 4 &&
                s.getIsCorrect() == false
        ));
    }

    @Test
    @DisplayName("Should throw exception for invalid rating")
    void testInvalidRating() {
        Experience experience = Experience.builder()
                .experienceId("exp_001")
                .confidenceScore(0.8)
                .build();

        when(experienceRepository.findByExperienceId("exp_001"))
                .thenReturn(Optional.of(experience));

        assertThrows(IllegalArgumentException.class,
                () -> learningService.updateExperienceScore("exp_001", 0, "Invalid"));
    }

    @Test
    @DisplayName("Should throw exception for non-existent experience")
    void testNonExistentExperience() {
        when(experienceRepository.findByExperienceId("exp_nonexistent"))
                .thenReturn(Optional.empty());

        assertThrows(ExperienceLearningService.ExperienceNotFoundException.class,
                () -> learningService.updateExperienceScore("exp_nonexistent", 4, "Test"));
    }

    @Test
    @DisplayName("Should cleanup expired confirmations")
    void testCleanupExpiredConfirmations() {
        // Create confirmation and mark as expired
        DiagnosisResult result = DiagnosisResult.builder()
                .sessionId("session_001")
                .confidence(0.5)
                .rootCauseResult(RootCauseResult.builder().description("Test").build())
                .build();

        String confirmationId = learningService.triggerManualConfirmation("session_001", result);

        // Get confirmation and mark as expired
        PendingConfirmation confirmation = learningService.getConfirmation(confirmationId).orElseThrow();
        confirmation.setStatus("EXPIRED");
        confirmation.setExpiresAt(LocalDateTime.now().minusHours(1));

        // Cleanup
        learningService.cleanupExpiredConfirmations();

        // Verify expired confirmation is removed
        Optional<PendingConfirmation> afterCleanup = learningService.getConfirmation(confirmationId);
        assertFalse(afterCleanup.isPresent());
    }
}