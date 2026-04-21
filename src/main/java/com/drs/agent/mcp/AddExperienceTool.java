package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.ExperienceDTO;
import com.drs.agent.model.Experience;
import com.drs.agent.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Add Experience Tool
 *
 * Adds new operational experience to MySQL and Milvus vector database.
 */
@Slf4j
@McpTool(
    name = "add_experience",
    description = "Add new operational experience to the knowledge base for future diagnosis and retrieval. Use this tool to store successful problem resolution patterns.",
    inputParams = "[{\"name\":\"problemType\",\"type\":\"string\",\"required\":true,\"description\":\"The type/category of the problem (e.g., 'database-timeout', 'service-crash')\"},{\"name\":\"keywords\",\"type\":\"string\",\"required\":true,\"description\":\"Keywords for searching this experience, comma-separated\"},{\"name\":\"diagnosisChain\",\"type\":\"string\",\"required\":false,\"description\":\"The diagnosis chain or reasoning process used to identify the problem\"},{\"name\":\"rootCauses\",\"type\":\"string\",\"required\":false,\"description\":\"The root causes identified for this problem\"},{\"name\":\"solutions\",\"type\":\"string\",\"required\":false,\"description\":\"The solutions or steps taken to resolve the problem\"}]",
    outputFormat = "{\"success\":\"boolean\",\"message\":\"string\",\"data\":\"object\"}"
)
@RequiredArgsConstructor
public class AddExperienceTool implements McpToolHandler {

    private final ExperienceService experienceService;

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing add_experience tool with parameters: {}", parameters);

        try {
            // Validate required parameters
            String problemType = (String) parameters.get("problemType");
            String keywords = (String) parameters.get("keywords");

            if (problemType == null || problemType.isBlank()) {
                return ToolResult.failure("problemType is required");
            }
            if (keywords == null || keywords.isBlank()) {
                return ToolResult.failure("keywords is required");
            }

            // Optional parameters
            String diagnosisChain = (String) parameters.get("diagnosisChain");
            String rootCauses = (String) parameters.get("rootCauses");
            String solutions = (String) parameters.get("solutions");

            // Add experience
            Experience experience = experienceService.addExperience(
                    problemType, keywords, diagnosisChain, rootCauses, solutions);

            // Convert to DTO
            ExperienceDTO dto = toDTO(experience);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Experience added successfully");
            result.put("data", dto);

            log.info("Add experience tool executed successfully, ID: {}", experience.getExperienceId());
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing add_experience tool", e);
            return ToolResult.failure("Error adding experience: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object problemType = parameters.get("problemType");
        if (problemType == null) {
            return ValidationResult.failure("Missing required parameter: problemType");
        }
        if (!(problemType instanceof String)) {
            return ValidationResult.failure("Parameter 'problemType' must be a string");
        }
        if (((String) problemType).isBlank()) {
            return ValidationResult.failure("Parameter 'problemType' cannot be blank");
        }

        Object keywords = parameters.get("keywords");
        if (keywords == null) {
            return ValidationResult.failure("Missing required parameter: keywords");
        }
        if (!(keywords instanceof String)) {
            return ValidationResult.failure("Parameter 'keywords' must be a string");
        }
        if (((String) keywords).isBlank()) {
            return ValidationResult.failure("Parameter 'keywords' cannot be blank");
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