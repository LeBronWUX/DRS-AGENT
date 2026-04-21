package com.drs.agent.service;

import com.drs.agent.model.TaskExecutionLog;
import com.drs.agent.repository.TaskExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Task Execution Service
 *
 * Manages task execution logging and status tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskExecutionLogRepository taskExecutionLogRepository;

    public String generateTaskId() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public TaskExecutionLog startTask(String taskType, String inputData) {
        String taskId = generateTaskId();
        TaskExecutionLog log = TaskExecutionLog.builder()
                .taskId(taskId)
                .taskType(taskType)
                .status("RUNNING")
                .inputData(inputData)
                .build();
        return taskExecutionLogRepository.save(log);
    }

    @Transactional
    public TaskExecutionLog completeTask(String taskId, String outputData, long executionTimeMs) {
        TaskExecutionLog log = taskExecutionLogRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        log.setStatus("COMPLETED");
        log.setOutputData(outputData);
        log.setExecutionTimeMs(executionTimeMs);
        return taskExecutionLogRepository.save(log);
    }

    @Transactional
    public TaskExecutionLog failTask(String taskId, String errorMessage) {
        TaskExecutionLog log = taskExecutionLogRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        log.setStatus("FAILED");
        log.setErrorMessage(errorMessage);
        return taskExecutionLogRepository.save(log);
    }

    public Optional<TaskExecutionLog> getTask(String taskId) {
        return taskExecutionLogRepository.findByTaskId(taskId);
    }

    public List<TaskExecutionLog> getTasksByType(String taskType) {
        return taskExecutionLogRepository.findByTaskType(taskType);
    }

    public List<TaskExecutionLog> getTasksByStatus(String status) {
        return taskExecutionLogRepository.findByStatus(status);
    }
}