package com.drs.agent.controller;

import com.drs.agent.model.Experience;
import com.drs.agent.model.ExperienceRequest;
import com.drs.agent.model.ExperienceResponse;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Experience Controller
 *
 * Handles experience-related API endpoints:
 * - POST /v1/experiences - Create experience
 * - GET /v1/experiences - List experiences
 * - GET /v1/experiences/{id} - Get experience detail
 * - PUT /v1/experiences/{id} - Update experience
 * - DELETE /v1/experiences/{id} - Delete experience
 */
@RestController
@RequestMapping("/v1/experiences")
public class ExperienceController {

    private static final Logger logger = LoggerFactory.getLogger(ExperienceController.class);

    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;

    public ExperienceController(ExperienceRepository experienceRepository,
                                 ObjectMapper objectMapper) {
        this.experienceRepository = experienceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new experience
     *
     * @param request Experience creation request
     * @return Created experience ID and status
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createExperience(@RequestBody ExperienceRequest request) {
        logger.info("Creating experience for problem type: {}", request.getProblemType());

        // Validate request
        if (request.getProblemType() == null || request.getProblemType().trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Problem type is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Create experience entity
        Experience experience = Experience.builder()
                .experienceId(IdGenerator.generateId())
                .problemType(request.getProblemType())
                .keywords(request.getKeywords() != null
                        ? String.join(",", request.getKeywords())
                        : "")
                .diagnosisChain(request.getDiagnosisChain())
                .rootCauses(request.getRootCauses())
                .solutions(request.getSolutions())
                .confidenceScore(request.getConfidenceScore() != null
                        ? request.getConfidenceScore()
                        : 0.0)
                .usageCount(0)
                .metadata(request.getMetadata())
                .build();

        Experience saved = experienceRepository.save(experience);

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("experienceId", saved.getExperienceId());
        response.put("status", "CREATED");

        return ResponseEntity.ok(response);
    }

    /**
     * List experiences with pagination and filtering
     *
     * @param problemType Filter by problem type (optional)
     * @param keywords Filter by keywords (optional)
     * @param page Page number (default 0)
     * @param size Page size (default 10)
     * @return Page of experiences
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listExperiences(
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String keywords,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        logger.info("Listing experiences - problemType: {}, keywords: {}, page: {}, size: {}",
                problemType, keywords, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Experience> experiencePage;

        if (problemType != null && !problemType.trim().isEmpty()) {
            experiencePage = experienceRepository.findByProblemType(problemType, pageable);
        } else if (keywords != null && !keywords.trim().isEmpty()) {
            List<Experience> experiences = experienceRepository.findByKeywordsContaining(keywords);
            experiencePage = new org.springframework.data.domain.PageImpl<>(
                    experiences.stream()
                            .skip(page * size)
                            .limit(size)
                            .collect(Collectors.toList()),
                    pageable,
                    experiences.size()
            );
        } else {
            experiencePage = experienceRepository.findAll(pageable);
        }

        List<ExperienceResponse> experiences = experiencePage.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", experiences);
        response.put("totalElements", experiencePage.getTotalElements());
        response.put("totalPages", experiencePage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get experience by ID
     *
     * @param id Experience ID
     * @return Experience details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExperienceResponse> getExperience(@PathVariable String id) {
        logger.info("Getting experience: {}", id);

        // Try to find by experienceId first, then by numeric id
        Optional<Experience> experience = experienceRepository.findByExperienceId(id);

        if (experience.isEmpty()) {
            try {
                Long numericId = Long.parseLong(id);
                experience = experienceRepository.findById(numericId);
            } catch (NumberFormatException e) {
                // Not a numeric ID
            }
        }

        return experience
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an existing experience
     *
     * @param id Experience ID
     * @param request Update request
     * @return Updated experience
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExperienceResponse> updateExperience(
            @PathVariable String id,
            @RequestBody ExperienceRequest request) {

        logger.info("Updating experience: {}", id);

        Optional<Experience> existingExperience = experienceRepository.findByExperienceId(id);

        if (existingExperience.isEmpty()) {
            try {
                Long numericId = Long.parseLong(id);
                existingExperience = experienceRepository.findById(numericId);
            } catch (NumberFormatException e) {
                // Not a numeric ID
            }
        }

        return existingExperience.map(experience -> {
            // Update fields
            if (request.getProblemType() != null) {
                experience.setProblemType(request.getProblemType());
            }
            if (request.getKeywords() != null) {
                experience.setKeywords(String.join(",", request.getKeywords()));
            }
            if (request.getDiagnosisChain() != null) {
                experience.setDiagnosisChain(request.getDiagnosisChain());
            }
            if (request.getRootCauses() != null) {
                experience.setRootCauses(request.getRootCauses());
            }
            if (request.getSolutions() != null) {
                experience.setSolutions(request.getSolutions());
            }
            if (request.getConfidenceScore() != null) {
                experience.setConfidenceScore(request.getConfidenceScore());
            }
            if (request.getMetadata() != null) {
                experience.setMetadata(request.getMetadata());
            }

            Experience saved = experienceRepository.save(experience);
            return ResponseEntity.ok(convertToResponse(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete an experience
     *
     * @param id Experience ID
     * @return Deletion result
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteExperience(@PathVariable String id) {
        logger.info("Deleting experience: {}", id);

        Optional<Experience> experience = experienceRepository.findByExperienceId(id);

        if (experience.isEmpty()) {
            try {
                Long numericId = Long.parseLong(id);
                experience = experienceRepository.findById(numericId);
            } catch (NumberFormatException e) {
                // Not a numeric ID
            }
        }

        if (experience.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        experienceRepository.delete(experience.get());

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "DELETED");

        return ResponseEntity.ok(response);
    }

    /**
     * Convert Experience entity to Response DTO
     */
    private ExperienceResponse convertToResponse(Experience experience) {
        List<String> keywordsList = null;
        if (experience.getKeywords() != null && !experience.getKeywords().isEmpty()) {
            keywordsList = List.of(experience.getKeywords().split(","));
        }

        return ExperienceResponse.builder()
                .id(String.valueOf(experience.getId()))
                .experienceId(experience.getExperienceId())
                .problemType(experience.getProblemType())
                .keywords(keywordsList)
                .diagnosisChain(experience.getDiagnosisChain())
                .rootCauses(experience.getRootCauses())
                .solutions(experience.getSolutions())
                .confidenceScore(experience.getConfidenceScore())
                .usageCount(experience.getUsageCount())
                .createdAt(experience.getCreatedAt())
                .updatedAt(experience.getUpdatedAt())
                .build();
    }
}