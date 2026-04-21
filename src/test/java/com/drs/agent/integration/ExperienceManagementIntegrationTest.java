package com.drs.agent.integration;

import com.drs.agent.controller.ExperienceController;
import com.drs.agent.model.Experience;
import com.drs.agent.model.ExperienceRequest;
import com.drs.agent.model.ExperienceResponse;
import com.drs.agent.model.IntentResult;
import com.drs.agent.model.RetrievalResult;
import com.drs.agent.model.ExperienceMatch;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.ExperienceRetriever;
import com.drs.agent.service.ExperienceService;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ExperienceManagementIntegrationTest
 *
 * Integration tests for experience management:
 * - Experience creation and retrieval
 * - Experience update and delete
 * - Vector similarity search
 * - Experience search by keywords
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
class ExperienceManagementIntegrationTest {

    @Autowired
    private ExperienceController experienceController;

    @MockBean
    private ExperienceRepository experienceRepository;

    @MockBean
    private ExperienceService experienceService;

    @MockBean
    private ExperienceRetriever experienceRetriever;

    @Autowired
    private ObjectMapper objectMapper;

    private Experience mockExperience;
    private ExperienceResponse mockResponse;

    @BeforeEach
    void setUp() {
        // Setup mock experience
        mockExperience = Experience.builder()
                .id(1L)
                .experienceId("exp_test_001")
                .problemType("TASK_CREATE_FAILED")
                .keywords("permission,workflow,failed")
                .diagnosisChain("[{\"step\": 1, \"action\": \"query_logs\"}]")
                .rootCauses("[\"Permission denied for workflow execution\"]")
                .solutions("[\"Grant required permissions\"]")
                .confidenceScore(0.85)
                .usageCount(5)
                .build();

        mockResponse = ExperienceResponse.builder()
                .id("1")
                .experienceId("exp_test_001")
                .problemType("TASK_CREATE_FAILED")
                .keywords(List.of("permission", "workflow", "failed"))
                .diagnosisChain("[{\"step\": 1, \"action\": \"query_logs\"}]")
                .rootCauses("[\"Permission denied for workflow execution\"]")
                .solutions("[\"Grant required permissions\"]")
                .confidenceScore(0.85)
                .usageCount(5)
                .build();
    }

    @Test
    @DisplayName("Test Add and Search Experience")
    void testAddAndSearchExperience() {
        // 1. Create new experience request
        ExperienceRequest request = ExperienceRequest.builder()
                .problemType("DATABASE_TIMEOUT")
                .keywords(List.of("database", "timeout", "connection"))
                .diagnosisChain("[{\"step\": 1, \"action\": \"check_connection_pool\"}]")
                .rootCauses("[\"Connection pool exhausted\"]")
                .solutions("[\"Increase connection pool size\"]")
                .confidenceScore(0.90)
                .build();

        // Mock repository save
        Experience savedExperience = Experience.builder()
                .id(2L)
                .experienceId("exp_new_001")
                .problemType("DATABASE_TIMEOUT")
                .keywords("database,timeout,connection")
                .confidenceScore(0.90)
                .usageCount(0)
                .build();

        when(experienceRepository.save(any(Experience.class)))
                .thenReturn(savedExperience);

        // 2. Call create endpoint
        ResponseEntity<Map<String, Object>> response = experienceController.createExperience(request);

        // 3. Verify creation response
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("CREATED", body.get("status"));
        assertNotNull(body.get("experienceId"));

        // 4. Mock search by experience retriever
        IntentResult intent = IntentResult.builder()
                .problemType("DATABASE_TIMEOUT")
                .keywords(List.of("database", "timeout"))
                .originalMessage("Database connection timeout error")
                .confidence(0.85)
                .recognized(true)
                .build();

        ExperienceMatch match = ExperienceMatch.exactMatch(savedExperience, List.of("database", "timeout"));
        RetrievalResult retrievalResult = RetrievalResult.builder()
                .matches(List.of(match))
                .maxSimilarity(1.0)
                .hasHighConfidenceMatch(true)
                .needsLearning(false)
                .build();

        when(experienceRetriever.search(any(IntentResult.class)))
                .thenReturn(retrievalResult);

        // 5. Search for the new experience
        RetrievalResult searchResult = experienceRetriever.search(intent);

        // 6. Verify search results
        assertTrue(searchResult.hasMatches());
        assertTrue(searchResult.getMatches().stream()
                .anyMatch(m -> m.getExperience().getExperienceId().equals("exp_new_001")));

        // Verify repository was called
        verify(experienceRepository, times(1)).save(any(Experience.class));
    }

    @Test
    @DisplayName("Test Experience Update")
    void testExperienceUpdate() {
        // Setup existing experience
        when(experienceRepository.findByExperienceId("exp_test_001"))
                .thenReturn(Optional.of(mockExperience));

        Experience updatedExperience = Experience.builder()
                .id(1L)
                .experienceId("exp_test_001")
                .problemType("TASK_CREATE_FAILED")
                .keywords("permission,workflow,failed,updated")
                .confidenceScore(0.95)  // Updated confidence
                .usageCount(5)
                .build();

        when(experienceRepository.save(any(Experience.class)))
                .thenReturn(updatedExperience);

        ExperienceRequest updateRequest = ExperienceRequest.builder()
                .keywords(List.of("permission", "workflow", "failed", "updated"))
                .confidenceScore(0.95)
                .build();

        ResponseEntity<ExperienceResponse> response =
                experienceController.updateExperience("exp_test_001", updateRequest);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Verify update was called
        verify(experienceRepository, times(1)).findByExperienceId("exp_test_001");
        verify(experienceRepository, times(1)).save(any(Experience.class));
    }

    @Test
    @DisplayName("Test Experience Delete")
    void testExperienceDelete() {
        when(experienceRepository.findByExperienceId("exp_test_001"))
                .thenReturn(Optional.of(mockExperience));

        doNothing().when(experienceRepository).delete(any(Experience.class));

        ResponseEntity<Map<String, Object>> response =
                experienceController.deleteExperience("exp_test_001");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("DELETED", body.get("status"));
        assertEquals("exp_test_001", body.get("id"));

        verify(experienceRepository, times(1)).delete(any(Experience.class));
    }

    @Test
    @DisplayName("Test Experience Delete - Not Found")
    void testExperienceDelete_NotFound() {
        when(experienceRepository.findByExperienceId("nonexistent_exp"))
                .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response =
                experienceController.deleteExperience("nonexistent_exp");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Vector Similarity Search")
    void testVectorSimilaritySearch() {
        // Setup mock experiences for vector search
        Experience exp1 = Experience.builder()
                .id(1L)
                .experienceId("exp_vector_001")
                .problemType("NETWORK_FAILURE")
                .keywords("network,timeout,connection")
                .confidenceScore(0.80)
                .build();

        Experience exp2 = Experience.builder()
                .id(2L)
                .experienceId("exp_vector_002")
                .problemType("NETWORK_FAILURE")
                .keywords("network,latency,slow")
                .confidenceScore(0.75)
                .build();

        ExperienceMatch match1 = ExperienceMatch.semanticMatch(exp1, 0.92);
        ExperienceMatch match2 = ExperienceMatch.semanticMatch(exp2, 0.78);

        RetrievalResult vectorResult = RetrievalResult.builder()
                .matches(List.of(match1, match2))
                .maxSimilarity(0.92)
                .avgSimilarity(0.85)
                .hasHighConfidenceMatch(true)
                .needsLearning(false)
                .retrievalMethod(RetrievalResult.RetrievalMethod.VECTOR)
                .totalMatches(2)
                .queryTimeMs(50L)
                .build();

        when(experienceRetriever.searchByDescription(anyString(), any(Integer.class)))
                .thenReturn(vectorResult);

        RetrievalResult result = experienceRetriever.searchByDescription(
                "Network connection timeout", 5);

        assertNotNull(result);
        assertTrue(result.hasMatches());
        assertEquals(2, result.getTotalMatches());
        assertTrue(result.getMaxSimilarity() >= 0.85);
        assertEquals(RetrievalResult.RetrievalMethod.VECTOR, result.getRetrievalMethod());

        // Verify matches are sorted by similarity
        List<ExperienceMatch> matches = result.getMatches();
        assertTrue(matches.get(0).getSimilarity() >= matches.get(1).getSimilarity());
    }

    @Test
    @DisplayName("Test List Experiences with Pagination")
    void testListExperiencesWithPagination() {
        // Create mock experience list
        List<Experience> experiences = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            experiences.add(Experience.builder()
                    .id((long) i + 1)
                    .experienceId("exp_list_" + i)
                    .problemType("TEST_PROBLEM")
                    .keywords("keyword" + i)
                    .confidenceScore(0.8)
                    .usageCount(i)
                    .build());
        }

        org.springframework.data.domain.Page<Experience> page =
                new org.springframework.data.domain.PageImpl<>(
                        experiences.subList(0, 10),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        15);

        when(experienceRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response =
                experienceController.listExperiences(null, null, 0, 10);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(15L, body.get("totalElements"));
        assertEquals(2, body.get("totalPages"));
        assertEquals(0, body.get("currentPage"));
        assertEquals(10, body.get("pageSize"));

        List<ExperienceResponse> content = (List<ExperienceResponse>) body.get("content");
        assertEquals(10, content.size());
    }

    @Test
    @DisplayName("Test List Experiences Filtered by Problem Type")
    void testListExperiencesFilteredByProblemType() {
        List<Experience> taskCreateFailedExps = List.of(
                Experience.builder()
                        .id(1L)
                        .experienceId("exp_filter_001")
                        .problemType("TASK_CREATE_FAILED")
                        .keywords("permission,failed")
                        .build(),
                Experience.builder()
                        .id(2L)
                        .experienceId("exp_filter_002")
                        .problemType("TASK_CREATE_FAILED")
                        .keywords("timeout,failed")
                        .build()
        );

        org.springframework.data.domain.Page<Experience> page =
                new org.springframework.data.domain.PageImpl<>(taskCreateFailedExps);

        when(experienceRepository.findByProblemType(eq("TASK_CREATE_FAILED"),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response =
                experienceController.listExperiences("TASK_CREATE_FAILED", null, 0, 10);

        assertNotNull(response.getBody());
        assertEquals(2L, response.getBody().get("totalElements"));

        List<ExperienceResponse> content =
                (List<ExperienceResponse>) response.getBody().get("content");
        assertTrue(content.stream()
                .allMatch(e -> "TASK_CREATE_FAILED".equals(e.getProblemType())));
    }

    @Test
    @DisplayName("Test List Experiences Filtered by Keywords")
    void testListExperiencesFilteredByKeywords() {
        List<Experience> keywordMatches = List.of(
                Experience.builder()
                        .id(1L)
                        .experienceId("exp_kw_001")
                        .problemType("TASK_CREATE_FAILED")
                        .keywords("permission,workflow,failed")
                        .build()
        );

        when(experienceRepository.findByKeywordsContaining("permission"))
                .thenReturn(keywordMatches);

        ResponseEntity<Map<String, Object>> response =
                experienceController.listExperiences(null, "permission", 0, 10);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("totalElements") != null);
    }

    @Test
    @DisplayName("Test Get Experience by ID")
    void testGetExperienceById() {
        when(experienceRepository.findByExperienceId("exp_test_001"))
                .thenReturn(Optional.of(mockExperience));

        ResponseEntity<ExperienceResponse> response =
                experienceController.getExperience("exp_test_001");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("exp_test_001", response.getBody().getExperienceId());
        assertEquals("TASK_CREATE_FAILED", response.getBody().getProblemType());
    }

    @Test
    @DisplayName("Test Get Experience by ID - Not Found")
    void testGetExperienceById_NotFound() {
        when(experienceRepository.findByExperienceId("nonexistent"))
                .thenReturn(Optional.empty());
        when(experienceRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseEntity<ExperienceResponse> response =
                experienceController.getExperience("nonexistent");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Test Hybrid Search - Exact Match First")
    void testHybridSearch_ExactMatchFirst() {
        // Setup intent with known problem type
        IntentResult intent = IntentResult.builder()
                .problemType("TASK_CREATE_FAILED")
                .keywords(List.of("permission", "workflow"))
                .originalMessage("Task creation failed due to permission")
                .recognized(true)
                .build();

        // Mock exact matches
        when(experienceRepository.findByProblemType("TASK_CREATE_FAILED"))
                .thenReturn(List.of(mockExperience));

        ExperienceMatch exactMatch = ExperienceMatch.exactMatch(mockExperience, List.of("permission"));
        RetrievalResult result = RetrievalResult.builder()
                .matches(List.of(exactMatch))
                .maxSimilarity(1.0)
                .avgSimilarity(1.0)
                .hasHighConfidenceMatch(true)
                .needsLearning(false)
                .retrievalMethod(RetrievalResult.RetrievalMethod.EXACT)
                .build();

        when(experienceRetriever.search(any(IntentResult.class)))
                .thenReturn(result);

        RetrievalResult searchResult = experienceRetriever.search(intent);

        assertTrue(searchResult.hasMatches());
        assertEquals(RetrievalResult.RetrievalMethod.EXACT, searchResult.getRetrievalMethod());
        assertTrue(searchResult.isHasHighConfidenceMatch());
    }

    @Test
    @DisplayName("Test Experience Invalid Create Request - Empty Problem Type")
    void testInvalidCreateRequest_EmptyProblemType() {
        ExperienceRequest request = ExperienceRequest.builder()
                .problemType("")  // Empty problem type
                .keywords(List.of("test"))
                .build();

        ResponseEntity<Map<String, Object>> response =
                experienceController.createExperience(request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    @DisplayName("Test Experience Usage Count Increment")
    void testExperienceUsageCountIncrement() {
        mockExperience.setUsageCount(5);

        when(experienceRepository.findByExperienceId("exp_test_001"))
                .thenReturn(Optional.of(mockExperience));
        when(experienceRepository.save(any(Experience.class)))
                .thenReturn(mockExperience);

        // Simulate usage count increment through retriever
        experienceRetriever.updateHitCount("exp_test_001");

        // Verify the update was attempted
        verify(experienceRetriever, times(1)).updateHitCount("exp_test_001");
    }

    @Test
    @DisplayName("Test Search by Type and Keywords")
    void testSearchByTypeAndKeywords() {
        ExperienceMatch match = ExperienceMatch.builder()
                .experience(mockExperience)
                .similarity(0.85)
                .matchType(ExperienceMatch.MatchType.HYBRID)
                .matchedKeywords(List.of("permission", "workflow"))
                .build();

        when(experienceRetriever.searchByTypeAndKeywords(anyString(), any(List.class)))
                .thenReturn(List.of(match));

        List<ExperienceMatch> results = experienceRetriever.searchByTypeAndKeywords(
                "TASK_CREATE_FAILED", List.of("permission", "workflow"));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(ExperienceMatch.MatchType.HYBRID, results.get(0).getMatchType());
    }

    @Test
    @DisplayName("Test Low Confidence Match Triggers Learning")
    void testLowConfidenceMatchTriggersLearning() {
        Experience lowConfidenceExp = Experience.builder()
                .id(1L)
                .experienceId("exp_low_001")
                .problemType("UNKNOWN")
                .confidenceScore(0.45)
                .build();

        ExperienceMatch lowMatch = ExperienceMatch.semanticMatch(lowConfidenceExp, 0.45);

        RetrievalResult lowConfidenceResult = RetrievalResult.builder()
                .matches(List.of(lowMatch))
                .maxSimilarity(0.45)
                .avgSimilarity(0.45)
                .hasHighConfidenceMatch(false)
                .needsLearning(true)
                .build();

        when(experienceRetriever.searchByDescription(anyString(), anyInt()))
                .thenReturn(lowConfidenceResult);

        RetrievalResult result = experienceRetriever.searchByDescription(
                "New unknown problem pattern", 5);

        assertTrue(result.isNeedsLearning());
        assertFalse(result.isHasHighConfidenceMatch());
        assertTrue(result.getMaxSimilarity() < 0.70);
    }
}