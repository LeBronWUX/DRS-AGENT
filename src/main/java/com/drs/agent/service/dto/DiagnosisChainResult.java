package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Diagnosis Chain Execution Result
 *
 * Contains the complete result of executing a diagnosis chain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisChainResult {

    /**
     * List of step execution results.
     */
    private List<StepResult> stepResults;

    /**
     * Whether the entire chain execution was successful.
     */
    private boolean success;

    /**
     * Error message if the chain execution failed.
     */
    private String error;

    /**
     * Total execution time in milliseconds.
     */
    private long totalExecutionTime;

    /**
     * Number of successful steps.
     */
    private int successfulSteps;

    /**
     * Number of failed steps.
     */
    private int failedSteps;

    /**
     * Aggregated context from all steps.
     */
    private java.util.Map<String, Object> aggregatedContext;

    /**
     * Summary of the diagnosis chain execution.
     */
    private String summary;

    /**
     * Chain template source (EXPERIENCE, CLAUDE_GENERATED, DEFAULT).
     */
    private String chainSource;

    /**
     * Whether the chain was interrupted early.
     */
    private boolean interruptedEarly;

    /**
     * Reason for early interruption (if applicable).
     */
    private String interruptionReason;
}