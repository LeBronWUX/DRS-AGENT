package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Experience Match
 *
 * Represents a single matched experience with similarity score and match details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceMatch {

    /**
     * The matched experience entity
     */
    private Experience experience;

    /**
     * Similarity score (0.0 - 1.0)
     */
    private double similarity;

    /**
     * Match type: exact, semantic, or partial
     */
    private MatchType matchType;

    /**
     * Keywords that matched
     */
    @Builder.Default
    private List<String> matchedKeywords = new ArrayList<>();

    /**
     * Additional match details
     */
    private String matchDetails;

    /**
     * Match type enumeration
     */
    public enum MatchType {
        /**
         * Exact match on problem type and keywords
         */
        EXACT,

        /**
         * Semantic similarity match via vector search
         */
        SEMANTIC,

        /**
         * Partial match on some keywords
         */
        PARTIAL,

        /**
         * Hybrid match combining multiple strategies
         */
        HYBRID
    }

    /**
     * Create an exact match
     */
    public static ExperienceMatch exactMatch(Experience experience, List<String> matchedKeywords) {
        return ExperienceMatch.builder()
                .experience(experience)
                .similarity(1.0)
                .matchType(MatchType.EXACT)
                .matchedKeywords(matchedKeywords)
                .matchDetails("Exact match on problem type and keywords")
                .build();
    }

    /**
     * Create a semantic match
     */
    public static ExperienceMatch semanticMatch(Experience experience, double similarity) {
        return ExperienceMatch.builder()
                .experience(experience)
                .similarity(similarity)
                .matchType(MatchType.SEMANTIC)
                .matchDetails("Vector similarity match")
                .build();
    }

    /**
     * Create a partial match
     */
    public static ExperienceMatch partialMatch(Experience experience, double similarity, List<String> matchedKeywords) {
        return ExperienceMatch.builder()
                .experience(experience)
                .similarity(similarity)
                .matchType(MatchType.PARTIAL)
                .matchedKeywords(matchedKeywords)
                .matchDetails("Partial keyword match")
                .build();
    }

    /**
     * Check if this is a high confidence match
     */
    public boolean isHighConfidence(double threshold) {
        return similarity >= threshold;
    }
}