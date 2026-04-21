package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Experience Request DTO
 *
 * Represents a request to create or update an experience.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceRequest {

    /**
     * Problem type (e.g., NETWORK_FAILURE, DATABASE_TIMEOUT, SERVICE_DOWN)
     */
    private String problemType;

    /**
     * Keywords for search (comma-separated)
     */
    private List<String> keywords;

    /**
     * Diagnosis chain steps (JSON array)
     */
    private String diagnosisChain;

    /**
     * Root causes identified (JSON array)
     */
    private String rootCauses;

    /**
     * Solutions applied (JSON array)
     */
    private String solutions;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidenceScore;

    /**
     * Additional metadata (JSON format)
     */
    private String metadata;
}