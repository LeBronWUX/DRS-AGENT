package com.drs.agent.repository;

import com.drs.agent.model.TaskExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Task Execution Log Repository
 *
 * Provides data access for task execution logs.
 */
@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    Optional<TaskExecutionLog> findByTaskId(String taskId);

    List<TaskExecutionLog> findByTaskType(String taskType);

    List<TaskExecutionLog> findByStatus(String status);
}