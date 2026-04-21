package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool Test Request DTO
 *
 * Request body for testing a tool with sample parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolTestRequest {

    /**
     * Test parameters to pass to the tool.
     */
    private Map<String, Object> params;
}