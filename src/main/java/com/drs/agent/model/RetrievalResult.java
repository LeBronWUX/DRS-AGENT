package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrieval Result
 *
 * Represents the result of experience retrieval from the knowledge base.
 * Contains matched experiences, confidence scores, and retrieval metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {

    /**
     * List of matched experiences
     */
    @Builder.Default
    private List<ExperienceMatch> matches = new ArrayList<>();

    /**
     * Highest similarity score among all matches
     */
    private double maxSimilarity;

    /**
     * Average similarity score of all matches
     */
    private double avgSimilarity;

    /**
     * Whether there is at least one high confidence match
     */
    private boolean hasHighConfidenceMatch;

    /**
     * Whether learning should be triggered
     */
    private boolean needsLearning;

    /**
     * Retrieval method used: vector, hybrid, keyword
     */
    private RetrievalMethod retrievalMethod;

    /**
     * Total number of matches found
     */
    private int totalMatches;

    /**
     * Query execution time in milliseconds
     */
    private long queryTimeMs;

    /**
     * Additional metadata about the retrieval
     */
    private String metadata;

    /**
     * Retrieval method enumeration
     */
    public enum RetrievalMethod {
        /**
         * Vector similarity search only
         */
        VECTOR,

        /**
         * Hybrid search combining exact match and vector search
         */
        HYBRID,

        /**
         * Keyword-based search only
         */
        KEYWORD,

        /**
         * Exact match on problem type and keywords
         */
        EXACT
    }

    /**
     * Create an empty result
     */
    public static RetrievalResult empty() {
        return RetrievalResult.builder()
                .matches(new ArrayList<>())
                .maxSimilarity(0.0)
                .avgSimilarity(0.0)
                .hasHighConfidenceMatch(false)
                .needsLearning(true)
                .totalMatches(0)
                .build();
    }

    /**
     * Create a result from a single match
     */
    public static RetrievalResult of(ExperienceMatch match, RetrievalMethod method) {
        List<ExperienceMatch> matches = new ArrayList<>();
        matches.add(match);
        return RetrievalResult.builder()
                .matches(matches)
                .maxSimilarity(match.getSimilarity())
                .avgSimilarity(match.getSimilarity())
                .hasHighConfidenceMatch(match.getSimilarity() >= 0.85)
                .needsLearning(match.getSimilarity() < 0.70)
                .retrievalMethod(method)
                .totalMatches(1)
                .build();
    }

    /**
     * Create a result from multiple matches
     */
    public static RetrievalResult of(List<ExperienceMatch> matches, RetrievalMethod method) {
        if (matches == null || matches.isEmpty()) {
            return empty();
        }

        double maxSim = matches.stream()
                .mapToDouble(ExperienceMatch::getSimilarity)
                .max()
                .orElse(0.0);

        double avgSim = matches.stream()
                .mapToDouble(ExperienceMatch::getSimilarity)
                .average()
                .orElse(0.0);

        boolean hasHighConf = matches.stream()
                .anyMatch(m -> m.getSimilarity() >= 0.85);

        boolean needsLearn = maxSim < 0.70;

        return RetrievalResult.builder()
                .matches(matches)
                .maxSimilarity(maxSim)
                .avgSimilarity(avgSim)
                .hasHighConfidenceMatch(hasHighConf)
                .needsLearning(needsLearn)
                .retrievalMethod(method)
                .totalMatches(matches.size())
                .build();
    }

    /**
     * Get the best match (highest similarity)
     */
    public ExperienceMatch getBestMatch() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .max((m1, m2) -> Double.compare(m1.getSimilarity(), m2.getSimilarity()))
                .orElse(null);
    }

    /**
     * Check if there are any matches
     */
    public boolean hasMatches() {
        return matches != null && !matches.isEmpty();
    }

    /**
     * Get matches above a similarity threshold
     */
    public List<ExperienceMatch> getMatchesAboveThreshold(double threshold) {
        if (matches == null) {
            return new ArrayList<>();
        }
        return matches.stream()
                .filter(m -> m.getSimilarity() >= threshold)
                .toList();
    }
}