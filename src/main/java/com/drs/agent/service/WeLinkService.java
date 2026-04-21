package com.drs.agent.service;

import com.drs.agent.model.DiagnosisIntent;
import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.model.WeLinkMessage;
import com.drs.agent.model.WeLinkResponse;
import com.drs.agent.model.WeLinkUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WeLink Service
 *
 * Handles WeLink message processing and integration.
 * This is a Mock implementation - TODO: Replace with actual WeLink API calls.
 *
 * Features:
 * - Message handling and response generation
 * - Intent parsing for diagnosis requests
 * - Message encryption/decryption (WeLink security mechanism)
 * - User information retrieval
 * - Group message pushing
 */
@Service
public class WeLinkService {

    private static final Logger logger = LoggerFactory.getLogger(WeLinkService.class);

    @Value("${welink.app-id:}")
    private String appId;

    @Value("${welink.app-secret:}")
    private String appSecret;

    @Value("${welink.webhook-url:}")
    private String webhookUrl;

    @Value("${welink.push-group-id:}")
    private String pushGroupId;

    @Value("${welink.enabled:false}")
    private boolean enabled;

    private final DiagnosisService diagnosisService;
    private final MessageTemplateService messageTemplateService;
    private final ObjectMapper objectMapper;

    // Pattern for parsing diagnosis intent
    // Supports formats like: "诊断 任务创建失败 workflowId=xxx" or "帮我诊断鉴权失败"
    private static final Pattern DIAGNOSIS_PATTERN = Pattern.compile(
            "(?:诊断|diagnose|帮我诊断|请诊断)\\s+(.+?)(?:\\s+workflowId=(\\S+))?(?:\\s+taskId=(\\S+))?",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for extracting key-value pairs
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(\\w+)=(\\S+)");

    // Problem type keywords
    private static final Map<String, String> PROBLEM_KEYWORDS = new LinkedHashMap<>();
    static {
        PROBLEM_KEYWORDS.put("任务创建失败", "任务创建失败");
        PROBLEM_KEYWORDS.put("鉴权失败", "鉴权失败");
        PROBLEM_KEYWORDS.put("认证失败", "鉴权失败");
        PROBLEM_KEYWORDS.put("再编辑丢失对象", "再编辑丢失对象");
        PROBLEM_KEYWORDS.put("对象丢失", "再编辑丢失对象");
        PROBLEM_KEYWORDS.put("增量同步失败", "增量同步失败");
        PROBLEM_KEYWORDS.put("全量迁移失败", "全量迁移失败");
        PROBLEM_KEYWORDS.put("迁移失败", "全量迁移失败");
        PROBLEM_KEYWORDS.put("性能问题", "性能问题");
        PROBLEM_KEYWORDS.put("性能下降", "性能问题");
    }

    public WeLinkService(DiagnosisService diagnosisService,
                         MessageTemplateService messageTemplateService,
                         ObjectMapper objectMapper) {
        this.diagnosisService = diagnosisService;
        this.messageTemplateService = messageTemplateService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle WeLink message
     *
     * @param message WeLink message content
     * @return Response content
     */
    public String handleMessage(WeLinkMessage message) {
        logger.info("Handling WeLink message from user: {}, content: {}",
                message.getSenderId(), message.getContent());

        if (!enabled) {
            logger.warn("WeLink integration is disabled");
            return messageTemplateService.renderDisabledResponse();
        }

        try {
            // Parse user intent
            DiagnosisIntent intent = parseDiagnosisIntent(message.getContent());

            switch (intent.getIntentType()) {
                case DIAGNOSE:
                    return handleDiagnosisIntent(intent, message.getSenderId());
                case QUERY:
                    return handleQueryIntent(intent, message.getSenderId());
                case FEEDBACK:
                    return handleFeedbackIntent(intent, message.getSenderId());
                case HELP:
                    return messageTemplateService.renderHelpMessage();
                case UNKNOWN:
                default:
                    return messageTemplateService.renderUnknownIntentResponse(message.getContent());
            }
        } catch (Exception e) {
            logger.error("Error handling WeLink message", e);
            return messageTemplateService.renderErrorResponse("处理消息时发生错误: " + e.getMessage());
        }
    }

    /**
     * Parse diagnosis intent from message content
     * Supports formats: "诊断 任务创建失败 workflowId=xxx"
     *
     * @param content Message content
     * @return Parsed diagnosis intent
     */
    public DiagnosisIntent parseDiagnosisIntent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return DiagnosisIntent.unknown("Empty message");
        }

        String trimmedContent = content.trim();
        logger.debug("Parsing intent from: {}", trimmedContent);

        // Check for help request
        if (isHelpRequest(trimmedContent)) {
            return DiagnosisIntent.help(trimmedContent);
        }

        // Try to match diagnosis pattern
        Matcher matcher = DIAGNOSIS_PATTERN.matcher(trimmedContent);

        if (matcher.find()) {
            DiagnosisIntent.DiagnosisIntentBuilder builder = DiagnosisIntent.builder()
                    .intentType(DiagnosisIntent.IntentType.DIAGNOSE)
                    .originalMessage(trimmedContent);

            String problemPart = matcher.group(1);
            String workflowId = matcher.group(2);
            String taskId = matcher.group(3);

            // Extract problem type
            String problemType = extractProblemType(problemPart);
            builder.problemType(problemType);
            builder.problem(problemPart);

            if (workflowId != null) {
                builder.workflowId(workflowId);
            }
            if (taskId != null) {
                builder.taskId(taskId);
            }

            // Extract additional context (key-value pairs)
            Map<String, String> context = extractContext(trimmedContent);
            builder.context(context);

            // Extract keywords
            List<String> keywords = extractKeywords(problemPart);
            builder.keywords(keywords);

            builder.confidence(0.9);
            return builder.build();
        }

        // Check for query intent
        if (isQueryIntent(trimmedContent)) {
            return parseQueryIntent(trimmedContent);
        }

        // Check for feedback intent
        if (isFeedbackIntent(trimmedContent)) {
            return parseFeedbackIntent(trimmedContent);
        }

        // Try fuzzy match for diagnosis request
        if (containsDiagnosisKeywords(trimmedContent)) {
            return parseFuzzyDiagnosisIntent(trimmedContent);
        }

        return DiagnosisIntent.unknown(trimmedContent);
    }

    /**
     * Push message to WeLink group
     *
     * @param groupId Group ID
     * @param content Message content
     */
    public void pushToGroup(String groupId, String content) {
        if (!enabled) {
            logger.warn("WeLink integration is disabled, skipping group push");
            return;
        }

        if (groupId == null || groupId.isEmpty()) {
            groupId = pushGroupId;
        }

        if (groupId == null || groupId.isEmpty()) {
            logger.warn("No group ID configured for push");
            return;
        }

        logger.info("Pushing message to WeLink group: {}", groupId);

        // TODO: Implement actual WeLink API call
        // Mock implementation - simulates API call
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("groupId", groupId);
            requestBody.put("msgType", "markdown");
            requestBody.put("content", content);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            logger.debug("Mock WeLink API call - URL: {}, Body: {}", webhookUrl, jsonBody);

            // Simulate successful push
            logger.info("Message successfully pushed to group: {}", groupId);
        } catch (Exception e) {
            logger.error("Failed to push message to WeLink group", e);
        }
    }

    /**
     * Decrypt message (WeLink security mechanism)
     *
     * @param encryptedMsg Encrypted message
     * @return Decrypted message content
     */
    public String decryptMessage(String encryptedMsg) {
        // TODO: Implement actual WeLink decryption
        // Mock implementation - returns original message
        logger.debug("Mock decryption - returning original message");
        return encryptedMsg;
    }

    /**
     * Encrypt response (WeLink security mechanism)
     *
     * @param response Response content
     * @return Encrypted response
     */
    public String encryptResponse(String response) {
        // TODO: Implement actual WeLink encryption
        // Mock implementation - returns original response
        logger.debug("Mock encryption - returning original response");
        return response;
    }

    /**
     * Get user information from WeLink
     *
     * @param userId User ID
     * @return User information
     */
    public WeLinkUser getUserInfo(String userId) {
        // TODO: Implement actual WeLink API call
        // Mock implementation
        logger.debug("Mock getUserInfo for userId: {}", userId);
        return WeLinkUser.builder()
                .userId(userId)
                .name("User_" + userId)
                .email(userId + "@company.com")
                .department("Unknown")
                .build();
    }

    /**
     * Handle diagnosis intent
     */
    private String handleDiagnosisIntent(DiagnosisIntent intent, String userId) {
        logger.info("Handling diagnosis intent for problem: {}", intent.getProblem());

        try {
            // Create diagnosis request
            DiagnosisRequest request = intent.toDiagnosisRequest(userId);

            // Execute diagnosis
            DiagnosisResponse response = diagnosisService.diagnose(request);

            // Render result using template
            return messageTemplateService.renderDiagnosisResult(response);
        } catch (Exception e) {
            logger.error("Diagnosis failed", e);
            return messageTemplateService.renderErrorResponse("诊断失败: " + e.getMessage());
        }
    }

    /**
     * Handle query intent
     */
    private String handleQueryIntent(DiagnosisIntent intent, String userId) {
        // TODO: Implement query handling
        return messageTemplateService.renderInfoResponse(
                "查询功能正在开发中，请稍后再试。\n\n" +
                "您可以使用以下命令:\n" +
                "- 诊断 <问题描述> - 执行诊断\n" +
                "- 帮助 - 查看帮助信息"
        );
    }

    /**
     * Handle feedback intent
     */
    private String handleFeedbackIntent(DiagnosisIntent intent, String userId) {
        // TODO: Implement feedback handling
        return messageTemplateService.renderInfoResponse(
                "反馈功能正在开发中，请稍后再试。"
        );
    }

    /**
     * Check if message is a help request
     */
    private boolean isHelpRequest(String content) {
        String lower = content.toLowerCase();
        return lower.contains("帮助") || lower.contains("help") ||
               lower.equals("?") || lower.equals("？") ||
               lower.contains("怎么用") || lower.contains("使用说明");
    }

    /**
     * Check if message is a query intent
     */
    private boolean isQueryIntent(String content) {
        String lower = content.toLowerCase();
        return lower.contains("查询") || lower.contains("结果") ||
               lower.contains("历史") || lower.contains("状态") ||
               lower.contains("session");
    }

    /**
     * Check if message is a feedback intent
     */
    private boolean isFeedbackIntent(String content) {
        String lower = content.toLowerCase();
        return lower.contains("反馈") || lower.contains("评价") ||
               lower.contains("评分") || lower.contains("不对") ||
               lower.contains("错误") || lower.contains("纠正");
    }

    /**
     * Check if message contains diagnosis keywords
     */
    private boolean containsDiagnosisKeywords(String content) {
        String lower = content.toLowerCase();
        return lower.contains("失败") || lower.contains("错误") ||
               lower.contains("问题") || lower.contains("异常") ||
               lower.contains("error") || lower.contains("fail");
    }

    /**
     * Parse fuzzy diagnosis intent when pattern doesn't match exactly
     */
    private DiagnosisIntent parseFuzzyDiagnosisIntent(String content) {
        String problemType = extractProblemType(content);
        Map<String, String> context = extractContext(content);

        return DiagnosisIntent.builder()
                .intentType(DiagnosisIntent.IntentType.DIAGNOSE)
                .problem(content)
                .problemType(problemType)
                .context(context)
                .keywords(extractKeywords(content))
                .originalMessage(content)
                .confidence(0.6) // Lower confidence for fuzzy match
                .build();
    }

    /**
     * Parse query intent
     */
    private DiagnosisIntent parseQueryIntent(String content) {
        return DiagnosisIntent.builder()
                .intentType(DiagnosisIntent.IntentType.QUERY)
                .originalMessage(content)
                .confidence(0.8)
                .build();
    }

    /**
     * Parse feedback intent
     */
    private DiagnosisIntent parseFeedbackIntent(String content) {
        Map<String, String> context = extractContext(content);

        return DiagnosisIntent.builder()
                .intentType(DiagnosisIntent.IntentType.FEEDBACK)
                .context(context)
                .originalMessage(content)
                .confidence(0.7)
                .build();
    }

    /**
     * Extract problem type from content
     */
    private String extractProblemType(String content) {
        if (content == null) {
            return "UNKNOWN";
        }

        for (Map.Entry<String, String> entry : PROBLEM_KEYWORDS.entrySet()) {
            if (content.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "UNKNOWN";
    }

    /**
     * Extract context (key-value pairs) from content
     */
    private Map<String, String> extractContext(String content) {
        Map<String, String> context = new HashMap<>();
        Matcher matcher = KEY_VALUE_PATTERN.matcher(content);
        while (matcher.find()) {
            context.put(matcher.group(1), matcher.group(2));
        }
        return context;
    }

    /**
     * Extract keywords from content
     */
    private List<String> extractKeywords(String content) {
        List<String> keywords = new ArrayList<>();
        for (Map.Entry<String, String> entry : PROBLEM_KEYWORDS.entrySet()) {
            if (content.contains(entry.getKey())) {
                keywords.add(entry.getKey());
            }
        }
        return keywords;
    }

    /**
     * Check if WeLink integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get push group ID
     */
    public String getPushGroupId() {
        return pushGroupId;
    }
}