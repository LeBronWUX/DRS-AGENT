package com.drs.agent.mcp;

import java.util.Map;

/**
 * MCP Tool Handler Interface
 *
 * Defines the contract for MCP (Model Context Protocol) tool implementations.
 * All MCP tools should implement this interface to be executable by the registry.
 *
 * Combined with @McpTool annotation, this provides a complete tool definition:
 * - @McpTool provides metadata (name, description, parameters)
 * - McpToolHandler provides the execution logic
 */
public interface McpToolHandler {

    /**
     * Execute the tool with the given parameters.
     *
     * @param parameters The input parameters as a Map
     * @return The execution result
     */
    ToolResult execute(Map<String, Object> parameters);

    /**
     * Validate the input parameters before execution.
     * Default implementation returns success (no validation).
     *
     * @param parameters The input parameters to validate
     * @return ValidationResult indicating if parameters are valid
     */
    default ValidationResult validate(Map<String, Object> parameters) {
        return ValidationResult.success();
    }

    /**
     * Validation Result
     */
    record ValidationResult(
            boolean valid,
            String errorMessage
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}