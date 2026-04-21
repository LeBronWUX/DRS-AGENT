package com.drs.agent.service.dto;

import com.drs.agent.model.DiagnosisResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PendingConfirmation DTO
 *
 * Represents a pending confirmation task for manual review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingConfirmation {

    /**
     * Confirmation task ID
     */
    private String confirmationId;

    /**
     * Session ID for the diagnosis
     */
    private String sessionId;

    /**
     * Problem description
     */
    private String problem;

    /**
     * Diagnosis result
     */
    private DiagnosisResponse result;

    /**
     * Problem type
     */
    private String problemType;

    /**
     * Current root cause prediction
     */
    private String predictedRootCause;

    /**
     * Confidence score
     */
    private double confidence;

    /**
     * Reason for pending confirmation
     */
    private String reason;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Expiration timestamp
     */
    private LocalDateTime expiresAt;

    /**
     * Status: PENDING, CONFIRMED, REJECTED, EXPIRED
     */
    private String status;

    /**
     * User ID who submitted the diagnosis
     */
    private String userId;
}