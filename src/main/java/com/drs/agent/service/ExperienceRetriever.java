package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.Experience;
import com.drs.agent.model.ExperienceMatch;
import com.drs.agent.model.IntentResult;
import com.drs.agent.model.RetrievalResult;
import com.drs.agent.repository.ExperienceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Experience Retriever
 *
 * Handles retrieval of similar experiences from the knowledge base using
 * hybrid search strategy combining exact matching and vector similarity.
 *
 * Features:
 * - Hybrid search: exact match + vector similarity
 * - Confidence-based decision making
 * - Learning trigger detection
 * - Hit count tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceRetriever {

    private final McpToolRegistry toolRegistry;
    private final ExperienceRepository experienceRepository;
    private final ExperienceService experienceService;
    private final ObjectMapper objectMapper;

    /**
     * High confidence threshold - matches above this can use experience directly
     */
    @Value("${experience.retrieval.high-confidence-threshold:0.85}")
    private double highConfidenceThreshold;

    /**
     * Low confidence threshold - matches below this should trigger learning
     */
    @Value("${experience.retrieval.low-confidence-threshold:0.70}")
    private double lowConfidenceThreshold;

    /**
     * Default number of results to return
     */
    @Value("${experience.retrieval.default-top-k:5}")
    private int defaultTopK;

    /**
     * Minimum similarity score to consider a match
     */
    @Value("${experience.retrieval.min-similarity:0.50}")
    private double minSimilarity;

    /**
     * Search for similar experiences based on intent recognition result.
     *
     * This method implements a hybrid search strategy:
     * 1. First try exact match on problem type + keywords
     * 2. If no exact match, fall back to vector semantic search
     * 3. Combine and rank results
     *
     * @param intent Intent recognition result
     * @return RetrievalResult containing matched experiences and confidence info
     */
    public RetrievalResult search(IntentResult intent) {
        long startTime = System.currentTimeMillis();
        log.info("Searching for experiences: problemType={}, keywords={}",
                intent.getProblemType(), intent.getKeywords());

        // Handle unrecognized intent
        if (!intent.isRecognized() || intent.getProblemType() == null) {
            log.info("Intent not recognized, falling back to vector search");
            return searchByDescription(intent.getOriginalMessage(), defaultTopK);
        }

        // Step 1: Try exact match first
        List<Experience> exactMatches = findExactMatches(intent.getProblemType(), intent.getKeywords());
        if (!exactMatches.isEmpty()) {
            log.info("Found {} exact matches", exactMatches.size());
            List<ExperienceMatch> matches = exactMatches.stream()
                    .map(exp -> ExperienceMatch.exactMatch(exp, findMatchedKeywords(exp, intent.getKeywords())))
                    .toList();

            long queryTime = System.currentTimeMillis() - startTime;
            RetrievalResult result = RetrievalResult.builder()
                    .matches(matches)
                    .maxSimilarity(1.0)
                    .avgSimilarity(1.0)
                    .hasHighConfidenceMatch(true)
                    .needsLearning(false)
                    .retrievalMethod(RetrievalResult.RetrievalMethod.EXACT)
                    .totalMatches(matches.size())
                    .queryTimeMs(queryTime)
                    .build();

            // Update hit counts
            matches.forEach(m -> updateHitCount(m.getExperience().getExperienceId()));
            return result;
        }

        // Step 2: Vector semantic search via MCP tool
        log.info("No exact matches found, performing vector search");
        RetrievalResult vectorResult = searchByDescription(intent.getOriginalMessage(), defaultTopK);

        // Step 3: Enrich with keyword matching info
        if (vectorResult.hasMatches()) {
            List<ExperienceMatch> enrichedMatches = vectorResult.getMatches().stream()
                    .map(m -> enrichMatch(m, intent))
                    .filter(m -> m.getSimilarity() >= minSimilarity)
                    .sorted((m1, m2) -> Double.compare(m2.getSimilarity(), m1.getSimilarity()))
                    .toList();

            long queryTime = System.currentTimeMillis() - startTime;
            return RetrievalResult.builder()
                    .matches(enrichedMatches)
                    .maxSimilarity(vectorResult.getMaxSimilarity())
                    .avgSimilarity(calculateAvgSimilarity(enrichedMatches))
                    .hasHighConfidenceMatch(isHighConfidence(vectorResult))
                    .needsLearning(shouldTriggerLearning(vectorResult))
                    .retrievalMethod(RetrievalResult.RetrievalMethod.HYBRID)
                    .totalMatches(enrichedMatches.size())
                    .queryTimeMs(queryTime)
                    .build();
        }

        // No matches found
        log.info("No matching experiences found");
        return RetrievalResult.builder()
                .matches(new ArrayList<>())
                .maxSimilarity(0.0)
                .avgSimilarity(0.0)
                .hasHighConfidenceMatch(false)
                .needsLearning(true)
                .retrievalMethod(RetrievalResult.RetrievalMethod.VECTOR)
                .totalMatches(0)
                .queryTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Search for similar experiences by problem description.
     *
     * Uses MCP tool to perform vector similarity search.
     *
     * @param problemDescription Problem description text
     * @param topK Number of results to return
     * @return RetrievalResult containing matched experiences
     */
    public RetrievalResult searchByDescription(String problemDescription, int topK) {
        long startTime = System.currentTimeMillis();
        log.info("Searching by description: topK={}", topK);

        try {
            // Call MCP tool for vector search
            Map<String, Object> params = new HashMap<>();
            params.put("problemDescription", problemDescription);
            params.put("topK", topK);

            ToolResult toolResult = toolRegistry.executeTool("search_experience", params);

            if (!toolResult.isSuccess()) {
                log.warn("Vector search failed: {}", toolResult.getError());
                return RetrievalResult.empty();
            }

            // Parse results
            List<ExperienceMatch> matches = parseVectorSearchResults(toolResult);
            long queryTime = System.currentTimeMillis() - startTime;

            if (matches.isEmpty()) {
                return RetrievalResult.builder()
                        .matches(new ArrayList<>())
                        .maxSimilarity(0.0)
                        .avgSimilarity(0.0)
                        .hasHighConfidenceMatch(false)
                        .needsLearning(true)
                        .retrievalMethod(RetrievalResult.RetrievalMethod.VECTOR)
                        .totalMatches(0)
                        .queryTimeMs(queryTime)
                        .build();
            }

            // Calculate similarity statistics
            double maxSim = matches.stream()
                    .mapToDouble(ExperienceMatch::getSimilarity)
                    .max()
                    .orElse(0.0);

            double avgSim = matches.stream()
                    .mapToDouble(ExperienceMatch::getSimilarity)
                    .average()
                    .orElse(0.0);

            return RetrievalResult.builder()
                    .matches(matches)
                    .maxSimilarity(maxSim)
                    .avgSimilarity(avgSim)
                    .hasHighConfidenceMatch(maxSim >= highConfidenceThreshold)
                    .needsLearning(maxSim < lowConfidenceThreshold)
                    .retrievalMethod(RetrievalResult.RetrievalMethod.VECTOR)
                    .totalMatches(matches.size())
                    .queryTimeMs(queryTime)
                    .build();

        } catch (Exception e) {
            log.error("Error during vector search: {}", e.getMessage(), e);
            return RetrievalResult.empty();
        }
    }

    /**
     * Search by problem type and keywords using hybrid approach.
     *
     * Combines MySQL exact matching with Milvus vector search.
     *
     * @param problemType Problem type category
     * @param keywords List of keywords
     * @return List of experience matches
     */
    public List<ExperienceMatch> searchByTypeAndKeywords(String problemType, List<String> keywords) {
        log.info("Searching by type and keywords: type={}, keywords={}", problemType, keywords);
        List<ExperienceMatch> allMatches = new ArrayList<>();

        // Step 1: MySQL exact match on problem type
        List<Experience> typeMatches = experienceRepository.findByProblemType(problemType);

        // Step 2: Keyword filtering and matching
        for (Experience exp : typeMatches) {
            List<String> matchedKeywords = findMatchedKeywords(exp, keywords);
            if (!matchedKeywords.isEmpty()) {
                double similarity = calculateKeywordSimilarity(keywords, matchedKeywords);
                allMatches.add(ExperienceMatch.builder()
                        .experience(exp)
                        .similarity(similarity)
                        .matchType(ExperienceMatch.MatchType.HYBRID)
                        .matchedKeywords(matchedKeywords)
                        .build());
            }
        }

        // Step 3: If no good matches, try vector search
        if (allMatches.isEmpty() || allMatches.stream().allMatch(m -> m.getSimilarity() < 0.7)) {
            String queryText = problemType + " " + String.join(" ", keywords);
            RetrievalResult vectorResult = searchByDescription(queryText, defaultTopK);

            // Merge results, avoiding duplicates
            Set<String> existingIds = allMatches.stream()
                    .map(m -> m.getExperience().getExperienceId())
                    .collect(Collectors.toSet());

            vectorResult.getMatches().stream()
                    .filter(m -> !existingIds.contains(m.getExperience().getExperienceId()))
                    .forEach(allMatches::add);
        }

        // Sort by similarity and return
        return allMatches.stream()
                .sorted((m1, m2) -> Double.compare(m2.getSimilarity(), m1.getSimilarity()))
                .limit(defaultTopK)
                .toList();
    }

    /**
     * Calculate similarity score between intent and experience.
     *
     * Uses a combination of:
     * - Problem type match score
     * - Keyword overlap score
     * - Entity matching score
     *
     * @param intent Intent recognition result
     * @param experience Experience to compare against
     * @return Similarity score (0.0 - 1.0)
     */
    public double calculateSimilarity(IntentResult intent, Experience experience) {
        double score = 0.0;
        double totalWeight = 0.0;

        // Problem type match (weight: 0.4)
        if (intent.getProblemType() != null && experience.getProblemType() != null) {
            if (intent.getProblemType().equalsIgnoreCase(experience.getProblemType())) {
                score += 0.4;
            }
            totalWeight += 0.4;
        }

        // Keyword overlap (weight: 0.4)
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            List<String> matchedKeywords = findMatchedKeywords(experience, intent.getKeywords());
            double keywordScore = calculateKeywordSimilarity(intent.getKeywords(), matchedKeywords);
            score += keywordScore * 0.4;
            totalWeight += 0.4;
        }

        // Entity matching (weight: 0.2)
        if (intent.getEntities() != null && !intent.getEntities().isEmpty()) {
            double entityScore = calculateEntityScore(intent.getEntities(), experience);
            score += entityScore * 0.2;
            totalWeight += 0.2;
        }

        // Normalize score
        if (totalWeight > 0) {
            return score / totalWeight;
        }
        return 0.0;
    }

    /**
     * Check if the result has high confidence matches.
     *
     * @param result Retrieval result
     * @return true if there is at least one high confidence match
     */
    public boolean isHighConfidence(RetrievalResult result) {
        if (result == null || !result.hasMatches()) {
            return false;
        }
        return result.getMaxSimilarity() >= highConfidenceThreshold;
    }

    /**
     * Check if learning should be triggered.
     *
     * Learning is triggered when:
     * - No matches found
     * - All matches are below low confidence threshold
     *
     * @param result Retrieval result
     * @return true if learning should be triggered
     */
    public boolean shouldTriggerLearning(RetrievalResult result) {
        if (result == null || !result.hasMatches()) {
            return true;
        }
        return result.getMaxSimilarity() < lowConfidenceThreshold;
    }

    /**
     * Update the hit count for an experience.
     *
     * @param experienceId Experience ID to update
     */
    @Transactional
    public void updateHitCount(String experienceId) {
        experienceRepository.findByExperienceId(experienceId).ifPresent(exp -> {
            exp.setUsageCount(exp.getUsageCount() + 1);
            experienceRepository.save(exp);
            log.debug("Updated hit count for experience: {} -> {}", experienceId, exp.getUsageCount());
        });
    }

    // ================== Private Helper Methods ==================

    /**
     * Find exact matches on problem type and keywords.
     */
    private List<Experience> findExactMatches(String problemType, List<String> keywords) {
        if (problemType == null) {
            return new ArrayList<>();
        }

        // Get all experiences with matching problem type
        List<Experience> candidates = experienceRepository.findByProblemType(problemType);

        if (keywords == null || keywords.isEmpty()) {
            return candidates;
        }

        // Filter by keyword match
        return candidates.stream()
                .filter(exp -> hasAllKeywords(exp, keywords))
                .toList();
    }

    /**
     * Check if experience contains all specified keywords.
     */
    private boolean hasAllKeywords(Experience experience, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }

        String expKeywords = experience.getKeywords() != null ? experience.getKeywords().toLowerCase() : "";
        String combined = expKeywords + " " +
                (experience.getProblemType() != null ? experience.getProblemType().toLowerCase() : "") +
                " " + (experience.getRootCauses() != null ? experience.getRootCauses().toLowerCase() : "") +
                " " + (experience.getDiagnosisChain() != null ? experience.getDiagnosisChain().toLowerCase() : "");

        // Check if at least half of the keywords match
        long matchedCount = keywords.stream()
                .filter(k -> combined.contains(k.toLowerCase()))
                .count();

        return matchedCount >= keywords.size() / 2.0;
    }

    /**
     * Find which keywords from the list match the experience.
     */
    private List<String> findMatchedKeywords(Experience experience, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }

        String combined = (experience.getKeywords() != null ? experience.getKeywords().toLowerCase() : "") +
                " " + (experience.getProblemType() != null ? experience.getProblemType().toLowerCase() : "") +
                " " + (experience.getRootCauses() != null ? experience.getRootCauses().toLowerCase() : "");

        return keywords.stream()
                .filter(k -> combined.contains(k.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Calculate keyword similarity score.
     */
    private double calculateKeywordSimilarity(List<String> queryKeywords, List<String> matchedKeywords) {
        if (queryKeywords == null || queryKeywords.isEmpty()) {
            return 0.0;
        }
        if (matchedKeywords == null || matchedKeywords.isEmpty()) {
            return 0.0;
        }

        return (double) matchedKeywords.size() / queryKeywords.size();
    }

    /**
     * Calculate entity matching score.
     */
    private double calculateEntityScore(List<String> entities, Experience experience) {
        if (entities == null || entities.isEmpty()) {
            return 0.0;
        }

        String combined = (experience.getKeywords() != null ? experience.getKeywords().toLowerCase() : "") +
                " " + (experience.getDiagnosisChain() != null ? experience.getDiagnosisChain().toLowerCase() : "") +
                " " + (experience.getRootCauses() != null ? experience.getRootCauses().toLowerCase() : "") +
                " " + (experience.getSolutions() != null ? experience.getSolutions().toLowerCase() : "");

        long matchedCount = entities.stream()
                .filter(e -> combined.contains(e.toLowerCase()))
                .count();

        return (double) matchedCount / entities.size();
    }

    /**
     * Parse vector search results from MCP tool response.
     */
    @SuppressWarnings("unchecked")
    private List<ExperienceMatch> parseVectorSearchResults(ToolResult toolResult) {
        List<ExperienceMatch> matches = new ArrayList<>();

        try {
            Object data = toolResult.getData();
            if (data instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) data;
                Object dataList = resultMap.get("data");

                if (dataList instanceof List) {
                    List<Map<String, Object>> experiences = (List<Map<String, Object>>) dataList;

                    for (Map<String, Object> expData : experiences) {
                        ExperienceMatch match = parseExperienceMatch(expData);
                        if (match != null) {
                            matches.add(match);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing vector search results: {}", e.getMessage(), e);
        }

        return matches;
    }

    /**
     * Parse a single experience match from the result data.
     */
    private ExperienceMatch parseExperienceMatch(Map<String, Object> expData) {
        try {
            String experienceId = (String) expData.get("experienceId");
            if (experienceId == null) {
                return null;
            }

            // Get the full experience from repository
            Optional<Experience> expOpt = experienceRepository.findByExperienceId(experienceId);
            if (expOpt.isEmpty()) {
                log.warn("Experience not found in repository: {}", experienceId);
                return null;
            }

            Experience experience = expOpt.get();

            // Calculate similarity based on confidence score or default
            double similarity = 0.8; // Default similarity for vector matches
            if (expData.get("confidenceScore") instanceof Number) {
                similarity = ((Number) expData.get("confidenceScore")).doubleValue();
            } else if (experience.getConfidenceScore() != null) {
                similarity = experience.getConfidenceScore();
            }

            return ExperienceMatch.semanticMatch(experience, similarity);

        } catch (Exception e) {
            log.error("Error parsing experience match: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Enrich a match with keyword matching information.
     */
    private ExperienceMatch enrichMatch(ExperienceMatch match, IntentResult intent) {
        if (intent.getKeywords() == null || intent.getKeywords().isEmpty()) {
            return match;
        }

        List<String> matchedKeywords = findMatchedKeywords(match.getExperience(), intent.getKeywords());
        double keywordScore = calculateKeywordSimilarity(intent.getKeywords(), matchedKeywords);

        // Combine vector similarity with keyword score (weighted)
        double combinedSimilarity = match.getSimilarity() * 0.7 + keywordScore * 0.3;

        ExperienceMatch enrichedMatch = ExperienceMatch.builder()
                .experience(match.getExperience())
                .similarity(combinedSimilarity)
                .matchType(ExperienceMatch.MatchType.HYBRID)
                .matchedKeywords(matchedKeywords)
                .matchDetails("Hybrid match: vector=" + match.getSimilarity() + ", keyword=" + keywordScore)
                .build();

        return enrichedMatch;
    }

    /**
     * Calculate average similarity from matches.
     */
    private double calculateAvgSimilarity(List<ExperienceMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }
        return matches.stream()
                .mapToDouble(ExperienceMatch::getSimilarity)
                .average()
                .orElse(0.0);
    }
}