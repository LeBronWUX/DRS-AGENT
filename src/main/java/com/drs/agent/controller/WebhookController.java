package com.drs.agent.controller;

import com.drs.agent.model.WeLinkMessage;
import com.drs.agent.model.WeLinkResponse;
import com.drs.agent.service.WeLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhook Controller
 *
 * Handles WeLink webhook callbacks and message events.
 * This controller provides endpoints for:
 * - POST /v1/webhooks/welink - WeLink message callback
 * - GET /v1/webhooks/welink/callback - Callback URL registration
 *
 * TODO: Replace mock implementations with actual WeLink API integration.
 */
@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WeLinkService weLinkService;

    public WebhookController(WeLinkService weLinkService) {
        this.weLinkService = weLinkService;
    }

    /**
     * Handle WeLink message callback
     *
     * This endpoint receives messages from WeLink bot and processes them.
     * Request format:
     * {
     *   "msgId": "xxx",
     *   "content": "帮我诊断任务创建失败 workflowId=123",
     *   "senderId": "user_001",
     *   "timestamp": 1234567890
     * }
     *
     * Response format:
     * {
     *   "msgId": "xxx",
     *   "content": "诊断结果:\n根因: 权限不足\n建议: 检查账号权限\n置信度: 92%"
     * }
     *
     * @param message WeLink message content
     * @return Response with diagnosis result or info message
     */
    @PostMapping("/welink")
    public ResponseEntity<WeLinkResponse> handleWeLinkMessage(@RequestBody WeLinkMessage message) {
        logger.info("Received WeLink message: msgId={}, sender={}, content={}",
                message.getMsgId(),
                message.getSenderId(),
                truncateContent(message.getContent(), 100));

        // Validate required fields
        if (message.getMsgId() == null || message.getMsgId().isEmpty()) {
            logger.warn("Message missing msgId");
            return ResponseEntity.badRequest().body(
                    WeLinkResponse.errorResponse(null, "Missing msgId")
            );
        }

        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            logger.warn("Message missing content");
            return ResponseEntity.badRequest().body(
                    WeLinkResponse.errorResponse(message.getMsgId(), "Missing content")
            );
        }

        // Check if WeLink is enabled
        if (!weLinkService.isEnabled()) {
            logger.warn("WeLink integration is disabled");
            return ResponseEntity.ok(
                    WeLinkResponse.textResponse(message.getMsgId(), "WeLink集成尚未启用")
            );
        }

        try {
            // Process message through WeLinkService
            String responseContent = weLinkService.handleMessage(message);

            // Build response
            WeLinkResponse response = WeLinkResponse.markdownResponse(
                    message.getMsgId(),
                    responseContent
            );

            logger.info("Successfully processed WeLink message: msgId={}", message.getMsgId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing WeLink message", e);
            return ResponseEntity.internalServerError().body(
                    WeLinkResponse.errorResponse(message.getMsgId(), "Internal server error: " + e.getMessage())
            );
        }
    }

    /**
     * Handle WeLink message callback with raw JSON
     *
     * Alternative endpoint that accepts raw JSON format from WeLink.
     *
     * @param rawMessage Raw message map
     * @return Response with diagnosis result
     */
    @PostMapping("/welink/raw")
    public ResponseEntity<Map<String, Object>> handleWeLinkRawMessage(
            @RequestBody Map<String, Object> rawMessage) {

        logger.info("Received WeLink raw message: {}", rawMessage);

        try {
            // Convert raw message to WeLinkMessage
            WeLinkMessage message = convertRawToWeLinkMessage(rawMessage);

            // Process message
            String responseContent = weLinkService.handleMessage(message);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("msgId", message.getMsgId());
            response.put("content", responseContent);
            response.put("msgType", "markdown");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing raw WeLink message", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("errorMessage", e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Callback URL registration endpoint
     *
     * This endpoint is used by WeLink to verify the webhook callback URL.
     * Returns configuration information.
     *
     * @return Configuration and status information
     */
    @GetMapping("/welink/callback")
    public ResponseEntity<Map<String, Object>> getCallbackInfo() {
        logger.info("WeLink callback info requested");

        Map<String, Object> info = new HashMap<>();
        info.put("status", "active");
        info.put("service", "DRS Intelligent Agent");
        info.put("version", "1.0.0");
        info.put("enabled", weLinkService.isEnabled());
        info.put("supportedCommands", new String[]{
                "诊断 <问题描述>",
                "诊断 <问题描述> workflowId=xxx",
                "帮助"
        });
        info.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(info);
    }

    /**
     * Health check for webhook endpoint
     *
     * @return Health status
     */
    @GetMapping("/welink/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("weLinkEnabled", weLinkService.isEnabled());
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
    }

    /**
     * Webhook verification endpoint (for WeLink security verification)
     *
     * WeLink may send verification requests when registering webhook URL.
     *
     * @param challenge Verification challenge string
     * @return Challenge response
     */
    @GetMapping("/welink/verify")
    public ResponseEntity<Map<String, Object>> verifyWebhook(
            @RequestParam(required = false) String challenge) {

        logger.info("Webhook verification requested with challenge: {}", challenge);

        Map<String, Object> response = new HashMap<>();
        response.put("challenge", challenge != null ? challenge : "verified");
        response.put("success", true);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Convert raw message map to WeLinkMessage
     */
    private WeLinkMessage convertRawToWeLinkMessage(Map<String, Object> rawMessage) {
        return WeLinkMessage.builder()
                .msgId(getStringValue(rawMessage, "msgId", "msg_id", "id"))
                .content(getStringValue(rawMessage, "content", "msg", "text"))
                .senderId(getStringValue(rawMessage, "senderId", "sender_id", "fromUserId", "from"))
                .senderName(getStringValue(rawMessage, "senderName", "sender_name", "name"))
                .timestamp(getLongValue(rawMessage, "timestamp", "time", "createTime"))
                .chatId(getStringValue(rawMessage, "chatId", "chat_id", "groupId"))
                .msgType(getStringValue(rawMessage, "msgType", "msg_type", "type"))
                .attributes(rawMessage)
                .build();
    }

    /**
     * Get string value from map with multiple possible keys
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Get long value from map with multiple possible keys
     */
    private Long getLongValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse timestamp value: {}", value);
                }
            }
        }
        return null;
    }

    /**
     * Truncate content for logging
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "null";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}