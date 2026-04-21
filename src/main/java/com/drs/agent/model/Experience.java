package com.drs.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Experience Entity
 *
 * Represents operational experience stored for intelligent diagnosis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "experience")
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experience_id", nullable = false, unique = true, length = 64)
    private String experienceId;

    @Column(name = "problem_type", nullable = false, length = 100)
    private String problemType;

    @Column(name = "keywords", nullable = false)
    private String keywords;

    @Column(name = "diagnosis_chain", columnDefinition = "TEXT")
    private String diagnosisChain;

    @Column(name = "root_causes", columnDefinition = "TEXT")
    private String rootCauses;

    @Column(name = "solutions", columnDefinition = "TEXT")
    private String solutions;

    @Column(name = "vector_id", length = 64)
    private String vectorId;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "usage_count")
    private Integer usageCount;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (usageCount == null) {
            usageCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}