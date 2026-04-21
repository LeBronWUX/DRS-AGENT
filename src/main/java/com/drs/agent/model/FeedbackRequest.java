package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feedback Request DTO
 *
 * Represents user feedback on a diagnosis result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    /**
     * Rating score (1-5)
     */
    private Integer rating;

    /**
     * User comment
     */
    private String comment;

    /**
     * Whether the diagnosis was correct
     */
    private Boolean isCorrect;

    /**
     * Actual root cause (if diagnosis was incorrect)
     */
    private String actualRootCause;

    /**
     * Actual solution applied (if different from recommendation)
     */
    private String actualSolution;

    /**
     * User ID who provided feedback
     */
    private String userId;
}