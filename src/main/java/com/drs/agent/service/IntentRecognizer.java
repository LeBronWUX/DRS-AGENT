package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.service.dto.IntentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intent Recognizer
 *
 * Analyzes user problem descriptions to identify problem types and extract context.
 * Uses keyword matching for fast classification and Claude API for complex cases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRecognizer {

    private final ClaudeService claudeService;
    private final PromptTemplateService promptTemplateService;
    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    // Supported problem types
    private static final List<String> PROBLEM_TYPES = Arrays.asList(
            "任务创建失败", "鉴权失败", "再编辑丢失对象",
            "增量同步失败", "全量迁移失败", "性能问题", "UNKNOWN"
    );

    // Keyword mappings for fast classification
    private static final Map<String, List<String>> PROBLEM_KEYWORDS = new LinkedHashMap<>();

    static {
        PROBLEM_KEYWORDS.put("任务创建失败", Arrays.asList(
                "创建失败", "任务创建", "无法创建", "create failed", "creation error",
                "任务新建", "新建任务", "添加任务"
        ));
        PROBLEM_KEYWORDS.put("鉴权失败", Arrays.asList(
                "鉴权失败", "认证失败", "权限不足", "authorization", "auth failed",
                "登录失败", "认证错误", "access denied", "unauthorized", "401", "403"
        ));
        PROBLEM_KEYWORDS.put("再编辑丢失对象", Arrays.asList(
                "再编辑", "丢失对象", "编辑后丢失", "对象丢失", "数据丢失",
                "re-edit", "missing object", "data missing", "对象不存在"
        ));
        PROBLEM_KEYWORDS.put("增量同步失败", Arrays.asList(
                "增量同步", "同步失败", "incremental", "增量迁移", "sync failed",
                "增量数据", "数据同步", "incremental sync", "增量复制"
        ));
        PROBLEM_KEYWORDS.put("全量迁移失败", Arrays.asList(
                "全量迁移", "迁移失败", "full migration", "全量数据", "迁移错误",
                "批量迁移", "migration failed", "数据迁移", "全量同步"
        ));
        PROBLEM_KEYWORDS.put("性能问题", Arrays.asList(
                "性能问题", "慢", "超时", "timeout", "performance", "响应慢",
                "延迟高", "卡顿", "latency", "slow", "性能下降", "吞吐量低"
        ));
    }

    // Tool recommendations for each problem type
    private static final Map<String, List<String>> TOOL_RECOMMENDATIONS = new LinkedHashMap<>();

    static {
        TOOL_RECOMMENDATIONS.put("任务创建失败", Arrays.asList(
                "query_ops_platform", "query_logs", "get_alert_context", "search_wiki"
        ));
        TOOL_RECOMMENDATIONS.put("鉴权失败", Arrays.asList(
                "query_ops_platform", "query_logs", "search_wiki"
        ));
        TOOL_RECOMMENDATIONS.put("再编辑丢失对象", Arrays.asList(
                "query_ops_platform", "query_logs", "search_wiki"
        ));
        TOOL_RECOMMENDATIONS.put("增量同步失败", Arrays.asList(
                "query_ops_platform", "query_logs", "get_alert_context", "search_wiki"
        ));
        TOOL_RECOMMENDATIONS.put("全量迁移失败", Arrays.asList(
                "query_ops_platform", "query_logs", "get_alert_context", "search_wiki"
        ));
        TOOL_RECOMMENDATIONS.put("性能问题", Arrays.asList(
                "query_ops_platform", "query_logs", "get_alert_context", "search_wiki"
        ));
    }

    // Patterns for extracting context
    private static final Pattern WORKFLOW_ID_PATTERN = Pattern.compile(
            "(workflowId|workflow_id|workflow|工作流)[\\s:=]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALERT_ID_PATTERN = Pattern.compile(
            "(alertId|alert_id|alert|告警)[\\s:=]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TASK_ID_PATTERN = Pattern.compile(
            "(taskId|task_id|task|任务)[\\s:=]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile(
            "(error_code|errorCode|error|错误码)[\\s:=]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(timestamp|时间|date|日期)[\\s:=]+([\\d-:T\\s]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "(service|服务|component|组件)[\\s:=]+([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Confidence threshold for using Claude classification
    private static final double CLAUDE_THRESHOLD = 0.6;

    /**
     * Recognize the intent from a user problem description.
     *
     * @param userMessage User problem description
     * @return IntentResult containing problem type, confidence, context, and keywords
     */
    public IntentResult recognize(String userMessage) {
        log.info("Recognizing intent for user message: {}", userMessage);

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return buildUnknownResult("Empty message");
        }

        // Step 1: Extract context information
        Map<String, String> context = extractContext(userMessage);
        log.debug("Extracted context: {}", context);

        // Step 2: Extract keywords
        List<String> keywords = extractKeywords(userMessage);
        log.debug("Extracted keywords: {}", keywords);

        // Step 3: Fast classification by keyword matching
        IntentResult keywordResult = classifyByKeywords(userMessage);

        // Step 4: If confidence is low, use Claude for intelligent classification
        if (keywordResult.getConfidence() < CLAUDE_THRESHOLD) {
            log.info("Low confidence from keyword matching ({}), using Claude classification",
                    keywordResult.getConfidence());
            IntentResult claudeResult = classifyWithClaude(userMessage);
            if (claudeResult != null && claudeResult.getConfidence() > keywordResult.getConfidence()) {
                // Merge context from keyword extraction
                claudeResult.setContext(context);
                claudeResult.setKeywords(keywords);
                return claudeResult;
            }
        }

        // Add extracted context and keywords to result
        keywordResult.setContext(context);
        keywordResult.setKeywords(keywords);
        keywordResult.setOriginalMessage(userMessage);

        // Step 5: Add suggested tools
        List<String> suggestedTools = getSuggestedTools(keywordResult.getProblemType());
        keywordResult.setSuggestedTools(suggestedTools);

        log.info("Intent recognized: problemType={}, confidence={}",
                keywordResult.getProblemType(), keywordResult.getConfidence());

        return keywordResult;
    }

    /**
     * Extract context information from the problem description.
     *
     * @param userMessage User problem description
     * @return Map of extracted context key-value pairs
     */
    private Map<String, String> extractContext(String userMessage) {
        Map<String, String> context = new LinkedHashMap<>();

        // Extract workflow ID
        Matcher workflowMatcher = WORKFLOW_ID_PATTERN.matcher(userMessage);
        if (workflowMatcher.find()) {
            context.put("workflowId", workflowMatcher.group(2));
        }

        // Extract alert ID
        Matcher alertMatcher = ALERT_ID_PATTERN.matcher(userMessage);
        if (alertMatcher.find()) {
            context.put("alertId", alertMatcher.group(2));
        }

        // Extract task ID
        Matcher taskMatcher = TASK_ID_PATTERN.matcher(userMessage);
        if (taskMatcher.find()) {
            context.put("taskId", taskMatcher.group(2));
        }

        // Extract error code
        Matcher errorMatcher = ERROR_CODE_PATTERN.matcher(userMessage);
        if (errorMatcher.find()) {
            context.put("error_code", errorMatcher.group(2));
        }

        // Extract timestamp
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(userMessage);
        if (timestampMatcher.find()) {
            context.put("timestamp", timestampMatcher.group(2).trim());
        }

        // Extract service/component
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(userMessage);
        if (serviceMatcher.find()) {
            context.put("service", serviceMatcher.group(2));
        }

        // Extract any quoted strings as potential identifiers
        Pattern quotedPattern = Pattern.compile("\"([^\"]+)\"|'([^']+)'");
        Matcher quotedMatcher = quotedPattern.matcher(userMessage);
        while (quotedMatcher.find()) {
            String quotedValue = quotedMatcher.group(1) != null ? quotedMatcher.group(1) : quotedMatcher.group(2);
            if (quotedValue != null && !quotedValue.isEmpty()) {
                // Use as a generic identifier if not already captured
                if (!context.containsValue(quotedValue)) {
                    context.put("quoted_value_" + context.size(), quotedValue);
                }
            }
        }

        return context;
    }

    /**
     * Extract keywords from the problem description.
     *
     * @param userMessage User problem description
     * @return List of extracted keywords
     */
    private List<String> extractKeywords(String userMessage) {
        List<String> keywords = new ArrayList<>();

        // Chinese keywords
        String[] chineseKeywords = {"失败", "错误", "异常", "超时", "丢失", "不存在", "无法", "问题", "告警"};
        for (String kw : chineseKeywords) {
            if (userMessage.contains(kw)) {
                keywords.add(kw);
            }
        }

        // English keywords
        String[] englishKeywords = {"failed", "error", "exception", "timeout", "missing", "not found",
                "cannot", "issue", "alert", "slow", "performance"};
        String lowerMessage = userMessage.toLowerCase();
        for (String kw : englishKeywords) {
            if (lowerMessage.contains(kw)) {
                keywords.add(kw);
            }
        }

        // Add matched problem type keywords
        for (Map.Entry<String, List<String>> entry : PROBLEM_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lowerMessage.contains(kw.toLowerCase()) || userMessage.contains(kw)) {
                    if (!keywords.contains(kw)) {
                        keywords.add(kw);
                    }
                }
            }
        }

        return keywords;
    }

    /**
     * Classify using keyword matching (fast classification).
     *
     * @param userMessage User problem description
     * @return IntentResult with classification result
     */
    private IntentResult classifyByKeywords(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        Map<String, Integer> matchCounts = new LinkedHashMap<>();
        Map<String, Double> confidenceScores = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : PROBLEM_KEYWORDS.entrySet()) {
            String problemType = entry.getKey();
            List<String> keywords = entry.getValue();
            int matches = 0;

            for (String kw : keywords) {
                if (lowerMessage.contains(kw.toLowerCase()) || userMessage.contains(kw)) {
                    matches++;
                }
            }

            matchCounts.put(problemType, matches);
            // Confidence based on keyword match ratio
            double confidence = matches > 0 ? (double) matches / keywords.size() : 0.0;
            confidenceScores.put(problemType, confidence);
        }

        // Find the best match
        String bestType = "UNKNOWN";
        double bestConfidence = 0.0;

        for (Map.Entry<String, Double> entry : confidenceScores.entrySet()) {
            if (entry.getValue() > bestConfidence) {
                bestConfidence = entry.getValue();
                bestType = entry.getKey();
            }
        }

        // Boost confidence if multiple keywords match
        if (matchCounts.getOrDefault(bestType, 0) >= 2) {
            bestConfidence = Math.min(bestConfidence * 1.5, 0.95);
        }

        return IntentResult.builder()
                .problemType(bestType)
                .confidence(bestConfidence)
                .classificationMethod("KEYWORD_MATCH")
                .build();
    }

    /**
     * Classify using Claude API (intelligent classification for complex cases).
     *
     * @param userMessage User problem description
     * @return IntentResult with classification result, or null if classification fails
     */
    private IntentResult classifyWithClaude(String userMessage) {
        log.debug("Using Claude for intelligent classification");

        try {
            // Build the classification prompt
            Map<String, String> params = new HashMap<>();
            params.put("user_problem", userMessage);
            params.put("supported_types", String.join(", ", PROBLEM_TYPES));

            String prompt = promptTemplateService.getTemplate(
                    PromptTemplateService.PROBLEM_CLASSIFIER, params);

            ClaudeResponse response = claudeService.sendMessage(prompt);
            String resultText = response.getTextContent();

            // Parse the Claude response
            return parseClaudeClassification(resultText);

        } catch (Exception e) {
            log.error("Claude classification failed", e);
            return null;
        }
    }

    /**
     * Parse Claude's classification response.
     *
     * @param responseText Claude response text
     * @return IntentResult parsed from response
     */
    private IntentResult parseClaudeClassification(String responseText) {
        try {
            // Try to parse JSON response
            if (responseText.contains("{")) {
                String jsonPart = extractJson(responseText);
                Map<String, Object> result = objectMapper.readValue(jsonPart,
                        new TypeReference<Map<String, Object>>() {});

                String problemType = getStringValue(result, "problemType", "problem_type", "type");
                double confidence = getDoubleValue(result, "confidence", "confidenceScore", 0.5);
                List<String> keywords = getListValue(result, "keywords");
                List<String> suggestedTools = getListValue(result, "suggestedTools");

                return IntentResult.builder()
                        .problemType(problemType != null ? problemType : "UNKNOWN")
                        .confidence(confidence)
                        .keywords(keywords)
                        .suggestedTools(suggestedTools)
                        .classificationMethod("CLAUDE_CLASSIFY")
                        .build();
            }

            // Fallback: parse text response
            for (String type : PROBLEM_TYPES) {
                if (responseText.contains(type)) {
                    return IntentResult.builder()
                            .problemType(type)
                            .confidence(0.7)
                            .classificationMethod("CLAUDE_CLASSIFY")
                            .build();
                }
            }

            return IntentResult.builder()
                    .problemType("UNKNOWN")
                    .confidence(0.3)
                    .classificationMethod("CLAUDE_CLASSIFY")
                    .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Claude classification response: {}", responseText, e);
            return IntentResult.builder()
                    .problemType("UNKNOWN")
                    .confidence(0.3)
                    .classificationMethod("CLAUDE_CLASSIFY")
                    .build();
        }
    }

    /**
     * Extract JSON from a potentially mixed content response.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * Get string value from map with multiple possible keys.
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    /**
     * Get double value from map with multiple possible keys.
     */
    private double getDoubleValue(Map<String, Object> map, String key, String altKey, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            value = map.get(altKey);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Get list value from map.
     */
    @SuppressWarnings("unchecked")
    private List<String> getListValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    /**
     * Get suggested tools for a problem type.
     *
     * @param problemType Problem type
     * @return List of suggested tool names
     */
    private List<String> getSuggestedTools(String problemType) {
        List<String> tools = TOOL_RECOMMENDATIONS.getOrDefault(problemType, Arrays.asList(
                "query_ops_platform", "query_logs", "search_wiki"
        ));

        // Filter tools that are actually available
        return tools.stream()
                .filter(tool -> toolRegistry.hasTool(tool))
                .toList();
    }

    /**
     * Build an unknown result for edge cases.
     *
     * @param reason Reason for unknown result
     * @return IntentResult with UNKNOWN type
     */
    private IntentResult buildUnknownResult(String reason) {
        return IntentResult.builder()
                .problemType("UNKNOWN")
                .confidence(0.0)
                .context(new HashMap<>())
                .keywords(new ArrayList<>())
                .suggestedTools(getSuggestedTools("UNKNOWN"))
                .originalMessage(reason)
                .classificationMethod("DEFAULT")
                .build();
    }

    /**
     * Get all supported problem types.
     *
     * @return List of supported problem types
     */
    public List<String> getSupportedProblemTypes() {
        return new ArrayList<>(PROBLEM_TYPES);
    }

    /**
     * Check if a problem type is supported.
     *
     * @param problemType Problem type to check
     * @return true if supported
     */
    public boolean isProblemTypeSupported(String problemType) {
        return PROBLEM_TYPES.contains(problemType);
    }
}