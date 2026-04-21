package com.drs.agent.service;

import com.drs.agent.config.ClaudeConfig;
import com.drs.agent.model.ClaudeMessage;
import com.drs.agent.model.ClaudeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DRS智能运维Agent服务
 * 提供问题诊断、根因分析、经验生成等核心能力
 */
@Service
public class DRSIntelligentAgentService {

    private static final Logger logger = LoggerFactory.getLogger(DRSIntelligentAgentService.class);

    private final ClaudeService claudeService;
    private final PromptTemplateService promptTemplateService;
    private final ClaudeConfig claudeConfig;

    public DRSIntelligentAgentService(ClaudeService claudeService,
                                       PromptTemplateService promptTemplateService,
                                       ClaudeConfig claudeConfig) {
        this.claudeService = claudeService;
        this.promptTemplateService = promptTemplateService;
        this.claudeConfig = claudeConfig;
    }

    /**
     * 问题分类
     *
     * @param userProblem 用户问题描述
     * @return 分类结果(JSON格式)
     */
    public String classifyProblem(String userProblem) {
        logger.info("Classifying problem: {}", userProblem);

        Map<String, String> params = new HashMap<>();
        params.put("user_problem", userProblem);

        String prompt = promptTemplateService.getTemplate(
                PromptTemplateService.PROBLEM_CLASSIFIER, params);

        ClaudeResponse response = claudeService.sendMessage(prompt);
        String result = response.getTextContent();

        logger.info("Problem classification result: {}", result);
        return result;
    }

    /**
     * 问题分类(异步)
     */
    public java.util.concurrent.CompletableFuture<String> classifyProblemAsync(String userProblem) {
        logger.info("Classifying problem (async): {}", userProblem);

        Map<String, String> params = new HashMap<>();
        params.put("user_problem", userProblem);

        String prompt = promptTemplateService.getTemplate(
                PromptTemplateService.PROBLEM_CLASSIFIER, params);

        return claudeService.sendMessageAsync(prompt)
                .thenApply(ClaudeResponse::getTextContent);
    }

    /**
     * 诊断编排
     *
     * @param problemType  问题类型
     * @param context       问题上下文
     * @param userProblem   用户原始描述
     * @return 诊断计划(JSON格式)
     */
    public String orchestrateDiagnosis(String problemType, String context, String userProblem) {
        logger.info("Orchestrating diagnosis for problem type: {}", problemType);

        Map<String, String> params = new HashMap<>();
        params.put("problem_type", problemType);
        params.put("context", context);
        params.put("user_problem", userProblem);

        String prompt = promptTemplateService.getTemplate(
                PromptTemplateService.DIAGNOSIS_ORCHESTRATOR, params);

        ClaudeResponse response = claudeService.sendMessage(prompt);
        String result = response.getTextContent();

        logger.info("Diagnosis orchestration completed");
        return result;
    }

    /**
     * 根因分析
     *
     * @param problemType         问题类型
     * @param context             问题上下文
     * @param userProblem         用户原始描述
     * @param diagnosisResults    诊断步骤结果
     * @param similarExperiences  相似历史经验
     * @return 根因分析结果(JSON格式)
     */
    public String analyzeRootCause(String problemType, String context, String userProblem,
                                    String diagnosisResults, String similarExperiences) {
        logger.info("Analyzing root cause for problem type: {}", problemType);

        Map<String, String> params = new HashMap<>();
        params.put("problem_type", problemType);
        params.put("context", context);
        params.put("user_problem", userProblem);
        params.put("diagnosis_results", diagnosisResults);
        params.put("similar_experiences", similarExperiences);

        String prompt = promptTemplateService.getTemplate(
                PromptTemplateService.ROOT_CAUSE_ANALYZER, params);

        ClaudeResponse response = claudeService.sendMessage(prompt);
        String result = response.getTextContent();

        logger.info("Root cause analysis completed");
        return result;
    }

    /**
     * 经验生成
     *
     * @param problemType       问题类型
     * @param userProblem       用户原始描述
     * @param diagnosisProcess  诊断过程
     * @param rootCauseResult   根因分析结果
     * @param solutionApplied   应用解决方案
     * @param verificationResult 验证结果
     * @return 经验知识(JSON格式)
     */
    public String generateExperience(String problemType, String userProblem,
                                       String diagnosisProcess, String rootCauseResult,
                                       String solutionApplied, String verificationResult) {
        logger.info("Generating experience for problem type: {}", problemType);

        Map<String, String> params = new HashMap<>();
        params.put("problem_type", problemType);
        params.put("user_problem", userProblem);
        params.put("diagnosis_process", diagnosisProcess);
        params.put("root_cause_result", rootCauseResult);
        params.put("solution_applied", solutionApplied);
        params.put("verification_result", verificationResult);

        String prompt = promptTemplateService.getTemplate(
                PromptTemplateService.EXPERIENCE_GENERATOR, params);

        ClaudeResponse response = claudeService.sendMessage(prompt);
        String result = response.getTextContent();

        logger.info("Experience generation completed");
        return result;
    }

    /**
     * 完整诊断流程
     *
     * @param userProblem 用户问题描述
     * @return 完整诊断结果
     */
    public DiagnosisResult fullDiagnosis(String userProblem) {
        logger.info("Starting full diagnosis for: {}", userProblem);

        DiagnosisResult result = new DiagnosisResult();
        result.setUserProblem(userProblem);

        // Step 1: 问题分类
        String classificationResult = classifyProblem(userProblem);
        result.setClassificationResult(classificationResult);

        // Step 2: 诊断编排(简化版,实际应解析分类结果获取problemType和context)
        String orchestrationResult = orchestrateDiagnosis("待解析", "{}", userProblem);
        result.setOrchestrationResult(orchestrationResult);

        // Step 3: 根因分析(简化版,实际应执行诊断步骤后获取diagnosisResults)
        String rootCauseResult = analyzeRootCause("待解析", "{}", userProblem, "[]", "[]");
        result.setRootCauseResult(rootCauseResult);

        logger.info("Full diagnosis completed");
        return result;
    }

    /**
     * 带历史对话的消息发送
     *
     * @param userMessage   用户消息
     * @param history       历史对话
     * @param templateName  使用的模板名称
     * @return 响应内容
     */
    public String sendMessageWithHistory(String userMessage, List<ClaudeMessage> history,
                                          String templateName) {
        Map<String, String> params = new HashMap<>();
        params.put("user_problem", userMessage);

        String systemPrompt = promptTemplateService.getTemplate(templateName, params);

        ClaudeResponse response = claudeService.sendMessagesWithHistory(userMessage, history, systemPrompt);
        return response.getTextContent();
    }

    /**
     * 诊断结果DTO
     */
    public static class DiagnosisResult {
        private String userProblem;
        private String classificationResult;
        private String orchestrationResult;
        private String rootCauseResult;

        public String getUserProblem() {
            return userProblem;
        }

        public void setUserProblem(String userProblem) {
            this.userProblem = userProblem;
        }

        public String getClassificationResult() {
            return classificationResult;
        }

        public void setClassificationResult(String classificationResult) {
            this.classificationResult = classificationResult;
        }

        public String getOrchestrationResult() {
            return orchestrationResult;
        }

        public void setOrchestrationResult(String orchestrationResult) {
            this.orchestrationResult = orchestrationResult;
        }

        public String getRootCauseResult() {
            return rootCauseResult;
        }

        public void setRootCauseResult(String rootCauseResult) {
            this.rootCauseResult = rootCauseResult;
        }
    }
}