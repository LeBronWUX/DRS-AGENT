package com.drs.agent.controller;

import com.drs.agent.model.AlertInfo;
import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.service.AlertParser;
import com.drs.agent.service.DiagnosisService;
import com.drs.agent.service.MessageTemplateService;
import com.drs.agent.service.WeLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Alert Webhook Controller
 *
 * Handles alert webhooks from monitoring systems and triggers automatic diagnosis.
 *
 * Features:
 * - POST /v1/webhooks/alerts - Receive alerts and trigger auto-diagnosis
 * - POST /v1/webhooks/alerts/custom - Custom format alert parsing
 * - GET /v1/webhooks/alerts/health - Health check for alert webhook
 *
 * Alert format example:
 * {
 *   "alertId": "alert_001",
 *   "alertType": "任务创建失败",
 *   "severity": "high",
 *   "workflowId": "wf_123",
 *   "message": "任务创建失败...",
 *   "timestamp": "2026-04-21T10:00:00"
 * }
 *
 * Auto-diagnosis triggers for high severity alerts and pushes results to WeLink group.
 */
@RestController
@RequestMapping("/v1/webhooks/alerts")
public class AlertWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(AlertWebhookController.class);

    private final AlertParser alertParser;
    private final DiagnosisService diagnosisService;
    private final WeLinkService weLinkService;
    private final MessageTemplateService messageTemplateService;

    public AlertWebhookController(AlertParser alertParser,
                                   DiagnosisService diagnosisService,
                                   WeLinkService weLinkService,
                                   MessageTemplateService messageTemplateService) {
        this.alertParser = alertParser;
        this.diagnosisService = diagnosisService;
        this.weLinkService = weLinkService;
        this.messageTemplateService = messageTemplateService;
    }

    /**
     * Handle alert webhook and trigger auto-diagnosis
     *
     * This endpoint receives alerts from monitoring systems.
     * For high severity alerts, it automatically triggers diagnosis
     * and pushes results to WeLink group.
     *
     * Request format:
     * {
     *   "alertId": "alert_001",
     *   "alertType": "任务创建失败",
     *   "severity": "high",
     *   "workflowId": "wf_123",
     *   "message": "任务创建失败...",
     *   "timestamp": "2026-04-21T10:00:00"
     * }
     *
     * @param alertRequest Alert content (JSON or plain text)
     * @return Response with diagnosis result or acknowledgment
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleAlert(@RequestBody Map<String, Object> alertRequest) {
        logger.info("Received alert webhook: {}", alertRequest);

        try {
            // Convert to JSON string for parsing
            String alertContent = convertToJsonString(alertRequest);

            // Parse alert
            AlertInfo alert = alertParser.parse(alertContent);
            logger.info("Parsed alert: id={}, type={}, severity={}, workflowId={}",
                    alert.getAlertId(),
                    alert.getAlertType(),
                    alert.getSeverity(),
                    alert.getWorkflowId());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("alertId", alert.getAlertId());
            response.put("alertType", alert.getAlertType());
            response.put("severity", alert.getSeverity());
            response.put("received", true);
            response.put("timestamp", System.currentTimeMillis());

            // Check if auto-diagnosis should be triggered
            if (alertParser.shouldAutoDiagnose(alert)) {
                logger.info("Triggering auto-diagnosis for alert: {}", alert.getAlertId());

                // Push alert notification to WeLink group first
                pushAlertNotification(alert);

                // Trigger diagnosis
                DiagnosisResponse diagnosisResult = triggerAutoDiagnosis(alert);

                // Push diagnosis result to WeLink group
                pushDiagnosisResult(alert, diagnosisResult);

                response.put("autoDiagnosed", true);
                response.put("diagnosisSessionId", diagnosisResult.getSessionId());
                response.put("diagnosisStatus", diagnosisResult.getStatus());
                response.put("rootCause", diagnosisResult.getRootCause());
                response.put("confidence", diagnosisResult.getConfidence());
            } else {
                logger.info("Alert {} does not meet auto-diagnosis criteria", alert.getAlertId());
                response.put("autoDiagnosed", false);
                response.put("reason", "Severity too low or alert type not supported for auto-diagnosis");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing alert webhook", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("received", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Handle alert with custom format
     *
     * This endpoint allows specifying the alert format for parsing.
     * Supported formats: json, prometheus, grafana, zabbix
     *
     * @param format Alert format name
     * @param alertRequest Alert content
     * @return Response with parsed alert info
     */
    @PostMapping("/custom")
    public ResponseEntity<Map<String, Object>> handleCustomFormatAlert(
            @RequestParam String format,
            @RequestBody Map<String, Object> alertRequest) {

        logger.info("Received custom format alert: format={}, content={}", format, alertRequest);

        try {
            String alertContent = convertToJsonString(alertRequest);

            // Parse with specified format
            AlertInfo alert = alertParser.parseCustomFormat(format, alertContent);

            Map<String, Object> response = new HashMap<>();
            response.put("alertId", alert.getAlertId());
            response.put("alertType", alert.getAlertType());
            response.put("severity", alert.getSeverity());
            response.put("parsed", true);
            response.put("format", format);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error parsing custom format alert", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("parsed", false);
            errorResponse.put("format", format);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Handle raw text alert
     *
     * This endpoint accepts plain text alerts.
     *
     * @param alertText Plain text alert content
     * @return Response with parsed alert and diagnosis result
     */
    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> handleTextAlert(
            @RequestBody String alertText) {

        logger.info("Received text alert: {}", truncate(alertText, 200));

        try {
            // Parse plain text alert
            AlertInfo alert = alertParser.parse(alertText);

            Map<String, Object> response = new HashMap<>();
            response.put("alertId", alert.getAlertId());
            response.put("alertType", alert.getAlertType());
            response.put("severity", alert.getSeverity());
            response.put("workflowId", alert.getWorkflowId());
            response.put("received", true);
            response.put("timestamp", System.currentTimeMillis());

            // Check for auto-diagnosis
            if (alertParser.shouldAutoDiagnose(alert)) {
                DiagnosisResponse diagnosisResult = triggerAutoDiagnosis(alert);
                response.put("autoDiagnosed", true);
                response.put("diagnosisSessionId", diagnosisResult.getSessionId());
                response.put("rootCause", diagnosisResult.getRootCause());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing text alert", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("received", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Health check for alert webhook
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("weLinkEnabled", weLinkService.isEnabled());
        health.put("timestamp", System.currentTimeMillis());
        health.put("supportedFormats", new String[]{"json", "prometheus", "grafana", "zabbix", "text"});

        return ResponseEntity.ok(health);
    }

    /**
     * Get auto-diagnosis configuration
     *
     * @return Auto-diagnosis settings
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAutoDiagnosisConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("autoDiagnoseEnabled", true);
        config.put("minimumSeverity", "HIGH");
        config.put("supportedAlertTypes", new String[]{
                "任务创建失败",
                "鉴权失败",
                "再编辑丢失对象",
                "增量同步失败",
                "全量迁移失败"
        });
        config.put("weLinkPushEnabled", weLinkService.isEnabled());
        config.put("weLinkGroupId", weLinkService.getPushGroupId());

        return ResponseEntity.ok(config);
    }

    /**
     * Trigger auto-diagnosis for alert
     */
    private DiagnosisResponse triggerAutoDiagnosis(AlertInfo alert) {
        // Build diagnosis request from alert
        StringBuilder problemBuilder = new StringBuilder();
        problemBuilder.append(alert.getAlertType());
        if (alert.getWorkflowId() != null) {
            problemBuilder.append(" workflowId=").append(alert.getWorkflowId());
        }
        if (alert.getTaskId() != null) {
            problemBuilder.append(" taskId=").append(alert.getTaskId());
        }
        if (alert.getMessage() != null) {
            problemBuilder.append(" ").append(alert.getMessage());
        }

        DiagnosisRequest request = DiagnosisRequest.builder()
                .userId("auto-diagnosis-system")
                .problem(problemBuilder.toString())
                .context(alert.getContext())
                .priority(alert.isHighPriority() ? "HIGH" : "MEDIUM")
                .build();

        // Execute diagnosis
        return diagnosisService.diagnose(request);
    }

    /**
     * Push alert notification to WeLink group
     */
    private void pushAlertNotification(AlertInfo alert) {
        if (!weLinkService.isEnabled()) {
            logger.warn("WeLink not enabled, skipping notification push");
            return;
        }

        String notification = messageTemplateService.renderAlertNotification(alert);
        weLinkService.pushToGroup(weLinkService.getPushGroupId(), notification);
    }

    /**
     * Push diagnosis result to WeLink group
     */
    private void pushDiagnosisResult(AlertInfo alert, DiagnosisResponse response) {
        if (!weLinkService.isEnabled()) {
            logger.warn("WeLink not enabled, skipping result push");
            return;
        }

        String resultMessage = messageTemplateService.renderAutoDiagnosisResult(alert, response);
        weLinkService.pushToGroup(weLinkService.getPushGroupId(), resultMessage);
    }

    /**
     * Convert map to JSON string
     */
    private String convertToJsonString(Map<String, Object> map) {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    sb.append(value);
                } else if (value != null) {
                    sb.append("\"").append(value.toString()).append("\"");
                } else {
                    sb.append("null");
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to convert map to JSON string", e);
            return "{}";
        }
    }

    /**
     * Truncate string for logging
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}