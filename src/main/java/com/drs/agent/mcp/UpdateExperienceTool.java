package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.ExperienceDTO;
import com.drs.agent.model.Experience;
import com.drs.agent.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Update Experience Tool
 *
 * Updates an existing experience in MySQL and Milvus.
 */
@Slf4j
@McpTool(
    name = "update_experience",
    description = "Update an existing operational experience in the knowledge base. Use this tool to improve or correct stored experiences.",
    inputParams = "[{\"name\":\"experienceId\",\"type\":\"string\",\"required\":true,\"description\":\"The unique ID of the experience to update\"},{\"name\":\"updates\",\"type\":\"object\",\"required\":true,\"description\":\"Object containing fields to update (problemType, keywords, diagnosisChain, rootCauses, solutions, confidenceScore)\"}]",
    outputFormat = "{\"success\":\"boolean\",\"message\":\"string\",\"data\":\"object\"}"
)
@RequiredArgsConstructor
public class UpdateExperienceTool implements McpToolHandler {

    private final ExperienceService experienceService;

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing update_experience tool with parameters: {}", parameters);

        try {
            // Validate required parameters
            String experienceId = (String) parameters.get("experienceId");
            Map<String, Object> updates = (Map<String, Object>) parameters.get("updates");

            if (experienceId == null || experienceId.isBlank()) {
                return ToolResult.failure("experienceId is required");
            }
            if (updates == null || updates.isEmpty()) {
                return ToolResult.failure("updates are required and cannot be empty");
            }

            // Validate confidence score if provided
            if (updates.containsKey("confidenceScore")) {
                double score = ((Number) updates.get("confidenceScore")).doubleValue();
                if (score < 0.0 || score > 1.0) {
                    return ToolResult.failure("confidenceScore must be between 0.0 and 1.0");
                }
            }

            // Update experience
            Optional<Experience> updatedExp = experienceService.updateExperience(experienceId, updates);

            if (updatedExp.isEmpty()) {
                return ToolResult.failure("Experience not found with ID: " + experienceId);
            }

            // Convert to DTO
            ExperienceDTO dto = toDTO(updatedExp.get());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Experience updated successfully");
            result.put("data", dto);

            log.info("Update experience tool executed successfully for ID: {}", experienceId);
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing update_experience tool", e);
            return ToolResult.failure("Error updating experience: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object experienceId = parameters.get("experienceId");
        if (experienceId == null) {
            return ValidationResult.failure("Missing required parameter: experienceId");
        }
        if (!(experienceId instanceof String)) {
            return ValidationResult.failure("Parameter 'experienceId' must be a string");
        }
        if (((String) experienceId).isBlank()) {
            return ValidationResult.failure("Parameter 'experienceId' cannot be blank");
        }

        Object updates = parameters.get("updates");
        if (updates == null) {
            return ValidationResult.failure("Missing required parameter: updates");
        }
        if (!(updates instanceof Map)) {
            return ValidationResult.failure("Parameter 'updates' must be an object");
        }
        if (((Map<?, ?>) updates).isEmpty()) {
            return ValidationResult.failure("Parameter 'updates' cannot be empty");
        }

        return ValidationResult.success();
    }

    private ExperienceDTO toDTO(Experience exp) {
        return ExperienceDTO.builder()
                .experienceId(exp.getExperienceId())
                .problemType(exp.getProblemType())
                .keywords(exp.getKeywords())
                .diagnosisChain(exp.getDiagnosisChain())
                .rootCauses(exp.getRootCauses())
                .solutions(exp.getSolutions())
                .confidenceScore(exp.getConfidenceScore())
                .usageCount(exp.getUsageCount())
                .createdAt(exp.getCreatedAt())
                .updatedAt(exp.getUpdatedAt())
                .build();
    }
}