package com.drs.agent.service;

import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.model.Experience;
import com.drs.agent.service.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RootCauseAnalyzer - 根因推理模块
 *
 * 综合诊断结果和相似经验，分析问题根本原因。
 * 支持以下功能：
 * 1. 从诊断结果中提取关键证据
 * 2. 匹配已知根因模式（从经验库）
 * 3. Claude推理生成根因（复杂场景）
 * 4. 生成置信度和解决方案建议
 */
@Service
public class RootCauseAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(RootCauseAnalyzer.class);

    private final ClaudeService claudeService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    // Known root cause categories
    private static final List<String> ROOT_CAUSE_CATEGORIES = Arrays.asList(
            "PERMISSION", "CONFIGURATION", "RESOURCE", "NETWORK", "CODE_BUG", "DATA", "UNKNOWN"
    );

    // Risk levels
    private static final List<String> RISK_LEVELS = Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");

    // Minimum confidence threshold for pattern matching
    private static final double MIN_PATTERN_CONFIDENCE = 0.7;

    // Confidence threshold for Claude analysis
    private static final double MIN_CLAUDE_CONFIDENCE = 0.85;

    public RootCauseAnalyzer(ClaudeService claudeService,
                              PromptTemplateService promptTemplateService,
                              ObjectMapper objectMapper) {
        this.claudeService = claudeService;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析根因
     *
     * @param chainResult        诊断链路执行结果
     * @param similarExperiences 相似经验案例
     * @param intent             意图识别结果
     * @return RootCauseResult包含根因、解决方案、置信度
     */
    public RootCauseResult analyze(DiagnosisChainResult chainResult,
                                   List<Experience> similarExperiences,
                                   IntentResult intent) {
        logger.info("Starting root cause analysis for problem type: {}", intent.getProblemType());
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Extract evidence from diagnosis chain
            List<String> evidence = extractEvidence(chainResult);
            logger.debug("Extracted {} evidence items", evidence.size());

            // Step 2: Try to match known patterns from experience library
            Optional<RootCausePattern> matchedPattern = matchKnownPatterns(similarExperiences, chainResult);

            RootCauseResult result;
            if (matchedPattern.isPresent() && matchedPattern.get().getConfidence() >= MIN_PATTERN_CONFIDENCE) {
                // Pattern matching succeeded with high confidence
                result = buildResultFromPattern(matchedPattern.get(), evidence, similarExperiences, chainResult);
                logger.info("Root cause identified via pattern matching: {}", result.getCategory());
            } else {
                // Use Claude for complex analysis
                Map<String, String> params = buildPromptParams(chainResult, similarExperiences, intent);
                result = analyzeWithClaude(params, evidence, similarExperiences);
                logger.info("Root cause identified via Claude analysis: {}", result.getCategory());
            }

            // Set analysis time
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);

            // Determine if learning is suggested
            result.setSuggestedLearning(determineLearningNeed(result, similarExperiences));

            logger.info("Root cause analysis completed. Category: {}, Confidence: {}",
                    result.getCategory(), result.getConfidence());

            return result;

        } catch (Exception e) {
            logger.error("Root cause analysis failed", e);
            return buildFallbackResult(intent, chainResult);
        }
    }

    /**
     * 从诊断结果中提取关键证据
     *
     * @param chainResult 诊断链路结果
     * @return 证据列表
     */
    private List<String> extractEvidence(DiagnosisChainResult chainResult) {
        List<String> evidence = new ArrayList<>();

        if (chainResult == null || chainResult.getStepResults() == null) {
            return evidence;
        }

        // Extract from each step result
        for (StepResult step : chainResult.getStepResults()) {
            if (step.getData() != null) {
                // Look for error patterns
                extractErrorsFromStep(step, evidence);
                // Look for key findings
                extractKeyFindingsFromStep(step, evidence);
            }
        }

        // Extract from aggregated context
        if (chainResult.getAggregatedContext() != null) {
            Map<String, Object> context = chainResult.getAggregatedContext();
            extractEvidenceFromContext(context, evidence);
        }

        return evidence;
    }

    /**
     * Extract error information from a step result
     */
    private void extractErrorsFromStep(StepResult step, List<String> evidence) {
        String result = step.getData() != null ? step.getData().toString() : "";

        // Common error patterns
        Pattern[] errorPatterns = {
                Pattern.compile("ERROR:\\s*(.+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Exception:\\s*(.+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Failed:\\s*(.+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Permission denied.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Timeout.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Connection refused.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("error_code[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("status[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : errorPatterns) {
            java.util.regex.Matcher matcher = pattern.matcher(result);
            while (matcher.find()) {
                evidence.add(String.format("[%s] %s: %s",
                        step.getStepName(), "ERROR", matcher.group()));
            }
        }
    }

    /**
     * Extract key findings from a step result
     */
    private void extractKeyFindingsFromStep(StepResult step, List<String> evidence) {
        String result = step.getData() != null ? step.getData().toString() : "";

        // Key finding patterns
        Pattern[] findingPatterns = {
                Pattern.compile("Found:\\s*(.+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("Detected:\\s*(.+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("traceId[:=]\\s*([a-zA-Z0-9\\-]+)"),
                Pattern.compile("workflowId[:=]\\s*([a-zA-Z0-9\\-]+)"),
                Pattern.compile("taskId[:=]\\s*([a-zA-Z0-9\\-]+)")
        };

        for (Pattern pattern : findingPatterns) {
            java.util.regex.Matcher matcher = pattern.matcher(result);
            while (matcher.find()) {
                evidence.add(String.format("[%s] %s",
                        step.getStepName(), matcher.group()));
            }
        }

        // Add step summary if significant
        if (step.isSuccess() && result.length() > 100) {
            evidence.add(String.format("[%s] Completed successfully", step.getStepName()));
        }
    }

    /**
     * Extract evidence from aggregated context
     */
    private void extractEvidenceFromContext(Map<String, Object> context, List<String> evidence) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Important context keys
            if (key.contains("error") || key.contains("fail") || key.contains("exception")) {
                evidence.add(String.format("Context[%s]: %s", key, value));
            }
            if (key.equals("traceId") || key.equals("workflowId") || key.equals("taskId")) {
                evidence.add(String.format("Context[%s]: %s", key, value));
            }
        }
    }

    /**
     * 匹配已知根因模式(从经验库)
     *
     * @param experiences 相似经验列表
     * @param chain        诊断链结果
     * @return 匹配的根因模式（Optional）
     */
    private Optional<RootCausePattern> matchKnownPatterns(List<Experience> experiences,
                                                          DiagnosisChainResult chain) {
        if (experiences == null || experiences.isEmpty() || chain == null) {
            return Optional.empty();
        }

        // Get all results from chain
        String combinedResults = chain.getStepResults() != null ?
                chain.getStepResults().stream()
                        .map(s -> s.getData() != null ? s.getData().toString() : "")
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n")) :
                "";

        // Try to match patterns from each experience
        for (Experience exp : experiences) {
            try {
                List<RootCausePattern> patterns = parsePatternsFromExperience(exp);
                for (RootCausePattern pattern : patterns) {
                    if (patternMatches(pattern, combinedResults)) {
                        // Calculate confidence based on experience usage and similarity
                        double confidence = calculatePatternConfidence(exp, pattern);
                        pattern.setConfidence(confidence);
                        pattern.setExperienceId(exp.getExperienceId());
                        logger.info("Pattern matched: {} from experience {}",
                                pattern.getPatternName(), exp.getExperienceId());
                        return Optional.of(pattern);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to parse patterns from experience: {}", exp.getExperienceId());
            }
        }

        return Optional.empty();
    }

    /**
     * Parse root cause patterns from experience entity
     */
    private List<RootCausePattern> parsePatternsFromExperience(Experience experience) {
        List<RootCausePattern> patterns = new ArrayList<>();

        if (experience.getRootCauses() != null) {
            try {
                // Try to parse as JSON array
                List<Map<String, Object>> causesList = objectMapper.readValue(
                        experience.getRootCauses(),
                        new TypeReference<List<Map<String, Object>>>() {});

                for (Map<String, Object> cause : causesList) {
                    RootCausePattern pattern = RootCausePattern.builder()
                            .patternName((String) cause.getOrDefault("pattern", "Unknown"))
                            .regexPattern((String) cause.getOrDefault("regex", ""))
                            .cause((String) cause.getOrDefault("cause", ""))
                            .solution((String) cause.getOrDefault("solution", ""))
                            .frequency((String) cause.getOrDefault("frequency", "MEDIUM"))
                            .keywords(parseKeywordsFromCause(cause))
                            .build();
                    patterns.add(pattern);
                }
            } catch (JsonProcessingException e) {
                // Try simple parsing
                RootCausePattern pattern = RootCausePattern.builder()
                        .patternName("SimplePattern")
                        .regexPattern("")
                        .cause(experience.getRootCauses())
                        .solution(experience.getSolutions())
                        .frequency("MEDIUM")
                        .build();
                patterns.add(pattern);
            }
        }

        return patterns;
    }

    /**
     * Parse keywords from cause map
     */
    private List<String> parseKeywordsFromCause(Map<String, Object> cause) {
        Object keywordsObj = cause.get("keywords");
        if (keywordsObj instanceof List) {
            return ((List<?>) keywordsObj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else if (keywordsObj instanceof String) {
            return Arrays.asList(((String) keywordsObj).split(","));
        }
        return Collections.emptyList();
    }

    /**
     * Check if a pattern matches the diagnosis results
     */
    private boolean patternMatches(RootCausePattern pattern, String combinedResults) {
        if (pattern.getRegexPattern() != null && !pattern.getRegexPattern().isEmpty()) {
            try {
                Pattern regex = Pattern.compile(pattern.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                return regex.matcher(combinedResults).find();
            } catch (Exception e) {
                logger.warn("Invalid regex pattern: {}", pattern.getRegexPattern());
            }
        }

        // Keyword matching fallback
        if (pattern.getKeywords() != null) {
            for (String keyword : pattern.getKeywords()) {
                if (combinedResults.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }

        // Pattern name matching
        if (pattern.getPatternName() != null) {
            return combinedResults.toLowerCase().contains(pattern.getPatternName().toLowerCase());
        }

        return false;
    }

    /**
     * Calculate confidence for a matched pattern
     */
    private double calculatePatternConfidence(Experience experience, RootCausePattern pattern) {
        double baseConfidence = 0.6;

        // Boost by experience confidence score
        if (experience.getConfidenceScore() != null) {
            baseConfidence += experience.getConfidenceScore() * 0.2;
        }

        // Boost by usage count (more usage = more reliable)
        if (experience.getUsageCount() != null) {
            baseConfidence += Math.min(experience.getUsageCount() * 0.01, 0.1);
        }

        // Boost by frequency
        if ("HIGH".equals(pattern.getFrequency())) {
            baseConfidence += 0.1;
        }

        return Math.min(baseConfidence, 1.0);
    }

    /**
     * 构建Claude Prompt参数
     *
     * @param chain       诊断链结果
     * @param experiences 相似经验
     * @param intent      意图结果
     * @return Prompt参数Map
     */
    private Map<String, String> buildPromptParams(DiagnosisChainResult chain,
                                                   List<Experience> experiences,
                                                   IntentResult intent) {
        Map<String, String> params = new HashMap<>();

        params.put("problem_type", intent.getProblemType());
        params.put("user_message", intent.getOriginalMessage() != null ? intent.getOriginalMessage() : "");

        // Build step results string
        StringBuilder stepResults = new StringBuilder();
        if (chain != null && chain.getStepResults() != null) {
            for (StepResult step : chain.getStepResults()) {
                stepResults.append(String.format("Step: %s\nTool: %s\nResult: %s\nStatus: %s\n\n",
                        step.getStepName(),
                        step.getToolName() != null ? step.getToolName() : "unknown",
                        step.getData() != null ? step.getData().toString() : "no result",
                        step.isSuccess() ? "SUCCESS" : "FAILED"));
            }
        }
        params.put("step_results", stepResults.toString());

        // Build similar experiences string
        StringBuilder expBuilder = new StringBuilder();
        if (experiences != null && !experiences.isEmpty()) {
            for (Experience exp : experiences) {
                expBuilder.append(String.format("Experience ID: %s\nProblem Type: %s\nKeywords: %s\nRoot Causes: %s\nSolutions: %s\n\n",
                        exp.getExperienceId(),
                        exp.getProblemType(),
                        exp.getKeywords(),
                        exp.getRootCauses() != null ? exp.getRootCauses() : "N/A",
                        exp.getSolutions() != null ? exp.getSolutions() : "N/A"));
            }
        }
        params.put("similar_experiences", expBuilder.toString());

        // Wiki content (placeholder for now)
        params.put("wiki_content", chain != null && chain.getAggregatedContext() != null ?
                chain.getAggregatedContext().toString() : "");

        return params;
    }

    /**
     * Claude推理生成根因(复杂场景)
     *
     * @param params        Prompt参数
     * @param evidence      证据列表
     * @param experiences   相似经验
     * @return RootCauseResult
     */
    private RootCauseResult analyzeWithClaude(Map<String, String> params,
                                               List<String> evidence,
                                               List<Experience> experiences) {
        logger.info("Analyzing root cause with Claude for complex scenario");

        try {
            // Get prompt from template
            String prompt = promptTemplateService.getTemplate(
                    PromptTemplateService.ROOT_CAUSE_ANALYZER, params);

            // Call Claude API
            ClaudeResponse response = claudeService.sendMessage(prompt);
            String claudeResult = response.getTextContent();

            // Parse Claude response
            RootCauseResult result = parseClaudeResponse(claudeResult);

            // Add evidence and related experiences
            result.setEvidence(evidence);
            result.setRelatedExperiences(experiences.stream()
                    .map(Experience::getExperienceId)
                    .collect(Collectors.toList()));

            return result;

        } catch (Exception e) {
            logger.error("Claude analysis failed", e);
            return buildFallbackClaudeResult(evidence);
        }
    }

    /**
     * Parse Claude response into RootCauseResult
     */
    private RootCauseResult parseClaudeResponse(String claudeResult) {
        try {
            // Try to extract JSON from response
            String jsonContent = extractJsonFromResponse(claudeResult);

            Map<String, Object> resultMap = objectMapper.readValue(jsonContent,
                    new TypeReference<Map<String, Object>>() {});

            RootCauseResult result = new RootCauseResult();

            // Parse root_cause section
            Object rootCauseObj = resultMap.get("root_cause");
            if (rootCauseObj instanceof Map) {
                Map<String, Object> rootCause = (Map<String, Object>) rootCauseObj;
                result.setCategory((String) rootCause.getOrDefault("category", "UNKNOWN"));
                result.setDescription((String) rootCause.getOrDefault("description", ""));
                result.setComponent((String) rootCause.getOrDefault("component", ""));
                result.setErrorPattern((String) rootCause.getOrDefault("error_pattern", ""));
            }

            // Parse solution section
            Object solutionObj = resultMap.get("solution");
            if (solutionObj instanceof Map) {
                Map<String, Object> solutionMap = (Map<String, Object>) solutionObj;
                Solution solution = Solution.builder()
                        .immediateAction((String) solutionMap.getOrDefault("immediate_action", ""))
                        .longTermFix((String) solutionMap.getOrDefault("long_term_fix", ""))
                        .automationPossible(parseBoolean(solutionMap.get("automation_possible")))
                        .steps(parseSteps(solutionMap.get("steps")))
                        .build();
                result.setSolution(solution);
            }

            // Parse other fields
            Object confidenceObj = resultMap.get("confidence");
            if (confidenceObj instanceof Number) {
                result.setConfidence(((Number) confidenceObj).doubleValue());
            } else {
                result.setConfidence(0.7);
            }

            result.setRiskLevel((String) resultMap.getOrDefault("risk_level", "MEDIUM"));
            result.setSuggestedLearning(parseBoolean(resultMap.get("suggested_learning")));

            // Parse evidence and related experiences from JSON
            if (resultMap.get("evidence") instanceof List) {
                result.setEvidence(((List<?>) resultMap.get("evidence")).stream()
                        .map(Object::toString).collect(Collectors.toList()));
            }

            if (resultMap.get("related_experiences") instanceof List) {
                result.setRelatedExperiences(((List<?>) resultMap.get("related_experiences")).stream()
                        .map(Object::toString).collect(Collectors.toList()));
            }

            // Generate summary
            result.setSummary(generateSummary(result));

            return result;

        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse Claude response as JSON", e);
            return parseSimpleResponse(claudeResult);
        }
    }

    /**
     * Extract JSON content from Claude response
     */
    private String extractJsonFromResponse(String response) {
        // Try to find JSON block
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }

        // Return original if no JSON found
        return response;
    }

    /**
     * Parse boolean value
     */
    private boolean parseBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return false;
    }

    /**
     * Parse steps list
     */
    private List<String> parseSteps(Object stepsObj) {
        if (stepsObj instanceof List) {
            return ((List<?>) stepsObj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Parse simple non-JSON response
     */
    private RootCauseResult parseSimpleResponse(String response) {
        RootCauseResult result = new RootCauseResult();

        // Simple keyword-based parsing
        if (response.toLowerCase().contains("permission")) {
            result.setCategory("PERMISSION");
        } else if (response.toLowerCase().contains("config")) {
            result.setCategory("CONFIGURATION");
        } else if (response.toLowerCase().contains("resource") || response.toLowerCase().contains("memory")) {
            result.setCategory("RESOURCE");
        } else if (response.toLowerCase().contains("network") || response.toLowerCase().contains("connection")) {
            result.setCategory("NETWORK");
        } else if (response.toLowerCase().contains("bug") || response.toLowerCase().contains("code")) {
            result.setCategory("CODE_BUG");
        } else {
            result.setCategory("UNKNOWN");
        }

        result.setDescription(response);
        result.setConfidence(0.6);
        result.setRiskLevel("MEDIUM");
        result.setSolution(Solution.builder()
                .immediateAction("Review the diagnosis results and take appropriate action")
                .build());

        return result;
    }

    /**
     * Build result from matched pattern
     */
    private RootCauseResult buildResultFromPattern(RootCausePattern pattern,
                                                    List<String> evidence,
                                                    List<Experience> experiences,
                                                    DiagnosisChainResult chain) {
        // Determine category from pattern
        String category = determineCategoryFromPattern(pattern);

        Solution solution = Solution.builder()
                .immediateAction(pattern.getSolution())
                .longTermFix(pattern.getSolution())
                .automationPossible(true)
                .steps(Arrays.asList("Apply solution from experience: " + pattern.getExperienceId()))
                .build();

        return RootCauseResult.builder()
                .category(category)
                .description(pattern.getCause())
                .errorPattern(pattern.getRegexPattern())
                .solution(solution)
                .confidence(pattern.getConfidence())
                .evidence(evidence)
                .relatedExperiences(experiences.stream()
                        .map(Experience::getExperienceId)
                        .collect(Collectors.toList()))
                .riskLevel(determineRiskLevel(pattern))
                .summary(pattern.getCause())
                .build();
    }

    /**
     * Determine category from pattern
     */
    private String determineCategoryFromPattern(RootCausePattern pattern) {
        String cause = pattern.getCause() != null ? pattern.getCause().toLowerCase() : "";

        if (cause.contains("permission") || cause.contains("权限")) {
            return "PERMISSION";
        } else if (cause.contains("config") || cause.contains("配置")) {
            return "CONFIGURATION";
        } else if (cause.contains("resource") || cause.contains("memory") || cause.contains("资源")) {
            return "RESOURCE";
        } else if (cause.contains("network") || cause.contains("connection") || cause.contains("网络")) {
            return "NETWORK";
        } else if (cause.contains("bug") || cause.contains("code") || cause.contains("代码")) {
            return "CODE_BUG";
        }

        return "UNKNOWN";
    }

    /**
     * Determine risk level from pattern
     */
    private String determineRiskLevel(RootCausePattern pattern) {
        String frequency = pattern.getFrequency();

        if ("HIGH".equals(frequency)) {
            return "HIGH";
        } else if ("LOW".equals(frequency)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    /**
     * Generate summary from result
     */
    private String generateSummary(RootCauseResult result) {
        return String.format("[%s] %s - %s",
                result.getCategory(),
                result.getComponent() != null ? result.getComponent() : "Unknown Component",
                result.getDescription() != null ? result.getDescription().substring(0, Math.min(100, result.getDescription().length())) : "No description");
    }

    /**
     * Determine if learning is needed
     */
    private boolean determineLearningNeed(RootCauseResult result, List<Experience> experiences) {
        // Suggest learning if:
        // 1. Confidence is moderate (not very high)
        // 2. No similar experiences found
        // 3. Pattern is new/interesting

        if (experiences == null || experiences.isEmpty()) {
            return true;  // New pattern, should learn
        }

        if (result.getConfidence() < MIN_CLAUDE_CONFIDENCE && result.getConfidence() >= 0.6) {
            return true;  // Moderate confidence, worth learning
        }

        if ("UNKNOWN".equals(result.getCategory())) {
            return true;  // Unknown category, worth investigating
        }

        return false;
    }

    /**
     * Build fallback result when analysis fails
     */
    private RootCauseResult buildFallbackResult(IntentResult intent, DiagnosisChainResult chain) {
        return RootCauseResult.builder()
                .category("UNKNOWN")
                .description("Analysis failed. Please review the diagnosis results manually.")
                .confidence(0.5)
                .evidence(Collections.singletonList("Analysis system encountered an error"))
                .riskLevel("MEDIUM")
                .solution(Solution.builder()
                        .immediateAction("Review diagnosis chain results and analyze manually")
                        .automationPossible(false)
                        .build())
                .suggestedLearning(true)
                .build();
    }

    /**
     * Build fallback result for Claude failure
     */
    private RootCauseResult buildFallbackClaudeResult(List<String> evidence) {
        return RootCauseResult.builder()
                .category("UNKNOWN")
                .description("Claude analysis failed. Please review evidence manually.")
                .confidence(0.5)
                .evidence(evidence)
                .riskLevel("MEDIUM")
                .solution(Solution.builder()
                        .immediateAction("Review collected evidence and analyze manually")
                        .automationPossible(false)
                        .build())
                .suggestedLearning(true)
                .build();
    }
}