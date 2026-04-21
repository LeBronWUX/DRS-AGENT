package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.Experience;
import com.drs.agent.model.ExperienceMatch;
import com.drs.agent.model.IntentResult;
import com.drs.agent.model.RetrievalResult;
import com.drs.agent.repository.ExperienceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExperienceRetriever.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExperienceRetrieverTest {

    @Mock
    private McpToolRegistry toolRegistry;

    @Mock
    private ExperienceRepository experienceRepository;

    @Mock
    private ExperienceService experienceService;

    private ObjectMapper objectMapper;

    private ExperienceRetriever retriever;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        retriever = new ExperienceRetriever(toolRegistry, experienceRepository, experienceService, objectMapper);

        // Set default configuration values using reflection
        ReflectionTestUtils.setField(retriever, "highConfidenceThreshold", 0.85);
        ReflectionTestUtils.setField(retriever, "lowConfidenceThreshold", 0.70);
        ReflectionTestUtils.setField(retriever, "defaultTopK", 5);
        ReflectionTestUtils.setField(retriever, "minSimilarity", 0.50);
    }

    @Nested
    @DisplayName("Search by Intent Tests")
    class SearchByIntentTests {

        @Test
        @DisplayName("Search with unrecognized intent should fall back to vector search")
        void testSearchWithUnrecognizedIntent() {
            IntentResult intent = IntentResult.unrecognized("Some random message");

            // Mock vector search response
            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 0);
            mockResultData.put("data", new ArrayList<>());
            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);

            RetrievalResult result = retriever.search(intent);

            assertNotNull(result);
            assertFalse(result.hasMatches());
            assertTrue(result.isNeedsLearning());
            assertEquals(RetrievalResult.RetrievalMethod.VECTOR, result.getRetrievalMethod());
        }

        @Test
        @DisplayName("Search with exact match should return high confidence result")
        void testSearchWithExactMatch() {
            Experience exp1 = createTestExperience("exp-001", "database-timeout", "timeout,connection,mysql");
            Experience exp2 = createTestExperience("exp-002", "database-timeout", "timeout,connection,oracle");

            IntentResult intent = IntentResult.of(
                    "Database connection timeout",
                    "database-timeout",
                    Arrays.asList("timeout", "connection")
            );

            when(experienceRepository.findByProblemType("database-timeout"))
                    .thenReturn(Arrays.asList(exp1, exp2));
            when(experienceRepository.findByExperienceId(anyString()))
                    .thenReturn(Optional.of(exp1));

            RetrievalResult result = retriever.search(intent);

            assertNotNull(result);
            assertTrue(result.hasMatches());
            assertTrue(result.isHasHighConfidenceMatch());
            assertFalse(result.isNeedsLearning());
            assertEquals(RetrievalResult.RetrievalMethod.EXACT, result.getRetrievalMethod());
            assertEquals(1.0, result.getMaxSimilarity());
        }

        @Test
        @DisplayName("Search with no exact match should fall back to vector search")
        void testSearchWithNoExactMatch() {
            IntentResult intent = IntentResult.of(
                    "Database connection timeout",
                    "database-timeout",
                    Arrays.asList("timeout", "connection")
            );

            // No exact matches
            when(experienceRepository.findByProblemType("database-timeout"))
                    .thenReturn(new ArrayList<>());

            // Mock vector search
            Experience exp = createTestExperience("exp-001", "database-timeout", "timeout,connection");
            Map<String, Object> expData = new HashMap<>();
            expData.put("experienceId", "exp-001");
            expData.put("problemType", "database-timeout");
            expData.put("confidenceScore", 0.85);

            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 1);
            mockResultData.put("data", Arrays.asList(expData));

            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);
            when(experienceRepository.findByExperienceId("exp-001"))
                    .thenReturn(Optional.of(exp));

            RetrievalResult result = retriever.search(intent);

            assertNotNull(result);
            assertTrue(result.hasMatches());
            assertEquals(RetrievalResult.RetrievalMethod.HYBRID, result.getRetrievalMethod());
        }

        @Test
        @DisplayName("Search with null problem type should use vector search")
        void testSearchWithNullProblemType() {
            IntentResult intent = IntentResult.builder()
                    .originalMessage("Some message")
                    .problemType(null)
                    .keywords(Arrays.asList("timeout"))
                    .recognized(true)
                    .build();

            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 0);
            mockResultData.put("data", new ArrayList<>());
            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);

            RetrievalResult result = retriever.search(intent);

            assertNotNull(result);
            assertFalse(result.hasMatches());
        }
    }

    @Nested
    @DisplayName("Search by Description Tests")
    class SearchByDescriptionTests {

        @Test
        @DisplayName("Vector search with valid results should return matches")
        void testVectorSearchWithResults() {
            Experience exp = createTestExperience("exp-001", "database", "timeout,connection");
            exp.setConfidenceScore(0.90);

            Map<String, Object> expData = new HashMap<>();
            expData.put("experienceId", "exp-001");
            expData.put("confidenceScore", 0.90);

            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 1);
            mockResultData.put("data", Arrays.asList(expData));

            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);
            when(experienceRepository.findByExperienceId("exp-001"))
                    .thenReturn(Optional.of(exp));

            RetrievalResult result = retriever.searchByDescription("Database timeout", 5);

            assertNotNull(result);
            assertTrue(result.hasMatches());
            assertEquals(1, result.getTotalMatches());
            assertTrue(result.getMaxSimilarity() > 0);
        }

        @Test
        @DisplayName("Vector search with no results should return empty result")
        void testVectorSearchNoResults() {
            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 0);
            mockResultData.put("data", new ArrayList<>());

            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);

            RetrievalResult result = retriever.searchByDescription("Unknown problem", 5);

            assertNotNull(result);
            assertFalse(result.hasMatches());
            assertTrue(result.isNeedsLearning());
        }

        @Test
        @DisplayName("Vector search failure should return empty result")
        void testVectorSearchFailure() {
            ToolResult mockToolResult = ToolResult.failure("Search failed");
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);

            RetrievalResult result = retriever.searchByDescription("Some problem", 5);

            assertNotNull(result);
            assertFalse(result.hasMatches());
        }

        @Test
        @DisplayName("High confidence match should set hasHighConfidenceMatch flag")
        void testHighConfidenceMatch() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            exp.setConfidenceScore(0.92);

            Map<String, Object> expData = new HashMap<>();
            expData.put("experienceId", "exp-001");
            expData.put("confidenceScore", 0.92);

            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 1);
            mockResultData.put("data", Arrays.asList(expData));

            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);
            when(experienceRepository.findByExperienceId("exp-001"))
                    .thenReturn(Optional.of(exp));

            RetrievalResult result = retriever.searchByDescription("Database timeout", 5);

            assertTrue(result.isHasHighConfidenceMatch());
            assertFalse(result.isNeedsLearning());
        }

        @Test
        @DisplayName("Low confidence match should trigger learning")
        void testLowConfidenceMatch() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            exp.setConfidenceScore(0.50);

            Map<String, Object> expData = new HashMap<>();
            expData.put("experienceId", "exp-001");
            expData.put("confidenceScore", 0.50);

            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 1);
            mockResultData.put("data", Arrays.asList(expData));

            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);
            when(experienceRepository.findByExperienceId("exp-001"))
                    .thenReturn(Optional.of(exp));

            RetrievalResult result = retriever.searchByDescription("Database timeout", 5);

            assertTrue(result.isNeedsLearning());
            assertFalse(result.isHasHighConfidenceMatch());
        }
    }

    @Nested
    @DisplayName("Search by Type and Keywords Tests")
    class SearchByTypeAndKeywordsTests {

        @Test
        @DisplayName("Search with matching type and keywords should return results")
        void testSearchWithTypeAndKeywords() {
            Experience exp1 = createTestExperience("exp-001", "database", "timeout,mysql,connection");
            Experience exp2 = createTestExperience("exp-002", "database", "slow,performance");

            when(experienceRepository.findByProblemType("database"))
                    .thenReturn(Arrays.asList(exp1, exp2));

            List<ExperienceMatch> matches = retriever.searchByTypeAndKeywords(
                    "database",
                    Arrays.asList("timeout", "mysql")
            );

            assertNotNull(matches);
            assertFalse(matches.isEmpty());
            assertTrue(matches.get(0).getSimilarity() > 0);
        }

        @Test
        @DisplayName("Search with no matching keywords should return empty or partial matches")
        void testSearchWithNoMatchingKeywords() {
            Experience exp = createTestExperience("exp-001", "database", "slow,performance");

            when(experienceRepository.findByProblemType("database"))
                    .thenReturn(Arrays.asList(exp));

            // Mock vector search as fallback
            Map<String, Object> mockResultData = new HashMap<>();
            mockResultData.put("success", true);
            mockResultData.put("count", 0);
            mockResultData.put("data", new ArrayList<>());
            ToolResult mockToolResult = ToolResult.success(mockResultData);
            when(toolRegistry.executeTool(eq("search_experience"), anyMap()))
                    .thenReturn(mockToolResult);

            List<ExperienceMatch> matches = retriever.searchByTypeAndKeywords(
                    "database",
                    Arrays.asList("timeout", "connection")
            );

            assertNotNull(matches);
        }
    }

    @Nested
    @DisplayName("Calculate Similarity Tests")
    class CalculateSimilarityTests {

        @Test
        @DisplayName("Exact problem type match should increase similarity")
        void testExactProblemTypeMatch() {
            Experience exp = createTestExperience("exp-001", "database-timeout", "timeout,connection");

            IntentResult intent = IntentResult.of(
                    "Database timeout",
                    "database-timeout",
                    Arrays.asList("timeout", "connection")
            );

            double similarity = retriever.calculateSimilarity(intent, exp);

            assertTrue(similarity > 0.8);
        }

        @Test
        @DisplayName("Different problem type should decrease similarity")
        void testDifferentProblemType() {
            Experience exp = createTestExperience("exp-001", "network-latency", "timeout,connection");

            IntentResult intent = IntentResult.of(
                    "Database timeout",
                    "database-timeout",
                    Arrays.asList("timeout", "connection")
            );

            double similarity = retriever.calculateSimilarity(intent, exp);

            assertTrue(similarity < 0.8);
        }

        @Test
        @DisplayName("Empty keywords should still calculate type similarity")
        void testEmptyKeywords() {
            Experience exp = createTestExperience("exp-001", "database-timeout", "timeout,connection");

            IntentResult intent = IntentResult.builder()
                    .originalMessage("Database timeout")
                    .problemType("database-timeout")
                    .keywords(new ArrayList<>())
                    .recognized(true)
                    .build();

            double similarity = retriever.calculateSimilarity(intent, exp);

            assertTrue(similarity >= 0.4);
        }
    }

    @Nested
    @DisplayName("Confidence and Learning Tests")
    class ConfidenceAndLearningTests {

        @Test
        @DisplayName("High confidence result should return true")
        void testIsHighConfidence() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            ExperienceMatch match = ExperienceMatch.semanticMatch(exp, 0.90);

            RetrievalResult result = RetrievalResult.of(match, RetrievalResult.RetrievalMethod.VECTOR);

            assertTrue(retriever.isHighConfidence(result));
        }

        @Test
        @DisplayName("Low confidence result should return false")
        void testLowConfidenceNotHigh() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            ExperienceMatch match = ExperienceMatch.semanticMatch(exp, 0.70);

            RetrievalResult result = RetrievalResult.of(match, RetrievalResult.RetrievalMethod.VECTOR);

            assertFalse(retriever.isHighConfidence(result));
        }

        @Test
        @DisplayName("Empty result should trigger learning")
        void testEmptyResultTriggerLearning() {
            RetrievalResult result = RetrievalResult.empty();

            assertTrue(retriever.shouldTriggerLearning(result));
        }

        @Test
        @DisplayName("High similarity should not trigger learning")
        void testHighSimilarityNotTriggerLearning() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            ExperienceMatch match = ExperienceMatch.semanticMatch(exp, 0.85);

            RetrievalResult result = RetrievalResult.of(match, RetrievalResult.RetrievalMethod.VECTOR);

            assertFalse(retriever.shouldTriggerLearning(result));
        }

        @Test
        @DisplayName("Low similarity should trigger learning")
        void testLowSimilarityTriggerLearning() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            ExperienceMatch match = ExperienceMatch.semanticMatch(exp, 0.60);

            RetrievalResult result = RetrievalResult.of(match, RetrievalResult.RetrievalMethod.VECTOR);

            assertTrue(retriever.shouldTriggerLearning(result));
        }
    }

    @Nested
    @DisplayName("Update Hit Count Tests")
    class UpdateHitCountTests {

        @Test
        @DisplayName("Update hit count should increment usage count")
        void testUpdateHitCount() {
            Experience exp = createTestExperience("exp-001", "database", "timeout");
            exp.setUsageCount(5);

            when(experienceRepository.findByExperienceId("exp-001"))
                    .thenReturn(Optional.of(exp));
            when(experienceRepository.save(any(Experience.class)))
                    .thenReturn(exp);

            retriever.updateHitCount("exp-001");

            verify(experienceRepository).save(argThat(saved ->
                    saved.getUsageCount() == 6
            ));
        }

        @Test
        @DisplayName("Update hit count for non-existent experience should do nothing")
        void testUpdateHitCountNonExistent() {
            when(experienceRepository.findByExperienceId("exp-nonexistent"))
                    .thenReturn(Optional.empty());

            retriever.updateHitCount("exp-nonexistent");

            verify(experienceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("RetrievalResult Utility Tests")
    class RetrievalResultUtilityTests {

        @Test
        @DisplayName("Get best match should return highest similarity")
        void testGetBestMatch() {
            Experience exp1 = createTestExperience("exp-001", "database", "timeout");
            Experience exp2 = createTestExperience("exp-002", "database", "connection");

            ExperienceMatch match1 = ExperienceMatch.semanticMatch(exp1, 0.75);
            ExperienceMatch match2 = ExperienceMatch.semanticMatch(exp2, 0.90);

            List<ExperienceMatch> matches = Arrays.asList(match1, match2);
            RetrievalResult result = RetrievalResult.of(matches, RetrievalResult.RetrievalMethod.HYBRID);

            ExperienceMatch bestMatch = result.getBestMatch();

            assertNotNull(bestMatch);
            assertEquals(0.90, bestMatch.getSimilarity());
            assertEquals("exp-002", bestMatch.getExperience().getExperienceId());
        }

        @Test
        @DisplayName("Get matches above threshold should filter correctly")
        void testGetMatchesAboveThreshold() {
            Experience exp1 = createTestExperience("exp-001", "database", "timeout");
            Experience exp2 = createTestExperience("exp-002", "database", "connection");

            ExperienceMatch match1 = ExperienceMatch.semanticMatch(exp1, 0.65);
            ExperienceMatch match2 = ExperienceMatch.semanticMatch(exp2, 0.90);

            List<ExperienceMatch> matches = Arrays.asList(match1, match2);
            RetrievalResult result = RetrievalResult.of(matches, RetrievalResult.RetrievalMethod.HYBRID);

            List<ExperienceMatch> highMatches = result.getMatchesAboveThreshold(0.80);

            assertEquals(1, highMatches.size());
            assertEquals(0.90, highMatches.get(0).getSimilarity());
        }

        @Test
        @DisplayName("Empty result hasMatches should return false")
        void testEmptyResultHasMatches() {
            RetrievalResult result = RetrievalResult.empty();

            assertFalse(result.hasMatches());
            assertNull(result.getBestMatch());
        }
    }

    // Helper method to create test experiences
    private Experience createTestExperience(String id, String problemType, String keywords) {
        return Experience.builder()
                .experienceId(id)
                .problemType(problemType)
                .keywords(keywords)
                .diagnosisChain("Standard diagnosis chain")
                .rootCauses("Root causes analysis")
                .solutions("Recommended solutions")
                .confidenceScore(0.85)
                .usageCount(0)
                .build();
    }
}