package com.drs.agent.service;

import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.model.DiagnosisSession;
import com.drs.agent.model.Experience;
import com.drs.agent.model.ExperienceRequest;
import com.drs.agent.model.FeedbackRequest;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.dto.*;
import com.drs.agent.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ExperienceLearningService - 快速学习模块
 *
 * 支持新经验录入、人工确认、反馈学习等功能：
 * 1. 录入新经验到MySQL和Milvus
 * 2. Claude辅助生成经验模板
 * 3. 低置信度触发人工确认
 * 4. 用户确认后录入经验
 * 5. 反馈评分更新经验权重
 */
@Service
public class ExperienceLearningService {

    private static final Logger logger = LoggerFactory.getLogger(ExperienceLearningService.class);

    private final ExperienceService experienceService;
    private final ClaudeService claudeService;
    private final PromptTemplateService promptTemplateService;
    private final DiagnosisSessionRepository sessionRepository;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;

    // Confirmation task expiration time (in hours)
    private static final int CONFIRMATION_EXPIRATION_HOURS = 24;

    // Minimum rating to boost experience score
    private static final int MIN_BOOST_RATING = 4;

    // Maximum rating penalty for low scores
    private static final int MAX_PENALTY_RATING = 2;

    // Confidence threshold for triggering manual confirmation
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.7;

    // Pending confirmations cache (in-memory for fast access)
    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    public ExperienceLearningService(ExperienceService experienceService,
                                      ClaudeService claudeService,
                                      PromptTemplateService promptTemplateService,
                                      DiagnosisSessionRepository sessionRepository,
                                      ExperienceRepository experienceRepository,
                                      ObjectMapper objectMapper) {
        this.experienceService = experienceService;
        this.claudeService = claudeService;
        this.promptTemplateService = promptTemplateService;
        this.sessionRepository = sessionRepository;
        this.experienceRepository = experienceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 录入新经验
     *
     * @param request 经验录入请求
     * @return 经验ID
     */
    @Transactional
    public String addExperience(ExperienceRequest request) {
        logger.info("Adding new experience: problemType={}", request.getProblemType());

        // Build keywords string
        String keywords = request.getKeywords() != null ?
                String.join(",", request.getKeywords()) : "";

        // Add experience to MySQL and Milvus
        Experience experience = experienceService.addExperience(
                request.getProblemType(),
                keywords,
                request.getDiagnosisChain(),
                request.getRootCauses(),
                request.getSolutions()
        );

        // Update confidence score if provided
        if (request.getConfidenceScore() != null) {
            experience.setConfidenceScore(request.getConfidenceScore());
            experienceRepository.save(experience);
        }

        logger.info("Experience added successfully: {}", experience.getExperienceId());
        return experience.getExperienceId();
    }

    /**
     * 从诊断请求构建ExperienceRequest
     */
    public ExperienceRequest buildExperienceRequest(String problemType, List<String> keywords,
                                                     String diagnosisChain, String rootCauses,
                                                     String solutions, Double confidenceScore) {
        return ExperienceRequest.builder()
                .problemType(problemType)
                .keywords(keywords)
                .diagnosisChain(diagnosisChain)
                .rootCauses(rootCauses)
                .solutions(solutions)
                .confidenceScore(confidenceScore)
                .build();
    }

    /**
     * Claude辅助生成经验模板
     *
     * @param diagnosisResult 完整诊断结果
     * @return 生成的经验模板JSON
     */
    public ExperienceTemplate generateTemplate(DiagnosisResult diagnosisResult) {
        logger.info("Generating experience template for session: {}", diagnosisResult.getSessionId());

        try {
            // Build prompt parameters
            Map<String, String> params = new HashMap<>();
            params.put("problem_type", diagnosisResult.getProblemType());
            params.put("user_message", diagnosisResult.getUserProblem() != null ?
                    diagnosisResult.getUserProblem() : "");

            // Diagnosis chain JSON
            String diagnosisChainJson = buildDiagnosisChainJson(diagnosisResult.getDiagnosisChainResult());
            params.put("diagnosis_chain", diagnosisChainJson);

            // Root cause JSON
            String rootCauseJson = buildRootCauseJson(diagnosisResult.getRootCauseResult());
            params.put("root_cause", rootCauseJson);

            // Solution
            params.put("solution", diagnosisResult.getSolutionApplied() != null ?
                    diagnosisResult.getSolutionApplied() :
                    (diagnosisResult.getRootCauseResult() != null &&
                     diagnosisResult.getRootCauseResult().getSolution() != null ?
                     diagnosisResult.getRootCauseResult().getSolution().getImmediateAction() : ""));

            // Get prompt from template
            String prompt = promptTemplateService.getTemplate(
                    PromptTemplateService.EXPERIENCE_GENERATOR, params);

            // Call Claude API
            ClaudeResponse response = claudeService.sendMessage(prompt);
            String claudeResult = response.getTextContent();

            // Parse Claude response into ExperienceTemplate
            ExperienceTemplate template = parseTemplateFromClaude(claudeResult);

            logger.info("Experience template generated successfully");
            return template;

        } catch (Exception e) {
            logger.error("Failed to generate experience template", e);
            return buildFallbackTemplate(diagnosisResult);
        }
    }

    /**
     * Build diagnosis chain JSON for prompt
     */
    private String buildDiagnosisChainJson(DiagnosisChainResult chainResult) {
        if (chainResult == null || chainResult.getStepResults() == null) {
            return "[]";
        }

        try {
            List<Map<String, Object>> steps = new ArrayList<>();
            int stepNum = 1;
            for (StepResult step : chainResult.getStepResults()) {
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("step", stepNum++);
                stepMap.put("action", step.getStepName() != null ? step.getStepName() : "Unknown");
                stepMap.put("tool", step.getToolName() != null ? step.getToolName() : "unknown");
                stepMap.put("key_outputs", extractKeyOutputs(step.getData() != null ? step.getData().toString() : null));
                steps.add(stepMap);
            }
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to build diagnosis chain JSON", e);
            return "[]";
        }
    }

    /**
     * Extract key outputs from step result
     */
    private List<String> extractKeyOutputs(String result) {
        if (result == null) return Collections.emptyList();

        // Simple extraction - look for important patterns
        List<String> outputs = new ArrayList<>();
        Pattern[] patterns = {
                Pattern.compile("traceId[:=]\\s*([a-zA-Z0-9\\-]+)"),
                Pattern.compile("workflowId[:=]\\s*([a-zA-Z0-9\\-]+)"),
                Pattern.compile("taskId[:=]\\s*([a-zA-Z0-9\\-]+)"),
                Pattern.compile("error_code[:=]\\s*(\\d+)"),
                Pattern.compile("status[:=]\\s*(\\d+)")
        };

        for (Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                outputs.add(matcher.group());
            }
        }

        return outputs;
    }

    /**
     * Build root cause JSON for prompt
     */
    private String buildRootCauseJson(RootCauseResult rootCause) {
        if (rootCause == null) {
            return "{}";
        }

        try {
            Map<String, Object> rootCauseMap = new LinkedHashMap<>();
            rootCauseMap.put("category", rootCause.getCategory());
            rootCauseMap.put("description", rootCause.getDescription());
            rootCauseMap.put("component", rootCause.getComponent());
            rootCauseMap.put("error_pattern", rootCause.getErrorPattern());
            rootCauseMap.put("confidence", rootCause.getConfidence());

            if (rootCause.getSolution() != null) {
                rootCauseMap.put("immediate_action", rootCause.getSolution().getImmediateAction());
                rootCauseMap.put("long_term_fix", rootCause.getSolution().getLongTermFix());
            }

            return objectMapper.writeValueAsString(rootCauseMap);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to build root cause JSON", e);
            return "{}";
        }
    }

    /**
     * Parse Claude response into ExperienceTemplate
     */
    private ExperienceTemplate parseTemplateFromClaude(String claudeResult) {
        try {
            // Extract JSON from response
            String jsonContent = extractJsonFromResponse(claudeResult);

            Map<String, Object> templateMap = objectMapper.readValue(jsonContent,
                    new TypeReference<Map<String, Object>>() {});

            ExperienceTemplate template = ExperienceTemplate.builder()
                    .problemType((String) templateMap.getOrDefault("problem_type", "UNKNOWN"))
                    .keywords(parseStringList(templateMap.get("keywords")))
                    .summary((String) templateMap.getOrDefault("summary", ""))
                    .estimatedSuccessRate(parseDouble(templateMap.get("success_rate"), 0.8))
                    .applicableScenarios(parseStringList(templateMap.get("applicable_scenarios")))
                    .expertTips(parseStringList(templateMap.get("expert_tips")))
                    .build();

            // Parse diagnosis chain steps
            if (templateMap.get("diagnosis_chain") instanceof List) {
                template.setDiagnosisChain(parseDiagnosisSteps((List<?>) templateMap.get("diagnosis_chain")));
            }

            // Parse root causes
            if (templateMap.get("root_causes") instanceof List) {
                template.setRootCauses(parseRootCauseTemplates((List<?>) templateMap.get("root_causes")));
            }

            // Parse solutions
            if (templateMap.get("solutions") instanceof List || templateMap.containsKey("solution")) {
                template.setSolutions(parseSolutionTemplates(templateMap));
            }

            return template;

        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse Claude template response", e);
            return parseSimpleTemplate(claudeResult);
        }
    }

    /**
     * Extract JSON from response
     */
    private String extractJsonFromResponse(String response) {
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        return response;
    }

    /**
     * Parse string list from object
     */
    private List<String> parseStringList(Object obj) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof List) {
            return ((List<?>) obj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (obj instanceof String) {
            return Arrays.asList(((String) obj).split(","));
        }
        return Collections.emptyList();
    }

    /**
     * Parse double value
     */
    private double parseDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Parse diagnosis steps from list
     */
    private List<ExperienceTemplate.DiagnosisStep> parseDiagnosisSteps(List<?> stepsList) {
        List<ExperienceTemplate.DiagnosisStep> steps = new ArrayList<>();
        for (Object stepObj : stepsList) {
            if (stepObj instanceof Map) {
                Map<String, Object> stepMap = (Map<String, Object>) stepObj;
                ExperienceTemplate.DiagnosisStep step = ExperienceTemplate.DiagnosisStep.builder()
                        .step(parseInt(stepMap.get("step"), 0))
                        .action((String) stepMap.getOrDefault("action", ""))
                        .tool((String) stepMap.getOrDefault("tool", ""))
                        .params(stepMap.get("params") != null ? stepMap.get("params").toString() : "")
                        .keyOutputs(parseStringList(stepMap.get("key_outputs")))
                        .build();
                steps.add(step);
            }
        }
        return steps;
    }

    /**
     * Parse int value
     */
    private int parseInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Parse root cause templates from list
     */
    private List<ExperienceTemplate.RootCauseTemplate> parseRootCauseTemplates(List<?> causesList) {
        List<ExperienceTemplate.RootCauseTemplate> causes = new ArrayList<>();
        for (Object causeObj : causesList) {
            if (causeObj instanceof Map) {
                Map<String, Object> causeMap = (Map<String, Object>) causeObj;
                ExperienceTemplate.RootCauseTemplate cause = ExperienceTemplate.RootCauseTemplate.builder()
                        .pattern((String) causeMap.getOrDefault("pattern", ""))
                        .regex((String) causeMap.getOrDefault("regex", ""))
                        .cause((String) causeMap.getOrDefault("cause", ""))
                        .solution((String) causeMap.getOrDefault("solution", ""))
                        .frequency((String) causeMap.getOrDefault("frequency", "MEDIUM"))
                        .build();
                causes.add(cause);
            }
        }
        return causes;
    }

    /**
     * Parse solution templates
     */
    private List<ExperienceTemplate.SolutionTemplate> parseSolutionTemplates(Map<String, Object> templateMap) {
        List<ExperienceTemplate.SolutionTemplate> solutions = new ArrayList<>();

        // Check if solutions is a list
        if (templateMap.get("solutions") instanceof List) {
            for (Object solObj : (List<?>) templateMap.get("solutions")) {
                if (solObj instanceof Map) {
                    Map<String, Object> solMap = (Map<String, Object>) solObj;
                    ExperienceTemplate.SolutionTemplate sol = ExperienceTemplate.SolutionTemplate.builder()
                            .description((String) solMap.getOrDefault("description", ""))
                            .steps(parseStringList(solMap.get("steps")))
                            .automationPossible(parseBoolean(solMap.get("automation_possible")))
                            .build();
                    solutions.add(sol);
                }
            }
        } else if (templateMap.containsKey("solution")) {
            // Single solution
            String solutionStr = templateMap.get("solution") != null ?
                    templateMap.get("solution").toString() : "";
            solutions.add(ExperienceTemplate.SolutionTemplate.builder()
                    .description(solutionStr)
                    .steps(Collections.emptyList())
                    .automationPossible(true)
                    .build());
        }

        return solutions;
    }

    /**
     * Parse boolean value
     */
    private boolean parseBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof String) return Boolean.parseBoolean((String) obj);
        return false;
    }

    /**
     * Parse simple template from non-JSON response
     */
    private ExperienceTemplate parseSimpleTemplate(String response) {
        return ExperienceTemplate.builder()
                .problemType("UNKNOWN")
                .keywords(Collections.emptyList())
                .summary(response.substring(0, Math.min(100, response.length())))
                .estimatedSuccessRate(0.8)
                .diagnosisChain(Collections.emptyList())
                .rootCauses(Collections.emptyList())
                .expertTips(Collections.singletonList("Review Claude response for detailed information"))
                .build();
    }

    /**
     * Build fallback template when Claude fails
     */
    private ExperienceTemplate buildFallbackTemplate(DiagnosisResult diagnosisResult) {
        RootCauseResult rootCause = diagnosisResult.getRootCauseResult();

        List<ExperienceTemplate.DiagnosisStep> steps = new ArrayList<>();
        if (diagnosisResult.getDiagnosisChainResult() != null &&
            diagnosisResult.getDiagnosisChainResult().getStepResults() != null) {
            int i = 1;
            for (StepResult step : diagnosisResult.getDiagnosisChainResult().getStepResults()) {
                steps.add(ExperienceTemplate.DiagnosisStep.builder()
                        .step(i++)
                        .action(step.getStepName() != null ? step.getStepName() : "Unknown")
                        .tool(step.getToolName() != null ? step.getToolName() : "")
                        .build());
            }
        }

        List<ExperienceTemplate.RootCauseTemplate> causes = new ArrayList<>();
        if (rootCause != null) {
            causes.add(ExperienceTemplate.RootCauseTemplate.builder()
                    .pattern(rootCause.getErrorPattern() != null ? rootCause.getErrorPattern() : "")
                    .cause(rootCause.getDescription() != null ? rootCause.getDescription() : "")
                    .frequency("MEDIUM")
                    .build());
        }

        return ExperienceTemplate.builder()
                .problemType(diagnosisResult.getProblemType())
                .keywords(diagnosisResult.getIntentResult() != null ?
                        diagnosisResult.getIntentResult().getKeywords() : Collections.emptyList())
                .summary("Manual review required - Claude generation failed")
                .diagnosisChain(steps)
                .rootCauses(causes)
                .estimatedSuccessRate(0.8)
                .expertTips(Collections.singletonList("Review diagnosis chain and root cause manually"))
                .build();
    }

    /**
     * 低置信度触发人工确认
     *
     * @param sessionId 会话ID
     * @param result    诊断结果
     * @return 待确认任务ID
     */
    @Transactional
    public String triggerManualConfirmation(String sessionId, DiagnosisResult result) {
        logger.info("Triggering manual confirmation for session: {}, confidence: {}",
                sessionId, result.getConfidence());

        // Generate confirmation ID
        String confirmationId = "conf_" + IdGenerator.generateId();

        // Determine reason for confirmation
        String reason = determineConfirmationReason(result);

        // Create pending confirmation
        PendingConfirmation confirmation = PendingConfirmation.builder()
                .confirmationId(confirmationId)
                .sessionId(sessionId)
                .problem(result.getUserProblem())
                .problemType(result.getProblemType())
                .predictedRootCause(result.getRootCauseResult() != null ?
                        result.getRootCauseResult().getDescription() : "")
                .confidence(result.getConfidence())
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(CONFIRMATION_EXPIRATION_HOURS))
                .status("PENDING")
                .userId(result.getUserFeedback() != null ? result.getUserFeedback().getUserId() : null)
                .build();

        // Store in cache
        pendingConfirmations.put(confirmationId, confirmation);

        logger.info("Manual confirmation created: {}", confirmationId);
        return confirmationId;
    }

    /**
     * Determine reason for manual confirmation
     */
    private String determineConfirmationReason(DiagnosisResult result) {
        if (result.getConfidence() < LOW_CONFIDENCE_THRESHOLD) {
            return "Low confidence score (" + result.getConfidence() + ")";
        }

        if (result.getRootCauseResult() != null &&
            "UNKNOWN".equals(result.getRootCauseResult().getCategory())) {
            return "Unknown root cause category";
        }

        if (result.getSimilarExperiences() == null || result.getSimilarExperiences().isEmpty()) {
            return "No similar experiences found";
        }

        return "Needs manual verification";
    }

    /**
     * 用户确认后录入经验
     *
     * @param confirmationId 确认任务ID
     * @param userFeedback   用户反馈(修正的根因等)
     */
    @Transactional
    public void confirmAndLearn(String confirmationId, UserFeedback userFeedback) {
        logger.info("Processing confirmation: {}, isCorrect: {}",
                confirmationId, userFeedback.isCorrect());

        PendingConfirmation confirmation = pendingConfirmations.get(confirmationId);
        if (confirmation == null) {
            logger.warn("Confirmation not found: {}", confirmationId);
            throw new ConfirmationNotFoundException("Confirmation not found: " + confirmationId);
        }

        // Check expiration
        if (confirmation.getExpiresAt() != null &&
            LocalDateTime.now().isAfter(confirmation.getExpiresAt())) {
            confirmation.setStatus("EXPIRED");
            pendingConfirmations.put(confirmationId, confirmation);
            throw new ConfirmationExpiredException("Confirmation expired: " + confirmationId);
        }

        // Get diagnosis session
        DiagnosisSession session = sessionRepository.findBySessionId(confirmation.getSessionId())
                .orElseThrow(() -> new SessionNotFoundException(
                        "Session not found: " + confirmation.getSessionId()));

        // Build experience data
        String problemType = confirmation.getProblemType();
        List<String> keywords = Collections.emptyList();
        String diagnosisChain = session.getDiagnosisChain();
        String rootCauses;
        String solutions;

        if (userFeedback.isCorrect()) {
            // Use original diagnosis
            rootCauses = session.getRootCause();
            solutions = session.getSolution();
        } else {
            // Use corrected values
            rootCauses = userFeedback.getCorrectedRootCause() != null ?
                    userFeedback.getCorrectedRootCause() : session.getRootCause();
            solutions = userFeedback.getCorrectedSolution() != null ?
                    userFeedback.getCorrectedSolution() : session.getSolution();
        }

        // Add to experience library if requested
        if (userFeedback.isAddToExperience() || userFeedback.getRating() >= MIN_BOOST_RATING) {
            Experience experience = experienceService.addExperience(
                    problemType,
                    String.join(",", keywords),
                    diagnosisChain,
                    rootCauses,
                    solutions
            );

            // Set confidence based on rating
            double confidenceScore = calculateConfidenceFromRating(userFeedback.getRating());
            experience.setConfidenceScore(confidenceScore);
            experienceRepository.save(experience);

            logger.info("New experience added from confirmation: {}", experience.getExperienceId());
        }

        // Update session with feedback
        session.setIsCorrect(userFeedback.isCorrect());
        session.setFeedbackRating(userFeedback.getRating());
        session.setFeedbackComment(userFeedback.getComment());
        if (userFeedback.getCorrectedRootCause() != null) {
            session.setActualRootCause(userFeedback.getCorrectedRootCause());
        }
        if (userFeedback.getCorrectedSolution() != null) {
            session.setActualSolution(userFeedback.getCorrectedSolution());
        }
        sessionRepository.save(session);

        // Update confirmation status
        confirmation.setStatus("CONFIRMED");
        pendingConfirmations.put(confirmationId, confirmation);

        logger.info("Confirmation processed successfully: {}", confirmationId);
    }

    /**
     * Calculate confidence score from rating
     */
    private double calculateConfidenceFromRating(int rating) {
        // Rating 5 -> confidence 1.0
        // Rating 4 -> confidence 0.9
        // Rating 3 -> confidence 0.75
        // Rating 2 -> confidence 0.6
        // Rating 1 -> confidence 0.5
        switch (rating) {
            case 5: return 1.0;
            case 4: return 0.9;
            case 3: return 0.75;
            case 2: return 0.6;
            case 1: return 0.5;
            default: return 0.7;
        }
    }

    /**
     * 反馈评分更新经验权重
     *
     * @param experienceId 经验ID
     * @param rating       评分(1-5)
     * @param comment      评论
     */
    @Transactional
    public void updateExperienceScore(String experienceId, int rating, String comment) {
        logger.info("Updating experience score: {}, rating: {}", experienceId, rating);

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        Experience experience = experienceRepository.findByExperienceId(experienceId)
                .orElseThrow(() -> new ExperienceNotFoundException(
                        "Experience not found: " + experienceId));

        // Calculate score adjustment
        double currentScore = experience.getConfidenceScore() != null ?
                experience.getConfidenceScore() : 0.8;
        double adjustment = calculateScoreAdjustment(rating);
        double newScore = Math.max(0.1, Math.min(1.0, currentScore + adjustment));

        // Update experience
        experience.setConfidenceScore(newScore);
        experience.setUsageCount(experience.getUsageCount() != null ?
                experience.getUsageCount() + 1 : 1);

        // Store comment in metadata
        if (comment != null && !comment.isEmpty()) {
            String metadata = experience.getMetadata();
            try {
                Map<String, Object> metadataMap = metadata != null ?
                        objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {}) :
                        new HashMap<>();
                List<Map<String, Object>> feedbackList = (List<Map<String, Object>>)
                        metadataMap.computeIfAbsent("feedbacks", k -> new ArrayList<>());

                Map<String, Object> feedbackEntry = new LinkedHashMap<>();
                feedbackEntry.put("rating", rating);
                feedbackEntry.put("comment", comment);
                feedbackEntry.put("timestamp", LocalDateTime.now().toString());
                feedbackList.add(feedbackEntry);

                experience.setMetadata(objectMapper.writeValueAsString(metadataMap));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to update metadata", e);
            }
        }

        experienceRepository.save(experience);

        logger.info("Experience score updated: {} -> {}", currentScore, newScore);
    }

    /**
     * Calculate score adjustment based on rating
     */
    private double calculateScoreAdjustment(int rating) {
        switch (rating) {
            case 5: return 0.05;   // Boost
            case 4: return 0.02;   // Small boost
            case 3: return 0;      // No change
            case 2: return -0.03;  // Small penalty
            case 1: return -0.1;   // Large penalty
            default: return 0;
        }
    }

    /**
     * 获取待确认列表
     *
     * @return 待确认任务列表
     */
    public List<PendingConfirmation> getPendingConfirmations() {
        // Filter out expired confirmations
        List<PendingConfirmation> active = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (PendingConfirmation confirmation : pendingConfirmations.values()) {
            if ("PENDING".equals(confirmation.getStatus()) &&
                (confirmation.getExpiresAt() == null || now.isBefore(confirmation.getExpiresAt()))) {
                active.add(confirmation);
            } else if ("PENDING".equals(confirmation.getStatus()) &&
                       confirmation.getExpiresAt() != null &&
                       now.isAfter(confirmation.getExpiresAt())) {
                // Mark as expired
                confirmation.setStatus("EXPIRED");
                pendingConfirmations.put(confirmation.getConfirmationId(), confirmation);
            }
        }

        // Sort by creation time (oldest first)
        active.sort(Comparator.comparing(PendingConfirmation::getCreatedAt));

        logger.info("Retrieved {} pending confirmations", active.size());
        return active;
    }

    /**
     * 获取指定确认任务
     *
     * @param confirmationId 确认任务ID
     * @return 确认任务详情
     */
    public Optional<PendingConfirmation> getConfirmation(String confirmationId) {
        return Optional.ofNullable(pendingConfirmations.get(confirmationId));
    }

    /**
     * 优化低分经验
     *
     * 查找评分较低的经验，标记为需要优化或删除
     */
    @Transactional
    public void optimizeLowScoreExperiences() {
        logger.info("Starting low score experience optimization");

        // Find experiences with confidence score below threshold
        double lowScoreThreshold = 0.5;

        List<Experience> allExperiences = experienceRepository.findAll();
        List<Experience> lowScoreExperiences = allExperiences.stream()
                .filter(exp -> exp.getConfidenceScore() != null &&
                               exp.getConfidenceScore() < lowScoreThreshold)
                .collect(Collectors.toList());

        logger.info("Found {} low score experiences", lowScoreExperiences.size());

        for (Experience exp : lowScoreExperiences) {
            try {
                // Option 1: Reduce confidence further if usage is low
                if (exp.getUsageCount() != null && exp.getUsageCount() < 3) {
                    exp.setConfidenceScore(exp.getConfidenceScore() * 0.8);
                    logger.info("Reduced confidence for low usage experience: {}",
                            exp.getExperienceId());
                }

                // Option 2: If usage is high but confidence is low, might need review
                if (exp.getUsageCount() != null && exp.getUsageCount() >= 5) {
                    // Mark for review in metadata
                    String metadata = exp.getMetadata();
                    Map<String, Object> metadataMap = metadata != null ?
                            objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {}) :
                            new HashMap<>();
                    metadataMap.put("needsReview", true);
                    metadataMap.put("reviewReason", "High usage but low confidence");
                    exp.setMetadata(objectMapper.writeValueAsString(metadataMap));
                    logger.info("Marked experience for review: {}", exp.getExperienceId());
                }

                experienceRepository.save(exp);

            } catch (Exception e) {
                logger.warn("Failed to optimize experience: {}", exp.getExperienceId(), e);
            }
        }

        logger.info("Low score experience optimization completed");
    }

    /**
     * Process feedback request from diagnosis session
     */
    @Transactional
    public void processFeedback(String sessionId, FeedbackRequest feedback) {
        logger.info("Processing feedback for session: {}", sessionId);

        DiagnosisSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

        // Build UserFeedback from FeedbackRequest
        UserFeedback userFeedback = UserFeedback.builder()
                .isCorrect(feedback.getIsCorrect() != null ? feedback.getIsCorrect() : false)
                .correctedRootCause(feedback.getActualRootCause())
                .correctedSolution(feedback.getActualSolution())
                .rating(feedback.getRating() != null ? feedback.getRating() : 3)
                .comment(feedback.getComment())
                .userId(feedback.getUserId())
                .addToExperience(true)  // Default to adding to experience
                .build();

        // Trigger confirmation if diagnosis was incorrect
        if (!userFeedback.isCorrect() && userFeedback.getCorrectedRootCause() != null) {
            String confirmationId = triggerManualConfirmation(sessionId, buildDiagnosisResult(session));
            confirmAndLearn(confirmationId, userFeedback);
        } else {
            // Just record the feedback
            session.setFeedbackRating(userFeedback.getRating());
            session.setFeedbackComment(userFeedback.getComment());
            session.setIsCorrect(userFeedback.isCorrect());
            sessionRepository.save(session);
        }
    }

    /**
     * Build DiagnosisResult from DiagnosisSession
     */
    private DiagnosisResult buildDiagnosisResult(DiagnosisSession session) {
        RootCauseResult rootCause = RootCauseResult.builder()
                .category("UNKNOWN")
                .description(session.getRootCause())
                .confidence(session.getConfidenceScore() != null ? session.getConfidenceScore() : 0.7)
                .build();

        IntentResult intent = IntentResult.builder()
                .problemType(session.getProblemType())
                .originalMessage(session.getProblem())
                .confidence(0.8)
                .build();

        return DiagnosisResult.builder()
                .sessionId(session.getSessionId())
                .problemType(session.getProblemType())
                .userProblem(session.getProblem())
                .rootCauseResult(rootCause)
                .intentResult(intent)
                .solutionApplied(session.getSolution())
                .confidence(session.getConfidenceScore() != null ? session.getConfidenceScore() : 0.7)
                .status(session.getStatus())
                .build();
    }

    /**
     * Delete expired confirmations (cleanup method)
     */
    public void cleanupExpiredConfirmations() {
        LocalDateTime now = LocalDateTime.now();
        List<String> toRemove = new ArrayList<>();

        for (PendingConfirmation confirmation : pendingConfirmations.values()) {
            if (confirmation.getExpiresAt() != null &&
                now.isAfter(confirmation.getExpiresAt()) &&
                "EXPIRED".equals(confirmation.getStatus())) {
                toRemove.add(confirmation.getConfirmationId());
            }
        }

        for (String id : toRemove) {
            pendingConfirmations.remove(id);
        }

        logger.info("Cleaned up {} expired confirmations", toRemove.size());
    }

    // Custom Exceptions
    public static class ConfirmationNotFoundException extends RuntimeException {
        public ConfirmationNotFoundException(String message) {
            super(message);
        }
    }

    public static class ConfirmationExpiredException extends RuntimeException {
        public ConfirmationExpiredException(String message) {
            super(message);
        }
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }

    public static class ExperienceNotFoundException extends RuntimeException {
        public ExperienceNotFoundException(String message) {
            super(message);
        }
    }
}