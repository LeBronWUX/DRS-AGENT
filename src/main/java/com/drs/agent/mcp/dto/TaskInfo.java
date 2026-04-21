package com.drs.agent.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Task Info DTO
 *
 * Represents task workflow information from the operations platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfo {

    private String workflowId;
    private String taskId;
    private String taskName;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String serviceName;
    private String environment;
    private String metadata;
}