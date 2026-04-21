package com.drs.agent.service;

import com.drs.agent.model.*;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.dto.*;
import com.drs.agent.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Diagnosis Service
 *
 * Implements the diagnosis workflow:
 * 1. Intent Recognition - Identify problem type using IntentRecognizer
 * 2. Experience Retrieval - Find similar experiences using ExperienceRetriever
 * 3. Diagnosis Orchestration - Execute diagnosis chain using DiagnosisOrchestrator
 * 4. Root Cause Analysis - Analyze root cause using RootCauseAnalyzer
 * 5. Session Persistence - Save to database
 *
 * Sprint 3 Update: Integrated IntentRecognizer and DiagnosisOrchestrator modules.
 */
@Service
public class DiagnosisService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisService.class);

    // Core modules (Sprint 3)
    private final IntentRecognizer intentRecognizer;
    private final DiagnosisOrchestrator orchestrator;
    private final ExperienceRetriever experienceRetriever;
    private final RootCauseAnalyzer rootCauseAnalyzer;

    // Legacy services
    private final DRSIntelligentAgentService agentService;
    private final DiagnosisSessionRepository sessionRepository;
    private final ExperienceRepository experienceRepository;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;

    public DiagnosisService(IntentRecognizer intentRecognizer,
                            DiagnosisOrchestrator orchestrator,
                            ExperienceRetriever experienceRetriever,
                            RootCauseAnalyzer rootCauseAnalyzer,
                            DRSIntelligentAgentService agentService,
                            DiagnosisSessionRepository sessionRepository,
                            ExperienceRepository experienceRepository,
                            VectorStoreService vectorStoreService,
                            ObjectMapper objectMapper) {
        this.intentRecognizer = intentRecognizer;
        this.orchestrator = orchestrator;
        this.experienceRetriever = experienceRetriever;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
        this.agentService = agentService;
        this.sessionRepository = sessionRepository;
        this.experienceRepository = experienceRepository;
        this.vectorStoreService = vectorStoreService;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute full diagnosis process using new Sprint 3 modules
     *
     * @param request Diagnosis request
     * @return Diagnosis response
     */
    @Transactional
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        logger.info("Starting diagnosis for problem: {}", request.getProblem());

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = IdGenerator.generateSessionId();
        }

        // Create diagnosis session
        DiagnosisSession session = DiagnosisSession.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .problem(request.getProblem())
                .context(request.getContext())
                .priority(request.getPriority())
                .status("IN_PROGRESS")
                .build();
        sessionRepository.save(session);

        try {
            // Step 1: Intent Recognition using IntentRecognizer
            logger.info("Step 1: Intent Recognition");
            com.drs.agent.service.dto.IntentResult intentResult = intentRecognizer.recognize(request.getProblem());
            String problemType = intentResult.getProblemType();
            logger.info("Problem classified as: {} with confidence {}", problemType, intentResult.getConfidence());

            // Step 2: Experience Retrieval using ExperienceRetriever
            logger.info("Step 2: Experience Retrieval");
            com.drs.agent.model.IntentResult modelIntent = intentResult.toModelIntentResult();
            RetrievalResult retrievalResult = experienceRetriever.search(modelIntent);
            List<Experience> similarExperiences = retrievalResult.getMatches().stream()
                    .map(ExperienceMatch::getExperience)
                    .collect(Collectors.toList());
            logger.info("Found {} similar experiences, max similarity: {}",
                    similarExperiences.size(), retrievalResult.getMaxSimilarity());

            // Step 3: Diagnosis Chain Execution using DiagnosisOrchestrator
            logger.info("Step 3: Diagnosis Chain Execution");
            DiagnosisChainResult chainResult = orchestrator.executeChain(intentResult);
            logger.info("Diagnosis chain completed: {} successful, {} failed steps, {}ms",
                    chainResult.getSuccessfulSteps(), chainResult.getFailedSteps(), chainResult.getTotalExecutionTime());

            // Step 4: Root Cause Analysis using RootCauseAnalyzer
            logger.info("Step 4: Root Cause Analysis");
            RootCauseResult rootCauseResult = rootCauseAnalyzer.analyze(chainResult, similarExperiences, intentResult);
            logger.info("Root cause identified: category={}, confidence={}",
                    rootCauseResult.getCategory(), rootCauseResult.getConfidence());

            // Step 5: Update session with results
            session.setProblemType(problemType);
            session.setRootCause(rootCauseResult.getDescription());
            session.setConfidenceScore(rootCauseResult.getConfidence());
            session.setSolution(buildSolutionString(rootCauseResult));
            session.setDiagnosisChain(convertChainResultToJson(chainResult));
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);

            // Build response
            List<DiagnosisResponse.DiagnosisStep> diagnosisSteps = convertChainResultToSteps(chainResult);
            List<DiagnosisResponse.ExperienceMatch> experienceMatches = convertExperiencesToMatches(retrievalResult);

            return DiagnosisResponse.builder()
                    .sessionId(sessionId)
                    .problemType(problemType)
                    .rootCause(rootCauseResult.getDescription())
                    .confidence(rootCauseResult.getConfidence())
                    .solution(buildSolutionString(rootCauseResult))
                    .diagnosisChain(diagnosisSteps)
                    .similarExperiences(experienceMatches)
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            logger.error("Diagnosis failed", e);
            session.setStatus("FAILED");
            session.setRootCause("Diagnosis failed: " + e.getMessage());
            sessionRepository.save(session);

            return DiagnosisResponse.builder()
                    .sessionId(sessionId)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Execute diagnosis using legacy method (for backwards compatibility)
     *
     * @param request Diagnosis request
     * @return Diagnosis response
     */
    @Transactional
    public DiagnosisResponse diagnoseLegacy(DiagnosisRequest request) {
        logger.info("Starting legacy diagnosis for problem: {}", request.getProblem());

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = IdGenerator.generateSessionId();
        }

        // Create diagnosis session
        DiagnosisSession session = DiagnosisSession.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .problem(request.getProblem())
                .context(request.getContext())
                .priority(request.getPriority())
                .status("IN_PROGRESS")
                .build();
        sessionRepository.save(session);

        try {
            // Step 1: Intent Recognition - Identify problem type
            String classificationResult = agentService.classifyProblem(request.getProblem());
            String problemType = parseProblemType(classificationResult);
            logger.info("Problem classified as: {}", problemType);

            // Step 2: Experience Retrieval - Find similar experiences
            List<DiagnosisResponse.ExperienceMatch> similarExperiences =
                    retrieveSimilarExperiences(request.getProblem(), problemType);
            logger.info("Found {} similar experiences", similarExperiences.size());

            // Step 3: Diagnosis Orchestration - Execute diagnosis chain
            String orchestrationResult = agentService.orchestrateDiagnosis(
                    problemType,
                    request.getContext() != null ? request.getContext() : "{}",
                    request.getProblem()
            );
            List<DiagnosisResponse.DiagnosisStep> diagnosisChain =
                    parseDiagnosisChain(orchestrationResult);
            logger.info("Diagnosis chain has {} steps", diagnosisChain.size());

            // Step 4: Root Cause Analysis
            String similarExperiencesJson = convertExperiencesToJson(similarExperiences);
            String diagnosisResultsJson = convertDiagnosisChainToJson(diagnosisChain);
            String rootCauseResult = agentService.analyzeRootCause(
                    problemType,
                    request.getContext() != null ? request.getContext() : "{}",
                    request.getProblem(),
                    diagnosisResultsJson,
                    similarExperiencesJson
            );
            RootCauseAnalysis analysis = parseRootCauseAnalysis(rootCauseResult);
            logger.info("Root cause analysis completed with confidence: {}", analysis.confidence);

            // Step 5: Update session with results
            session.setProblemType(problemType);
            session.setRootCause(analysis.rootCause);
            session.setConfidenceScore(analysis.confidence);
            session.setSolution(analysis.solution);
            session.setDiagnosisChain(diagnosisResultsJson);
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);

            // Build response
            return DiagnosisResponse.builder()
                    .sessionId(sessionId)
                    .problemType(problemType)
                    .rootCause(analysis.rootCause)
                    .confidence(analysis.confidence)
                    .solution(analysis.solution)
                    .diagnosisChain(diagnosisChain)
                    .similarExperiences(similarExperiences)
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            logger.error("Diagnosis failed", e);
            session.setStatus("FAILED");
            session.setRootCause("Diagnosis failed: " + e.getMessage());
            sessionRepository.save(session);

            return DiagnosisResponse.builder()
                    .sessionId(sessionId)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Build solution string from RootCauseResult
     */
    private String buildSolutionString(RootCauseResult result) {
        if (result.getSolution() != null) {
            Solution solution = result.getSolution();
            StringBuilder sb = new StringBuilder();
            if (solution.getImmediateAction() != null) {
                sb.append("Immediate Action: ").append(solution.getImmediateAction()).append("\n");
            }
            if (solution.getLongTermFix() != null) {
                sb.append("Long Term Fix: ").append(solution.getLongTermFix());
            }
            return sb.toString();
        }
        return result.getDescription();
    }

    /**
     * Convert DiagnosisChainResult to JSON string
     */
    private String convertChainResultToJson(DiagnosisChainResult chainResult) {
        try {
            return objectMapper.writeValueAsString(chainResult.getStepResults());
        } catch (JsonProcessingException e) {
            logger.warn("Failed to convert chain result to JSON", e);
            return "[]";
        }
    }

    /**
     * Convert DiagnosisChainResult to response steps
     */
    private List<DiagnosisResponse.DiagnosisStep> convertChainResultToSteps(DiagnosisChainResult chainResult) {
        List<DiagnosisResponse.DiagnosisStep> steps = new ArrayList<>();
        for (StepResult stepResult : chainResult.getStepResults()) {
            steps.add(DiagnosisResponse.DiagnosisStep.builder()
                    .stepId("step_" + stepResult.getStepOrder())
                    .stepName(stepResult.getStepName())
                    .description(stepResult.getAction())
                    .result(stepResult.getRawOutput())
                    .status(stepResult.isSuccess() ? "COMPLETED" : "FAILED")
                    .executionTimeMs(stepResult.getExecutionTime())
                    .build());
        }
        return steps;
    }

    /**
     * Convert RetrievalResult matches to response matches
     */
    private List<DiagnosisResponse.ExperienceMatch> convertExperiencesToMatches(RetrievalResult retrievalResult) {
        List<DiagnosisResponse.ExperienceMatch> matches = new ArrayList<>();
        for (ExperienceMatch match : retrievalResult.getMatches()) {
            Experience exp = match.getExperience();
            matches.add(DiagnosisResponse.ExperienceMatch.builder()
                    .experienceId(exp.getExperienceId())
                    .problemType(exp.getProblemType())
                    .similarity(match.getSimilarity())
                    .summary(exp.getKeywords())
                    .build());
        }
        return matches;
    }

    /**
     * Get diagnosis result by session ID
     *
     * @param sessionId Session ID
     * @return Diagnosis response
     */
    public Optional<DiagnosisResponse> getDiagnosisResult(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .map(this::convertToResponse);
    }

    /**
     * Submit feedback for a diagnosis
     *
     * @param sessionId Session ID
     * @param feedback Feedback request
     * @return Updated diagnosis response
     */
    @Transactional
    public Optional<DiagnosisResponse> submitFeedback(String sessionId, FeedbackRequest feedback) {
        return sessionRepository.findBySessionId(sessionId).map(session -> {
            session.setFeedbackRating(feedback.getRating());
            session.setFeedbackComment(feedback.getComment());
            session.setIsCorrect(feedback.getIsCorrect());

            if (feedback.getActualRootCause() != null) {
                session.setActualRootCause(feedback.getActualRootCause());
            }
            if (feedback.getActualSolution() != null) {
                session.setActualSolution(feedback.getActualSolution());
            }

            sessionRepository.save(session);

            // Generate new experience from corrected diagnosis
            if (Boolean.FALSE.equals(feedback.getIsCorrect()) &&
                feedback.getActualRootCause() != null) {
                generateExperienceFromFeedback(session, feedback);
            }

            return convertToResponse(session);
        });
    }

    /**
     * Get diagnosis history with pagination
     *
     * @param userId User ID (optional)
     * @param problemType Problem type (optional)
     * @param startDate Start date (optional)
     * @param endDate End date (optional)
     * @param page Page number
     * @param size Page size
     * @return Page of diagnosis responses
     */
    public Page<DiagnosisResponse> getDiagnosisHistory(String userId, String problemType,
                                                        LocalDateTime startDate, LocalDateTime endDate,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<DiagnosisSession> sessions;

        if (userId != null && problemType != null) {
            sessions = sessionRepository.findAll(
                    (root, query, cb) -> cb.and(
                            cb.equal(root.get("userId"), userId),
                            cb.equal(root.get("problemType"), problemType)
                    ), pageable);
        } else if (userId != null) {
            sessions = sessionRepository.findByUserId(userId, pageable);
        } else if (problemType != null) {
            sessions = sessionRepository.findByProblemType(problemType, pageable);
        } else {
            sessions = sessionRepository.findAll(pageable);
        }

        return sessions.map(this::convertToResponse);
    }

    /**
     * Get diagnosis statistics
     *
     * @return Statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalDiagnoses", sessionRepository.count());
        stats.put("averageConfidence", sessionRepository.getAverageConfidenceScore());
        stats.put("correctDiagnoses", sessionRepository.countCorrectDiagnoses());

        // Problem type distribution
        List<Object[]> problemTypeCounts = sessionRepository.countByProblemType();
        Map<String, Long> problemTypeDistribution = new HashMap<>();
        for (Object[] row : problemTypeCounts) {
            problemTypeDistribution.put((String) row[0], (Long) row[1]);
        }
        stats.put("problemTypeDistribution", problemTypeDistribution);

        // Recent count (last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        stats.put("recentDiagnoses", sessionRepository.countAfterDate(weekAgo));

        return stats;
    }

    /**
     * Retrieve similar experiences from knowledge base
     */
    private List<DiagnosisResponse.ExperienceMatch> retrieveSimilarExperiences(String problem, String problemType) {
        List<DiagnosisResponse.ExperienceMatch> matches = new ArrayList<>();

        // First, try to find by problem type
        List<Experience> experiences = experienceRepository.findByProblemType(problemType);

        for (Experience exp : experiences) {
            matches.add(DiagnosisResponse.ExperienceMatch.builder()
                    .experienceId(exp.getExperienceId())
                    .problemType(exp.getProblemType())
                    .similarity(exp.getConfidenceScore())
                    .summary(exp.getKeywords())
                    .build());
        }

        // Limit to top 5 matches
        return matches.stream()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Parse problem type from classification result
     */
    private String parseProblemType(String classificationResult) {
        try {
            Map<String, Object> result = objectMapper.readValue(classificationResult,
                    new TypeReference<Map<String, Object>>() {});

            if (result.containsKey("problemType")) {
                return (String) result.get("problemType");
            }
            if (result.containsKey("problem_type")) {
                return (String) result.get("problem_type");
            }
            if (result.containsKey("type")) {
                return (String) result.get("type");
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse classification result, using default type", e);
        }

        // Default problem type
        return "UNKNOWN";
    }

    /**
     * Parse diagnosis chain from orchestration result
     */
    @SuppressWarnings("unchecked")
    private List<DiagnosisResponse.DiagnosisStep> parseDiagnosisChain(String orchestrationResult) {
        List<DiagnosisResponse.DiagnosisStep> steps = new ArrayList<>();

        try {
            Map<String, Object> result = objectMapper.readValue(orchestrationResult,
                    new TypeReference<Map<String, Object>>() {});

            if (result.containsKey("steps")) {
                List<Map<String, Object>> stepsList = (List<Map<String, Object>>) result.get("steps");
                int index = 0;
                for (Map<String, Object> step : stepsList) {
                    steps.add(DiagnosisResponse.DiagnosisStep.builder()
                            .stepId("step_" + index++)
                            .stepName((String) step.getOrDefault("name", "Unknown"))
                            .description((String) step.getOrDefault("description", ""))
                            .result((String) step.getOrDefault("result", ""))
                            .status((String) step.getOrDefault("status", "PENDING"))
                            .executionTimeMs(((Number) step.getOrDefault("executionTimeMs", 0)).longValue())
                            .build());
                }
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse diagnosis chain", e);
        }

        if (steps.isEmpty()) {
            steps.add(DiagnosisResponse.DiagnosisStep.builder()
                    .stepId("step_0")
                    .stepName("Default Analysis")
                    .description("Basic problem analysis")
                    .result(orchestrationResult)
                    .status("COMPLETED")
                    .build());
        }

        return steps;
    }

    /**
     * Parse root cause analysis result
     */
    @SuppressWarnings("unchecked")
    private RootCauseAnalysis parseRootCauseAnalysis(String rootCauseResult) {
        RootCauseAnalysis analysis = new RootCauseAnalysis();

        try {
            Map<String, Object> result = objectMapper.readValue(rootCauseResult,
                    new TypeReference<Map<String, Object>>() {});

            analysis.rootCause = (String) result.getOrDefault("rootCause",
                    result.getOrDefault("root_cause", "Unknown root cause"));
            analysis.solution = (String) result.getOrDefault("solution",
                    result.getOrDefault("recommended_solution", "No solution available"));

            Object confidence = result.getOrDefault("confidence",
                    result.getOrDefault("confidenceScore", 0.5));
            if (confidence instanceof Number) {
                analysis.confidence = ((Number) confidence).doubleValue();
            } else {
                analysis.confidence = 0.5;
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse root cause analysis", e);
            analysis.rootCause = rootCauseResult;
            analysis.solution = "Unable to determine solution";
            analysis.confidence = 0.5;
        }

        return analysis;
    }

    /**
     * Convert experiences to JSON string
     */
    private String convertExperiencesToJson(List<DiagnosisResponse.ExperienceMatch> experiences) {
        try {
            return objectMapper.writeValueAsString(experiences);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Convert diagnosis chain to JSON string
     */
    private String convertDiagnosisChainToJson(List<DiagnosisResponse.DiagnosisStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Convert session entity to response DTO
     */
    private DiagnosisResponse convertToResponse(DiagnosisSession session) {
        List<DiagnosisResponse.DiagnosisStep> diagnosisChain = new ArrayList<>();
        if (session.getDiagnosisChain() != null) {
            try {
                diagnosisChain = objectMapper.readValue(session.getDiagnosisChain(),
                        new TypeReference<List<DiagnosisResponse.DiagnosisStep>>() {});
            } catch (JsonProcessingException e) {
                logger.warn("Failed to parse diagnosis chain for session: {}", session.getSessionId());
            }
        }

        return DiagnosisResponse.builder()
                .sessionId(session.getSessionId())
                .problemType(session.getProblemType())
                .rootCause(session.getRootCause())
                .confidence(session.getConfidenceScore())
                .solution(session.getSolution())
                .diagnosisChain(diagnosisChain)
                .status(session.getStatus())
                .build();
    }

    /**
     * Generate new experience from feedback
     */
    private void generateExperienceFromFeedback(DiagnosisSession session, FeedbackRequest feedback) {
        String experienceResult = agentService.generateExperience(
                session.getProblemType(),
                session.getProblem(),
                session.getDiagnosisChain(),
                feedback.getActualRootCause(),
                feedback.getActualSolution(),
                "User corrected diagnosis"
        );

        logger.info("Generated new experience from feedback for session: {}", session.getSessionId());
        // The experience generation would be saved by the ExperienceService or agent
    }

    /**
     * Internal class for root cause analysis
     */
    private static class RootCauseAnalysis {
        String rootCause;
        String solution;
        Double confidence;
    }
}