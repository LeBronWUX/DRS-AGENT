package com.drs.agent.service;

import com.drs.agent.model.Experience;
import com.drs.agent.repository.ExperienceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Experience Initializer Service
 *
 * Automatically loads preset experience templates from JSON files into the database
 * and Milvus vector database on application startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceInitializer {

    private final ExperienceService experienceService;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;

    @Value("${drs.experience.init-on-startup:true}")
    private boolean initOnStartup;

    @Value("${drs.experience.overwrite-existing:false}")
    private boolean overwriteExisting;

    private static final String EXPERIENCE_FILES_PATTERN = "classpath:experiences/*.json";

    /**
     * Initialize preset experiences on application startup.
     */
    @PostConstruct
    public void initializeExperiences() {
        if (!initOnStartup) {
            log.info("Experience initialization on startup is disabled");
            return;
        }

        log.info("Starting to initialize preset experiences...");

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(EXPERIENCE_FILES_PATTERN);

            log.info("Found {} experience files to process", resources.length);

            int successCount = 0;
            int skipCount = 0;
            int errorCount = 0;

            for (Resource resource : resources) {
                try {
                    boolean result = loadExperienceFromFile(resource);
                    if (result) {
                        successCount++;
                    } else {
                        skipCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to load experience from {}: {}",
                            resource.getFilename(), e.getMessage(), e);
                }
            }

            log.info("Experience initialization completed: {} loaded, {} skipped, {} errors",
                    successCount, skipCount, errorCount);

        } catch (IOException e) {
            log.error("Failed to resolve experience files: {}", e.getMessage(), e);
        }
    }

    /**
     * Load a single experience from a JSON file.
     *
     * @param resource The resource pointing to the JSON file
     * @return true if the experience was loaded, false if it was skipped
     */
    private boolean loadExperienceFromFile(Resource resource) throws IOException {
        String filename = resource.getFilename();
        log.info("Processing experience file: {}", filename);

        try (InputStream is = resource.getInputStream()) {
            JsonNode json = objectMapper.readTree(is);

            String experienceId = getTextField(json, "id");
            if (experienceId == null || experienceId.isEmpty()) {
                log.warn("Experience file {} missing 'id' field, skipping", filename);
                return false;
            }

            // Check if experience already exists
            Optional<Experience> existing = experienceRepository.findByExperienceId(experienceId);
            if (existing.isPresent()) {
                if (overwriteExisting) {
                    log.info("Experience {} already exists, overwriting", experienceId);
                    experienceRepository.deleteByExperienceId(experienceId);
                } else {
                    log.debug("Experience {} already exists, skipping", experienceId);
                    return false;
                }
            }

            // Extract fields from JSON
            String problemType = getTextField(json, "problem_type");
            JsonNode keywordsNode = json.get("keywords");
            String keywords = keywordsNode != null ? keywordsNode.toString() : "[]";
            JsonNode diagnosisChainNode = json.get("diagnosis_chain");
            String diagnosisChain = diagnosisChainNode != null ? diagnosisChainNode.toString() : "[]";
            JsonNode rootCausesNode = json.get("root_causes");
            String rootCauses = rootCausesNode != null ? rootCausesNode.toString() : "[]";
            JsonNode expertTipsNode = json.get("expert_tips");
            String solutions = expertTipsNode != null ? expertTipsNode.toString() : "[]";

            // Validate required fields
            if (problemType == null || problemType.isEmpty()) {
                log.warn("Experience file {} missing 'problem_type' field, skipping", filename);
                return false;
            }

            // Create experience using the service (which handles MySQL and Milvus)
            Experience experience = experienceService.addExperience(
                    problemType,
                    keywords,
                    diagnosisChain,
                    rootCauses,
                    solutions
            );

            log.info("Successfully loaded experience: {} -> {}", experienceId, experience.getId());
            return true;

        } catch (Exception e) {
            log.error("Error processing experience file {}: {}", filename, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Safely get a text field from a JSON node.
     */
    private String getTextField(JsonNode json, String fieldName) {
        JsonNode field = json.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    /**
     * Manually trigger experience reload.
     * Can be called via API or admin interface.
     */
    public void reloadExperiences() {
        log.info("Manually triggering experience reload...");
        initializeExperiences();
    }

    /**
     * Check initialization status.
     */
    public InitializationStatus getInitializationStatus() {
        long totalCount = experienceRepository.count();
        return new InitializationStatus(
                initOnStartup,
                overwriteExisting,
                totalCount,
                LocalDateTime.now()
        );
    }

    /**
     * Initialization status DTO.
     */
    public record InitializationStatus(
            boolean initOnStartup,
            boolean overwriteExisting,
            long totalExperiences,
            LocalDateTime lastChecked
    ) {}
}