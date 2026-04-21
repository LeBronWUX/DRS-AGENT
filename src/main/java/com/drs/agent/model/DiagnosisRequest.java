package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diagnosis Request DTO
 *
 * Represents a diagnosis request from the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisRequest {

    /**
     * Session ID for continuing an existing diagnosis session
     */
    private String sessionId;

    /**
     * Problem description from user
     */
    private String problem;

    /**
     * Additional context information (JSON format)
     */
    private String context;

    /**
     * User ID who initiated the diagnosis
     */
    private String userId;

    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String priority;
}