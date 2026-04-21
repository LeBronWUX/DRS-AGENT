package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step Execution Result
 *
 * Contains the result of executing a single diagnosis step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    /**
     * Step order/sequence number.
     */
    private int stepOrder;

    /**
     * Name of the tool that was executed.
     */
    private String toolName;

    /**
     * Whether the step execution was successful.
     */
    private boolean success;

    /**
     * Data returned by the tool.
     */
    private Object data;

    /**
     * Error message if the step failed.
     */
    private String error;

    /**
     * Execution time in milliseconds.
     */
    private long executionTime;

    /**
     * Step name/identifier.
     */
    private String stepName;

    /**
     * Action description that was executed.
     */
    private String action;

    /**
     * Raw output from the tool (for debugging).
     */
    private String rawOutput;

    /**
     * Whether fallback was used.
     */
    private boolean usedFallback;
}