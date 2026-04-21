package com.drs.agent.service;

import com.drs.agent.model.Experience;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.util.IdGenerator;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Experience Service
 *
 * Handles experience storage and retrieval with MySQL and Milvus vector database.
 * Milvus client is optional - if not available, only MySQL operations will be performed.
 */
@Slf4j
@Service
public class ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final ClaudeService claudeService;
    private final MilvusServiceClient milvusServiceClient;

    @Value("${milvus.experience-collection:experience_vectors}")
    private String collectionName;

    @Value("${milvus.experience-vector-dimension:1536}")
    private int vectorDimension;

    @Value("${milvus.enabled:false}")
    private boolean milvusEnabled;

    @Autowired
    public ExperienceService(ExperienceRepository experienceRepository,
                              ClaudeService claudeService,
                              @Autowired(required = false) MilvusServiceClient milvusServiceClient) {
        this.experienceRepository = experienceRepository;
        this.claudeService = claudeService;
        this.milvusServiceClient = milvusServiceClient;
        log.info("ExperienceService initialized, Milvus enabled: {}", milvusServiceClient != null);
    }

    private static final String EMBEDDING_PROMPT_TEMPLATE = """
        Generate a semantic embedding for the following operational experience text.
        The text describes a problem type, keywords, diagnosis chain, and root causes.
        Focus on the technical aspects and operational patterns.

        Text: %s

        Return only a JSON array of %d floating point numbers representing the embedding vector.
        """;

    /**
     * Generate embedding vector using Claude API.
     * TODO: Replace with proper embedding model (e.g., OpenAI embeddings, local model).
     */
    public List<Float> generateEmbedding(String text) {
        try {
            // For mock purposes, generate a deterministic pseudo-embedding
            // In production, this should call a proper embedding model
            log.info("Generating embedding for text (length: {})", text.length());

            // Generate a simple hash-based pseudo embedding for testing
            List<Float> embedding = new ArrayList<>();
            Random random = new Random(text.hashCode());

            for (int i = 0; i < vectorDimension; i++) {
                embedding.add(random.nextFloat() * 2 - 1); // Values between -1 and 1
            }

            // Normalize the vector
            double norm = Math.sqrt(embedding.stream()
                    .mapToDouble(f -> f * f)
                    .sum());
            for (int i = 0; i < embedding.size(); i++) {
                embedding.set(i, (float) (embedding.get(i) / norm));
            }

            log.debug("Generated embedding with {} dimensions", embedding.size());
            return embedding;

        } catch (Exception e) {
            log.error("Error generating embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Add a new experience to MySQL and Milvus.
     */
    @Transactional
    public Experience addExperience(String problemType, String keywords, String diagnosisChain,
                                     String rootCauses, String solutions) {
        log.info("Adding new experience: problemType={}, keywords={}", problemType, keywords);

        // Generate experience ID
        String experienceId = "exp_" + IdGenerator.generateId();

        // Generate embedding from combined text
        String combinedText = String.join(" | ",
                problemType, keywords,
                diagnosisChain != null ? diagnosisChain : "",
                rootCauses != null ? rootCauses : "",
                solutions != null ? solutions : "");

        List<Float> embedding = generateEmbedding(combinedText);

        // Save to Milvus
        String vectorId = saveToMilvus(experienceId, embedding);

        // Save to MySQL
        Experience experience = Experience.builder()
                .experienceId(experienceId)
                .problemType(problemType)
                .keywords(keywords)
                .diagnosisChain(diagnosisChain)
                .rootCauses(rootCauses)
                .solutions(solutions)
                .vectorId(vectorId)
                .confidenceScore(1.0)
                .usageCount(0)
                .build();

        return experienceRepository.save(experience);
    }

    /**
     * Save embedding to Milvus.
     */
    private String saveToMilvus(String experienceId, List<Float> embedding) {
        if (milvusServiceClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, skipping vector storage for experience: {}", experienceId);
            return "local-" + UUID.randomUUID().toString();
        }

        try {
            String vectorId = UUID.randomUUID().toString();

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", Collections.singletonList(vectorId)));
            fields.add(new InsertParam.Field("experience_id", Collections.singletonList(experienceId)));
            fields.add(new InsertParam.Field("embedding", Collections.singletonList(embedding)));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            var response = milvusServiceClient.insert(insertParam);

            if (response.getStatus() != 0) {
                log.warn("Milvus insert returned non-zero status: {}", response.getStatus());
            } else {
                log.info("Successfully saved embedding to Milvus, vectorId: {}", vectorId);
            }

            return vectorId;

        } catch (Exception e) {
            log.error("Error saving to Milvus: {}", e.getMessage(), e);
            // Continue without Milvus - return a placeholder vector ID
            return "local-" + UUID.randomUUID().toString();
        }
    }

    /**
     * Search for similar experiences using vector similarity.
     */
    public List<Experience> searchSimilarExperiences(String problemDescription, int topK) {
        log.info("Searching for similar experiences: topK={}", topK);

        try {
            // Generate embedding for the problem description
            List<Float> queryEmbedding = generateEmbedding(problemDescription);

            // Search in Milvus
            List<String> experienceIds = searchInMilvus(queryEmbedding, topK);

            // Retrieve experiences from MySQL
            List<Experience> experiences = new ArrayList<>();
            for (String expId : experienceIds) {
                experienceRepository.findByExperienceId(expId)
                        .ifPresent(experiences::add);
            }

            // Update usage count
            experiences.forEach(exp -> {
                exp.setUsageCount(exp.getUsageCount() + 1);
                experienceRepository.save(exp);
            });

            return experiences;

        } catch (Exception e) {
            log.error("Error searching similar experiences: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Search in Milvus for similar vectors.
     */
    private List<String> searchInMilvus(List<Float> embedding, int topK) {
        if (milvusServiceClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, skipping vector search");
            return Collections.emptyList();
        }

        try {
            List<List<Float>> vectors = Collections.singletonList(embedding);
            List<String> outFields = Arrays.asList("experience_id");

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(topK)
                    .withVectors(vectors)
                    .withVectorFieldName("embedding")
                    .withOutFields(outFields)
                    .build();

            var response = milvusServiceClient.search(searchParam);

            if (response.getStatus() != 0) {
                log.warn("Milvus search returned non-zero status: {}", response.getStatus());
                return Collections.emptyList();
            }

            List<String> experienceIds = new ArrayList<>();
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            for (var result : wrapper.getRowRecords(0)) {
                Object expId = result.get("experience_id");
                if (expId != null) {
                    experienceIds.add(expId.toString());
                }
            }

            log.info("Found {} similar experiences in Milvus", experienceIds.size());
            return experienceIds;

        } catch (Exception e) {
            log.error("Error searching in Milvus: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Update an existing experience.
     */
    @Transactional
    public Optional<Experience> updateExperience(String experienceId, Map<String, Object> updates) {
        log.info("Updating experience: {}", experienceId);

        return experienceRepository.findByExperienceId(experienceId).map(exp -> {
            // Update fields
            if (updates.containsKey("problemType")) {
                exp.setProblemType((String) updates.get("problemType"));
            }
            if (updates.containsKey("keywords")) {
                exp.setKeywords((String) updates.get("keywords"));
            }
            if (updates.containsKey("diagnosisChain")) {
                exp.setDiagnosisChain((String) updates.get("diagnosisChain"));
            }
            if (updates.containsKey("rootCauses")) {
                exp.setRootCauses((String) updates.get("rootCauses"));
            }
            if (updates.containsKey("solutions")) {
                exp.setSolutions((String) updates.get("solutions"));
            }
            if (updates.containsKey("confidenceScore")) {
                exp.setConfidenceScore(((Number) updates.get("confidenceScore")).doubleValue());
            }

            // Regenerate embedding and update Milvus
            String combinedText = String.join(" | ",
                    exp.getProblemType(), exp.getKeywords(),
                    exp.getDiagnosisChain() != null ? exp.getDiagnosisChain() : "",
                    exp.getRootCauses() != null ? exp.getRootCauses() : "",
                    exp.getSolutions() != null ? exp.getSolutions() : "");

            List<Float> newEmbedding = generateEmbedding(combinedText);
            String newVectorId = saveToMilvus(exp.getExperienceId(), newEmbedding);

            // Update vector ID if changed
            if (!newVectorId.equals(exp.getVectorId())) {
                exp.setVectorId(newVectorId);
            }

            return experienceRepository.save(exp);
        });
    }

    /**
     * Get experience by ID.
     */
    public Optional<Experience> getExperience(String experienceId) {
        return experienceRepository.findByExperienceId(experienceId);
    }

    /**
     * Delete experience by ID.
     */
    @Transactional
    public boolean deleteExperience(String experienceId) {
        log.info("Deleting experience: {}", experienceId);
        Optional<Experience> exp = experienceRepository.findByExperienceId(experienceId);
        if (exp.isPresent()) {
            experienceRepository.deleteByExperienceId(experienceId);
            // TODO: Also delete from Milvus
            return true;
        }
        return false;
    }
}