package com.drs.agent.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tool Execution Result
 *
 * Represents the result of a tool execution.
 * Contains success status, data, error information, and execution metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {

    /**
     * Indicates whether the tool execution was successful.
     */
    private boolean success;

    /**
     * The result data returned by the tool.
     * Can be any type of data structure.
     */
    private Object data;

    /**
     * Error message if the execution failed.
     */
    private String error;

    /**
     * Execution time in milliseconds.
     */
    private long executionTime;

    /**
     * Timestamp when the execution completed.
     */
    private Instant timestamp;

    /**
     * Additional metadata about the execution.
     */
    private Object metadata;

    /**
     * Create a successful result with data.
     *
     * @param data The result data
     * @return Successful ToolResult
     */
    public static ToolResult success(Object data) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a successful result with data and execution time.
     *
     * @param data The result data
     * @param executionTime Execution time in milliseconds
     * @return Successful ToolResult
     */
    public static ToolResult success(Object data, long executionTime) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .executionTime(executionTime)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failure result with error message.
     *
     * @param error The error message
     * @return Failed ToolResult
     */
    public static ToolResult failure(String error) {
        return ToolResult.builder()
                .success(false)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failure result with error message and execution time.
     *
     * @param error The error message
     * @param executionTime Execution time in milliseconds
     * @return Failed ToolResult
     */
    public static ToolResult failure(String error, long executionTime) {
        return ToolResult.builder()
                .success(false)
                .error(error)
                .executionTime(executionTime)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failure result with error message and exception.
     *
     * @param error The error message
     * @param exception The exception that caused the failure
     * @return Failed ToolResult
     */
    public static ToolResult failure(String error, Exception exception) {
        return ToolResult.builder()
                .success(false)
                .error(error + ": " + exception.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}