package com.drs.agent.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Solution DTO
 *
 * Represents the solution recommendation for a root cause.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Solution {

    /**
     * Immediate action to take (quick fix)
     */
    private String immediateAction;

    /**
     * Long-term fix recommendation
     */
    private String longTermFix;

    /**
     * Step-by-step instructions
     */
    private List<String> steps;

    /**
     * Whether this solution can be automated
     */
    private boolean automationPossible;

    /**
     * Estimated time to implement (in minutes)
     */
    private int estimatedTimeMinutes;

    /**
     * Required permissions/roles to execute
     */
    private List<String> requiredPermissions;

    /**
     * Potential risks of implementing this solution
     */
    private List<String> risks;

    /**
     * Additional notes or tips
     */
    private String notes;
}