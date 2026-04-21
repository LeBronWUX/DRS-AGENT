package com.drs.agent.service;

import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.model.Experience;
import com.drs.agent.repository.DiagnosisSessionRepository;
import com.drs.agent.model.DiagnosisSession;
import com.drs.agent.model.ExperienceMatch;
import com.drs.agent.model.RetrievalResult;
import com.drs.agent.service.dto.*;
import com.drs.agent.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Streaming Diagnosis Service
 *
 * Provides real-time diagnosis process output via SSE.
 * Each step emits events to show thinking process to users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingDiagnosisService {

    private final IntentRecognizer intentRecognizer;
    private final DiagnosisOrchestrator orchestrator;
    private final ExperienceRetriever experienceRetriever;
    private final RootCauseAnalyzer rootCauseAnalyzer;
    private final DiagnosisSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Execute diagnosis with real-time SSE streaming.
     *
     * @param request Diagnosis request
     * @param emitter SSE emitter to send events
     */
    public void streamDiagnosisSse(DiagnosisRequest request, SseEmitter emitter) {
        executor.submit(() -> {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = IdGenerator.generateSessionId();
            }

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
                // Emit initial event
                sendEvent(emitter, "START", sessionId, 0, "开始诊断",
                        "正在分析您的问题描述...", null, null, null, "RUNNING", null, null, null, null, null);

                // Thinking event
                sendEvent(emitter, "THINKING", sessionId, 0, "理解问题",
                        "正在理解问题描述，识别关键信息...",
                        "分析关键词: " + extractKeywords(request.getProblem()), null, null, "RUNNING", null, null, null, null, null);

                // Step 1: Intent Recognition
                sendEvent(emitter, "STEP_START", sessionId, 1, "问题分类",
                        "使用AI模型识别问题类型...", null, "调用问题分类器", null, "RUNNING", null, null, null, null, null);

                IntentResult intentResult = intentRecognizer.recognize(request.getProblem());
                String problemType = intentResult.getProblemType();

                sendEvent(emitter, "STEP_COMPLETE", sessionId, 1, "问题分类",
                        "问题分类完成",
                        "识别结果: " + problemType + " (置信度: " + intentResult.getConfidence() + ")",
                        "问题分类", problemType, "SUCCESS", null, null, null, null, null);

                // Step 2: Experience Retrieval
                sendEvent(emitter, "STEP_START", sessionId, 2, "经验检索",
                        "从知识库检索相似的历史经验...", null, "搜索经验库", null, "RUNNING", null, null, null, null, null);

                com.drs.agent.model.IntentResult modelIntent = intentResult.toModelIntentResult();
                RetrievalResult retrievalResult = experienceRetriever.search(modelIntent);
                List<Experience> similarExperiences = retrievalResult.getMatches().stream()
                        .map(ExperienceMatch::getExperience)
                        .collect(Collectors.toList());

                sendEvent(emitter, "STEP_COMPLETE", sessionId, 2, "经验检索",
                        "找到 " + similarExperiences.size() + " 条相似经验",
                        "最高相似度: " + retrievalResult.getMaxSimilarity() +
                        ", 相关经验类型: " + similarExperiences.stream()
                                .map(Experience::getProblemType)
                                .distinct()
                                .collect(Collectors.joining(", ")),
                        "经验检索", "找到" + similarExperiences.size() + "条", "SUCCESS", null, null, null, null, null);

                // Step 3: Diagnosis Chain Execution
                sendEvent(emitter, "STEP_START", sessionId, 3, "诊断链执行",
                        "开始执行诊断步骤链...", null, "执行诊断步骤", null, "RUNNING", null, null, null, null, null);

                DiagnosisChainResult chainResult = orchestrator.executeChain(intentResult);

                // Emit each step result
                int stepIndex = 0;
                for (StepResult stepResult : chainResult.getStepResults()) {
                    sendEvent(emitter, "CHAIN_STEP", sessionId, stepIndex + 4, stepResult.getStepName(),
                            stepResult.getAction(),
                            "工具: " + stepResult.getToolName() +
                            ", 输出: " + truncate(stepResult.getRawOutput(), 200),
                            stepResult.getAction(),
                            stepResult.isSuccess() ? "成功" : "失败: " + stepResult.getError(),
                            stepResult.isSuccess() ? "SUCCESS" : "FAILED",
                            stepResult.getExecutionTime(), null, null, null, null);
                    stepIndex++;
                }

                // Step 4: Root Cause Analysis
                sendEvent(emitter, "STEP_START", sessionId, stepIndex + 5, "根因分析",
                        "综合分析诊断结果和历史经验，识别根本原因...", null, "AI根因分析", null, "RUNNING", null, null, null, null, null);

                RootCauseResult rootCauseResult = rootCauseAnalyzer.analyze(chainResult, similarExperiences, intentResult);

                sendEvent(emitter, "ANALYSIS", sessionId, stepIndex + 5, "根因分析",
                        "根因分析完成",
                        "根因类别: " + rootCauseResult.getCategory() +
                        "\n根因描述: " + rootCauseResult.getDescription() +
                        "\n置信度: " + rootCauseResult.getConfidence(),
                        "根因分析", rootCauseResult.getDescription(), "SUCCESS", null,
                        rootCauseResult.getConfidence(), rootCauseResult.getDescription(), null, null);

                // Step 5: Solution Generation
                sendEvent(emitter, "STEP_START", sessionId, stepIndex + 6, "方案生成",
                        "根据根因分析结果，生成解决方案...", null, "生成解决方案", null, "RUNNING", null, null, null, null, null);

                String solution = buildSolutionString(rootCauseResult);

                sendEvent(emitter, "STEP_COMPLETE", sessionId, stepIndex + 6, "方案生成",
                        "解决方案已生成",
                        solution,
                        "方案生成", solution, "SUCCESS", null, null, null, null, null);

                // Final result
                session.setProblemType(problemType);
                session.setRootCause(rootCauseResult.getDescription());
                session.setConfidenceScore(rootCauseResult.getConfidence());
                session.setSolution(solution);
                session.setStatus("COMPLETED");
                session.setCompletedAt(LocalDateTime.now());
                sessionRepository.save(session);

                // Build complete response
                DiagnosisResponse response = DiagnosisResponse.builder()
                        .sessionId(sessionId)
                        .problemType(problemType)
                        .rootCause(rootCauseResult.getDescription())
                        .confidence(rootCauseResult.getConfidence())
                        .solution(solution)
                        .status("COMPLETED")
                        .build();

                String finalResult = objectMapper.writeValueAsString(response);
                sendEvent(emitter, "RESULT", sessionId, null, "诊断完成",
                        "诊断已完成，请查看结果", null, null, null, "COMPLETED", null,
                        rootCauseResult.getConfidence(), rootCauseResult.getDescription(), solution, finalResult);

                emitter.complete();

            } catch (Exception e) {
                log.error("Streaming diagnosis failed", e);
                try {
                    session.setStatus("FAILED");
                    session.setRootCause("诊断失败: " + e.getMessage());
                    sessionRepository.save(session);

                    sendEvent(emitter, "ERROR", sessionId, 0, "诊断失败",
                            "诊断过程中发生错误", e.getMessage(), null, e.getMessage(), "FAILED", null, null, null, null, null);
                } catch (Exception ioException) {
                    log.error("Failed to send error event", ioException);
                }
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Failed to complete emitter with error", ex);
                }
            }
        });
    }

    /**
     * Send SSE event.
     */
    private void sendEvent(SseEmitter emitter, String eventType, String sessionId, Integer stepNumber,
                           String stepName, String description, String thinking,
                           String action, String result, String status, Long executionTimeMs,
                           Double confidence, String rootCause, String solution, String finalResult) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("sessionId", sessionId);
            if (stepNumber != null) event.put("stepNumber", stepNumber);
            event.put("stepName", stepName);
            event.put("description", description);
            if (thinking != null) event.put("thinking", thinking);
            if (action != null) event.put("action", action);
            if (result != null) event.put("result", result);
            event.put("status", status);
            if (executionTimeMs != null) event.put("executionTimeMs", executionTimeMs);
            if (confidence != null) event.put("confidence", confidence);
            if (rootCause != null) event.put("rootCause", rootCause);
            if (solution != null) event.put("solution", solution);
            if (finalResult != null) event.put("finalResult", finalResult);

            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.error("Failed to send SSE event: {}", e.getMessage());
        }
    }

    /**
     * Build solution string from RootCauseResult.
     */
    private String buildSolutionString(RootCauseResult result) {
        if (result.getSolution() != null) {
            Solution solution = result.getSolution();
            StringBuilder sb = new StringBuilder();
            if (solution.getImmediateAction() != null) {
                sb.append("立即行动: ").append(solution.getImmediateAction()).append("\n");
            }
            if (solution.getLongTermFix() != null) {
                sb.append("长期修复: ").append(solution.getLongTermFix());
            }
            return sb.toString();
        }
        return result.getDescription();
    }

    /**
     * Extract keywords from problem description.
     */
    private String extractKeywords(String problem) {
        if (problem == null) return "";
        String[] words = problem.split("[\\s,，。.!！?？;；:：]+");
        return String.join(", ", words.length > 5 ?
                java.util.Arrays.copyOfRange(words, 0, 5) : words);
    }

    /**
     * Truncate string to specified length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}