package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserFeedback DTO
 *
 * Represents user feedback on a diagnosis result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedback {

    /**
     * Whether the diagnosis was correct
     */
    private boolean isCorrect;

    /**
     * Corrected root cause (if diagnosis was wrong)
     */
    private String correctedRootCause;

    /**
     * Corrected solution (if solution was wrong)
     */
    private String correctedSolution;

    /**
     * Rating score (1-5)
     */
    private int rating;

    /**
     * User comment/feedback
     */
    private String comment;

    /**
     * User ID who provided feedback
     */
    private String userId;

    /**
     * Whether to add to experience library
     */
    private boolean addToExperience;

    /**
     * Additional notes
     */
    private String notes;
}