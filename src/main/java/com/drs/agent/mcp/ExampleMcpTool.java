package com.drs.agent.mcp;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Example MCP Tool
 *
 * A sample MCP tool demonstrating the tool implementation pattern.
 * This tool simply echoes back the input message with a prefix.
 */
@Slf4j
@McpTool(
    name = "example_echo",
    description = "An example MCP tool that echoes back the input message. Use this tool to test the MCP framework.",
    inputParams = "[{\"name\":\"message\",\"type\":\"string\",\"required\":true,\"description\":\"The message to echo back\"}]",
    outputFormat = "{\"echoedMessage\":\"string\",\"timestamp\":\"string\"}"
)
public class ExampleMcpTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing example_echo tool with parameters: {}", parameters);

        try {
            String message = (String) parameters.get("message");
            Map<String, Object> result = Map.of(
                "echoedMessage", "Echo: " + message,
                "timestamp", java.time.Instant.now().toString()
            );

            log.info("Example tool executed successfully");
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing example tool", e);
            return ToolResult.failure("Failed to process message", e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return ValidationResult.failure("Parameters cannot be null or empty");
        }

        Object message = parameters.get("message");
        if (message == null) {
            return ValidationResult.failure("Missing required parameter: message");
        }

        if (!(message instanceof String)) {
            return ValidationResult.failure("Parameter 'message' must be a string");
        }

        return ValidationResult.success();
    }
}