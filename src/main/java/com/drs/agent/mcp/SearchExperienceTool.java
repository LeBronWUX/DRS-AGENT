package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.ExperienceDTO;
import com.drs.agent.model.Experience;
import com.drs.agent.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Search Experience Tool
 *
 * Searches for similar experiences using vector similarity in Milvus.
 */
@Slf4j
@McpTool(
    name = "search_experience",
    description = "Search for similar operational experiences using vector similarity for diagnosis reference. Use this tool to find historical solutions to similar problems.",
    inputParams = "[{\"name\":\"problemDescription\",\"type\":\"string\",\"required\":true,\"description\":\"Description of the problem to search for similar experiences\"},{\"name\":\"topK\",\"type\":\"number\",\"required\":false,\"description\":\"Number of top similar experiences to return\",\"defaultValue\":5}]",
    outputFormat = "{\"success\":\"boolean\",\"count\":\"number\",\"query\":\"string\",\"data\":\"array\"}"
)
@RequiredArgsConstructor
public class SearchExperienceTool implements McpToolHandler {

    private final ExperienceService experienceService;

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing search_experience tool with parameters: {}", parameters);

        try {
            // Validate required parameters
            String problemDescription = (String) parameters.get("problemDescription");

            if (problemDescription == null || problemDescription.isBlank()) {
                return ToolResult.failure("problemDescription is required");
            }

            // Get topK parameter with default
            int topK = parameters.containsKey("topK")
                    ? ((Number) parameters.get("topK")).intValue()
                    : 5;

            // Ensure topK is within reasonable bounds
            topK = Math.max(1, Math.min(topK, 20));

            // Search for similar experiences
            List<Experience> experiences = experienceService.searchSimilarExperiences(problemDescription, topK);

            // Convert to DTOs
            List<ExperienceDTO> dtos = experiences.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", dtos.size());
            result.put("query", problemDescription);
            result.put("data", dtos);

            log.info("Search experience tool executed successfully, found {} experiences", dtos.size());
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing search_experience tool", e);
            return ToolResult.failure("Error searching experiences: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object problemDescription = parameters.get("problemDescription");
        if (problemDescription == null) {
            return ValidationResult.failure("Missing required parameter: problemDescription");
        }
        if (!(problemDescription instanceof String)) {
            return ValidationResult.failure("Parameter 'problemDescription' must be a string");
        }
        if (((String) problemDescription).isBlank()) {
            return ValidationResult.failure("Parameter 'problemDescription' cannot be blank");
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