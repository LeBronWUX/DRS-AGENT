package com.drs.agent.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Experience DTO
 *
 * Represents an operational experience for knowledge base.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDTO {

    private String experienceId;
    private String problemType;
    private String keywords;
    private String diagnosisChain;
    private String rootCauses;
    private String solutions;
    private Double confidenceScore;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}