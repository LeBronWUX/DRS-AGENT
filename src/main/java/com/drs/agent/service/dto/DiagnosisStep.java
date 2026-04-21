package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Diagnosis Step Definition
 *
 * Defines a single step in the diagnosis chain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisStep {

    /**
     * Step order/sequence number.
     */
    private int stepOrder;

    /**
     * Action description for this step.
     */
    private String action;

    /**
     * Tool name to invoke for this step.
     */
    private String tool;

    /**
     * Parameters template for the tool invocation.
     * Values may contain placeholders like ${workflowId} that need to be resolved.
     */
    private Map<String, String> params;

    /**
     * Expected output description.
     */
    private String expectedOutput;

    /**
     * Fallback action when this step fails.
     */
    private String fallback;

    /**
     * Whether this step is required for the diagnosis.
     */
    private boolean required;

    /**
     * Timeout in milliseconds for this step.
     */
    private long timeoutMs;

    /**
     * Step name/identifier.
     */
    private String stepName;

    /**
     * Description of what this step does.
     */
    private String description;
}