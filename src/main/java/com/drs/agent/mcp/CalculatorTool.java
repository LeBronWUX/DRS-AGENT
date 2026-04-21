package com.drs.agent.mcp;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculator Tool
 *
 * Performs basic mathematical calculations.
 * This tool demonstrates parameter validation with multiple required parameters.
 */
@Slf4j
@McpTool(
    name = "calculator",
    description = "Performs basic mathematical calculations (add, subtract, multiply, divide). Use this tool when you need to perform arithmetic operations.",
    inputParams = "[{\"name\":\"operation\",\"type\":\"string\",\"required\":true,\"description\":\"The operation to perform\",\"enumValues\":[\"add\",\"subtract\",\"multiply\",\"divide\"]},{\"name\":\"operand1\",\"type\":\"number\",\"required\":true,\"description\":\"The first operand\"},{\"name\":\"operand2\",\"type\":\"number\",\"required\":true,\"description\":\"The second operand\"}]",
    outputFormat = "{\"result\":\"number\",\"operation\":\"string\",\"expression\":\"string\"}"
)
public class CalculatorTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing calculator tool with parameters: {}", parameters);

        try {
            String operation = (String) parameters.get("operation");
            double operand1 = toDouble(parameters.get("operand1"));
            double operand2 = toDouble(parameters.get("operand2"));

            double result;
            String expression;

            switch (operation.toLowerCase()) {
                case "add":
                    result = operand1 + operand2;
                    expression = operand1 + " + " + operand2 + " = " + result;
                    break;
                case "subtract":
                    result = operand1 - operand2;
                    expression = operand1 + " - " + operand2 + " = " + result;
                    break;
                case "multiply":
                    result = operand1 * operand2;
                    expression = operand1 + " * " + operand2 + " = " + result;
                    break;
                case "divide":
                    if (operand2 == 0) {
                        return ToolResult.failure("Division by zero is not allowed");
                    }
                    result = operand1 / operand2;
                    expression = operand1 + " / " + operand2 + " = " + result;
                    break;
                default:
                    return ToolResult.failure("Unknown operation: " + operation);
            }

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);
            output.put("operation", operation);
            output.put("expression", expression);

            log.info("Calculator tool executed successfully: {}", expression);
            return ToolResult.success(output);

        } catch (Exception e) {
            log.error("Error executing calculator tool", e);
            return ToolResult.failure("Calculation error: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        // Check required parameters
        if (parameters.get("operation") == null) {
            return ValidationResult.failure("Missing required parameter: operation");
        }
        if (parameters.get("operand1") == null) {
            return ValidationResult.failure("Missing required parameter: operand1");
        }
        if (parameters.get("operand2") == null) {
            return ValidationResult.failure("Missing required parameter: operand2");
        }

        // Validate operation type
        Object operation = parameters.get("operation");
        if (!(operation instanceof String)) {
            return ValidationResult.failure("Parameter 'operation' must be a string");
        }

        String operationStr = ((String) operation).toLowerCase();
        if (!operationStr.equals("add") && !operationStr.equals("subtract") &&
            !operationStr.equals("multiply") && !operationStr.equals("divide")) {
            return ValidationResult.failure("Invalid operation. Must be one of: add, subtract, multiply, divide");
        }

        // Validate operand types
        if (!isNumeric(parameters.get("operand1"))) {
            return ValidationResult.failure("Parameter 'operand1' must be a number");
        }
        if (!isNumeric(parameters.get("operand2"))) {
            return ValidationResult.failure("Parameter 'operand2' must be a number");
        }

        return ValidationResult.success();
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        throw new IllegalArgumentException("Cannot convert to number: " + value);
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}