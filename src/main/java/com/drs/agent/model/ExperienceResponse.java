package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Experience Response DTO
 *
 * Represents the experience data returned to the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceResponse {

    /**
     * Experience ID
     */
    private String id;

    /**
     * Experience unique identifier
     */
    private String experienceId;

    /**
     * Problem type
     */
    private String problemType;

    /**
     * Keywords for search
     */
    private List<String> keywords;

    /**
     * Diagnosis chain steps
     */
    private String diagnosisChain;

    /**
     * Root causes identified
     */
    private String rootCauses;

    /**
     * Solutions applied
     */
    private String solutions;

    /**
     * Confidence score
     */
    private Double confidenceScore;

    /**
     * Number of times this experience was used
     */
    private Integer usageCount;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
}