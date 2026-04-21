package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Intent Result
 *
 * Represents the result of intent recognition from user message.
 * Used as input for experience retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    /**
     * Original user message
     */
    private String originalMessage;

    /**
     * Recognized problem type (e.g., "database", "network", "performance")
     */
    private String problemType;

    /**
     * Extracted keywords from the message
     */
    private List<String> keywords;

    /**
     * Extracted entities (e.g., service names, error codes)
     */
    private List<String> entities;

    /**
     * Intent confidence score (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Additional context or metadata
     */
    private String context;

    /**
     * Whether the intent was recognized successfully
     */
    private boolean recognized;

    /**
     * Create a simple intent result with problem type and keywords
     */
    public static IntentResult of(String originalMessage, String problemType, List<String> keywords) {
        return IntentResult.builder()
                .originalMessage(originalMessage)
                .problemType(problemType)
                .keywords(keywords)
                .recognized(true)
                .build();
    }

    /**
     * Create an unrecognized intent result
     */
    public static IntentResult unrecognized(String originalMessage) {
        return IntentResult.builder()
                .originalMessage(originalMessage)
                .recognized(false)
                .build();
    }
}