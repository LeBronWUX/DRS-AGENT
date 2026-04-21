package com.drs.agent.service;

import com.drs.agent.model.AlertInfo;
import com.drs.agent.model.DiagnosisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Message Template Service
 *
 * Provides message templates for WeLink responses.
 * Supports different message formats: text, markdown, and card.
 */
@Service
public class MessageTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(MessageTemplateService.class);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Render diagnosis result as markdown message
     *
     * @param response Diagnosis response
     * @return Formatted markdown message
     */
    public String renderDiagnosisResult(DiagnosisResponse response) {
        StringBuilder sb = new StringBuilder();

        sb.append("## DRS智能诊断报告\n\n");

        // Status
        sb.append("**状态**: ");
        if ("COMPLETED".equals(response.getStatus())) {
            sb.append("诊断完成\n\n");
        } else {
            sb.append("诊断失败\n\n");
        }

        // Session ID
        if (response.getSessionId() != null) {
            sb.append("**会话ID**: `").append(response.getSessionId()).append("`\n\n");
        }

        // Problem type
        if (response.getProblemType() != null) {
            sb.append("**问题类型**: ").append(response.getProblemType()).append("\n\n");
        }

        // Root cause
        if (response.getRootCause() != null) {
            sb.append("### 根因分析\n");
            sb.append(response.getRootCause()).append("\n\n");
        }

        // Confidence
        if (response.getConfidence() != null) {
            sb.append("**置信度**: ").append(String.format("%.1f%%", response.getConfidence() * 100)).append("\n\n");
        }

        // Solution
        if (response.getSolution() != null) {
            sb.append("### 建议解决方案\n");
            sb.append(response.getSolution()).append("\n\n");
        }

        // Diagnosis chain
        if (response.getDiagnosisChain() != null && !response.getDiagnosisChain().isEmpty()) {
            sb.append("### 诊断链\n");
            int stepNum = 1;
            for (DiagnosisResponse.DiagnosisStep step : response.getDiagnosisChain()) {
                String status = "COMPLETED".equals(step.getStatus()) ? "success" : "failed";
                sb.append(stepNum++).append(". **").append(step.getStepName()).append("**");
                sb.append(" [").append(status).append("]\n");
                if (step.getResult() != null && !step.getResult().isEmpty()) {
                    sb.append("   - ").append(truncate(step.getResult(), 100)).append("\n");
                }
            }
            sb.append("\n");
        }

        // Similar experiences
        if (response.getSimilarExperiences() != null && !response.getSimilarExperiences().isEmpty()) {
            sb.append("### 相似经验\n");
            for (DiagnosisResponse.ExperienceMatch match : response.getSimilarExperiences()) {
                sb.append("- ").append(match.getSummary());
                sb.append(" (相似度: ").append(String.format("%.0f%%", match.getSimilarity() * 100)).append(")\n");
            }
            sb.append("\n");
        }

        // Footer
        sb.append("---\n");
        sb.append("_由DRS智能运维助手生成_ ").append(LocalDateTime.now().format(DATE_FORMATTER));

        return sb.toString();
    }

    /**
     * Render alert notification as markdown message
     *
     * @param alert Alert information
     * @return Formatted markdown message
     */
    public String renderAlertNotification(AlertInfo alert) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 告警通知\n\n");

        // Severity indicator
        String severityIcon = getSeverityIcon(alert.getSeverity());
        sb.append("**级别**: ").append(severityIcon).append(" ").append(alert.getSeverity()).append("\n\n");

        // Alert type
        sb.append("**类型**: ").append(alert.getAlertType()).append("\n\n");

        // Message
        if (alert.getMessage() != null) {
            sb.append("**详情**: ").append(alert.getMessage()).append("\n\n");
        }

        // Context
        if (alert.getWorkflowId() != null) {
            sb.append("**Workflow ID**: `").append(alert.getWorkflowId()).append("`\n\n");
        }
        if (alert.getTaskId() != null) {
            sb.append("**Task ID**: `").append(alert.getTaskId()).append("`\n\n");
        }
        if (alert.getServiceName() != null) {
            sb.append("**服务**: ").append(alert.getServiceName()).append("\n\n");
        }
        if (alert.getErrorCode() != null) {
            sb.append("**错误码**: ").append(alert.getErrorCode()).append("\n\n");
        }

        // Timestamp
        sb.append("**时间**: ").append(alert.getTimestamp()).append("\n\n");

        // Footer
        sb.append("---\n");
        sb.append("_自动诊断已触发，请稍候..._");

        return sb.toString();
    }

    /**
     * Render auto-diagnosis result for alert
     *
     * @param alert Alert information
     * @param response Diagnosis response
     * @return Formatted markdown message
     */
    public String renderAutoDiagnosisResult(AlertInfo alert, DiagnosisResponse response) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 自动诊断结果\n\n");

        // Alert reference
        sb.append("**告警ID**: `").append(alert.getAlertId()).append("`\n\n");

        // Diagnosis result
        sb.append(renderDiagnosisResult(response));

        return sb.toString();
    }

    /**
     * Render experience recommendation as markdown message
     *
     * @param title Recommendation title
     * @param summary Summary text
     * @param relevance Relevance score
     * @return Formatted markdown message
     */
    public String renderExperienceRecommendation(String title, String summary, double relevance) {
        StringBuilder sb = new StringBuilder();

        sb.append("### 经验推荐\n\n");
        sb.append("**").append(title).append("**\n\n");
        sb.append(summary).append("\n\n");
        sb.append("_相关性: ").append(String.format("%.0f%%", relevance * 100)).append("_\n");

        return sb.toString();
    }

    /**
     * Render error response
     *
     * @param errorMessage Error message
     * @return Formatted error message
     */
    public String renderErrorResponse(String errorMessage) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 错误\n\n");
        sb.append("处理请求时发生错误:\n\n");
        sb.append("```\n").append(errorMessage).append("\n```\n\n");
        sb.append("请稍后重试或联系管理员。\n\n");
        sb.append("---\n");
        sb.append("_DRS智能运维助手_");

        return sb.toString();
    }

    /**
     * Render info response
     *
     * @param message Info message
     * @return Formatted info message
     */
    public String renderInfoResponse(String message) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 提示\n\n");
        sb.append(message).append("\n\n");
        sb.append("---\n");
        sb.append("_DRS智能运维助手_");

        return sb.toString();
    }

    /**
     * Render unknown intent response
     *
     * @param originalMessage Original user message
     * @return Formatted response
     */
    public String renderUnknownIntentResponse(String originalMessage) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 无法理解您的请求\n\n");
        sb.append("抱歉，我无法理解您的请求:\n\n");
        sb.append("> ").append(truncate(originalMessage, 100)).append("\n\n");
        sb.append("您可以使用以下命令:\n\n");
        sb.append("- `诊断 <问题描述>` - 执行诊断\n");
        sb.append("- `诊断 <问题描述> workflowId=xxx` - 带Workflow ID的诊断\n");
        sb.append("- `帮助` - 查看帮助信息\n\n");
        sb.append("---\n");
        sb.append("_DRS智能运维助手_");

        return sb.toString();
    }

    /**
     * Render disabled response when WeLink is disabled
     *
     * @return Formatted disabled message
     */
    public String renderDisabledResponse() {
        return "WeLink集成尚未启用。请联系管理员进行配置。";
    }

    /**
     * Render help message
     *
     * @return Formatted help message
     */
    public String renderHelpMessage() {
        StringBuilder sb = new StringBuilder();

        sb.append("## DRS智能运维助手 使用指南\n\n");

        sb.append("### 诊断命令\n");
        sb.append("```\n");
        sb.append("诊断 <问题描述>\n");
        sb.append("诊断 <问题描述> workflowId=xxx\n");
        sb.append("诊断 <问题描述> workflowId=xxx taskId=yyy\n");
        sb.append("```\n\n");

        sb.append("### 支持的问题类型\n");
        sb.append("- 任务创建失败\n");
        sb.append("- 鉴权失败\n");
        sb.append("- 再编辑丢失对象\n");
        sb.append("- 增量同步失败\n");
        sb.append("- 全量迁移失败\n");
        sb.append("- 性能问题\n\n");

        sb.append("### 示例\n");
        sb.append("```\n");
        sb.append("诊断 任务创建失败 workflowId=123456\n");
        sb.append("诊断 鉴权失败\n");
        sb.append("诊断 增量同步失败 workflowId=789\n");
        sb.append("```\n\n");

        sb.append("### 其他命令\n");
        sb.append("- `帮助` - 显示此帮助信息\n");
        sb.append("- `查询` - 查询诊断历史 (开发中)\n");
        sb.append("- `反馈` - 提交反馈 (开发中)\n\n");

        sb.append("---\n");
        sb.append("_DRS智能运维助手_");

        return sb.toString();
    }

    /**
     * Render operation guide message
     *
     * @param operation Operation name
     * @param steps Operation steps
     * @return Formatted guide message
     */
    public String renderOperationGuide(String operation, List<String> steps) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 操作指南: ").append(operation).append("\n\n");
        sb.append("### 步骤\n\n");

        int stepNum = 1;
        for (String step : steps) {
            sb.append(stepNum++).append(". ").append(step).append("\n");
        }

        sb.append("\n---\n");
        sb.append("_DRS智能运维助手_");

        return sb.toString();
    }

    /**
     * Get severity icon
     */
    private String getSeverityIcon(String severity) {
        if (severity == null) {
            return ":information_source:";
        }
        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return ":red_circle:";
            case "HIGH":
                return ":orange_circle:";
            case "MEDIUM":
                return ":yellow_circle:";
            case "LOW":
                return ":green_circle:";
            default:
                return ":information_source:";
        }
    }

    /**
     * Truncate string to specified length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}