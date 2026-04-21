package com.drs.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Diagnosis Session Entity
 *
 * Stores diagnosis session history for tracking and learning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "diagnosis_session")
public class DiagnosisSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "problem", nullable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "problem_type", length = 100)
    private String problemType;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "solution", columnDefinition = "TEXT")
    private String solution;

    @Column(name = "diagnosis_chain", columnDefinition = "TEXT")
    private String diagnosisChain;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "actual_root_cause", columnDefinition = "TEXT")
    private String actualRootCause;

    @Column(name = "actual_solution", columnDefinition = "TEXT")
    private String actualSolution;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}