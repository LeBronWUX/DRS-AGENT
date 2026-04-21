package com.drs.agent.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Log Entry DTO
 *
 * Represents a single log entry from the logging system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    private String id;
    private String service;
    private String level;
    private String message;
    private String traceId;
    private LocalDateTime timestamp;
    private String thread;
    private String logger;
    private String exception;
    private String metadata;
}