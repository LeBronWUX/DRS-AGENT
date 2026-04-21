package com.drs.agent.service;

import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.ClaudeResponse;
import com.drs.agent.model.Experience;
import com.drs.agent.repository.ExperienceRepository;
import com.drs.agent.service.dto.DiagnosisChainResult;
import com.drs.agent.service.dto.DiagnosisStep;
import com.drs.agent.service.dto.IntentResult;
import com.drs.agent.service.dto.StepResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Diagnosis Orchestrator
 *
 * Orchestrates and executes diagnosis chains based on problem type and experience.
 * Supports experience-based chain templates and Claude-generated chains for novel problems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisOrchestrator {

    private final McpToolRegistry toolRegistry;
    private final IntentRecognizer intentRecognizer;
    private final ClaudeService claudeService;
    private final PromptTemplateService promptTemplateService;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;

    // Default timeout for each step
    private static final long DEFAULT_STEP_TIMEOUT_MS = 30000;

    // Maximum number of steps in a chain
    private static final int MAX_CHAIN_STEPS = 10;

    // Default diagnosis chain templates for each problem type
    private static final Map<String, List<DiagnosisStep>> DEFAULT_CHAIN_TEMPLATES = new LinkedHashMap<>();

    static {
        DEFAULT_CHAIN_TEMPLATES.put("任务创建失败", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询任务信息")
                        .action("Query task information from operations platform")
                        .tool("query_ops_platform")
                        .params(Map.of("taskId", "${taskId}", "queryType", "task_detail"))
                        .expectedOutput("Task details including status and error messages")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询相关日志")
                        .action("Query logs related to the task creation")
                        .tool("query_logs")
                        .params(Map.of("service", "${service}", "traceId", "${taskId}", "keywords", "error,failed,creation"))
                        .expectedOutput("Log entries showing error details")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("获取告警上下文")
                        .action("Get alert context if available")
                        .tool("get_alert_context")
                        .params(Map.of("alertId", "${alertId}"))
                        .expectedOutput("Alert context with related events")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .fallback("Skip if no alert ID")
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(4)
                        .stepName("搜索知识库")
                        .action("Search wiki for similar issues")
                        .tool("search_wiki")
                        .params(Map.of("query", "任务创建失败 ${error_code}", "limit", "5"))
                        .expectedOutput("Wiki documents with solutions")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));

        DEFAULT_CHAIN_TEMPLATES.put("鉴权失败", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询用户信息")
                        .action("Query user authentication status")
                        .tool("query_ops_platform")
                        .params(Map.of("userId", "${userId}", "queryType", "user_auth"))
                        .expectedOutput("User authentication details")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询鉴权日志")
                        .action("Query authentication logs")
                        .tool("query_logs")
                        .params(Map.of("service", "auth-service", "keywords", "auth,failed,401,403"))
                        .expectedOutput("Authentication failure logs")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("搜索解决方案")
                        .action("Search wiki for authentication solutions")
                        .tool("search_wiki")
                        .params(Map.of("query", "鉴权失败 认证错误", "limit", "5"))
                        .expectedOutput("Authentication troubleshooting guides")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));

        DEFAULT_CHAIN_TEMPLATES.put("再编辑丢失对象", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询对象信息")
                        .action("Query object information")
                        .tool("query_ops_platform")
                        .params(Map.of("objectId", "${objectId}", "queryType", "object_detail"))
                        .expectedOutput("Object details and status")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询编辑日志")
                        .action("Query edit operation logs")
                        .tool("query_logs")
                        .params(Map.of("service", "${service}", "keywords", "edit,missing,lost,object"))
                        .expectedOutput("Logs showing edit operations")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("搜索类似案例")
                        .action("Search wiki for similar missing object cases")
                        .tool("search_wiki")
                        .params(Map.of("query", "再编辑 丢失对象 数据丢失", "limit", "5"))
                        .expectedOutput("Wiki documents with solutions")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));

        DEFAULT_CHAIN_TEMPLATES.put("增量同步失败", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询同步任务")
                        .action("Query sync task details")
                        .tool("query_ops_platform")
                        .params(Map.of("taskId", "${taskId}", "queryType", "sync_task"))
                        .expectedOutput("Sync task status and details")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询同步日志")
                        .action("Query sync operation logs")
                        .tool("query_logs")
                        .params(Map.of("service", "${service}", "traceId", "${taskId}", "keywords", "sync,incremental,failed"))
                        .expectedOutput("Sync failure logs")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("获取告警信息")
                        .action("Get alert context")
                        .tool("get_alert_context")
                        .params(Map.of("alertId", "${alertId}"))
                        .expectedOutput("Alert details and related events")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(4)
                        .stepName("搜索同步问题解决方案")
                        .action("Search wiki for sync solutions")
                        .tool("search_wiki")
                        .params(Map.of("query", "增量同步失败 数据同步错误", "limit", "5"))
                        .expectedOutput("Sync troubleshooting guides")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));

        DEFAULT_CHAIN_TEMPLATES.put("全量迁移失败", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询迁移任务")
                        .action("Query migration task details")
                        .tool("query_ops_platform")
                        .params(Map.of("taskId", "${taskId}", "queryType", "migration_task"))
                        .expectedOutput("Migration task status")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询迁移日志")
                        .action("Query migration logs")
                        .tool("query_logs")
                        .params(Map.of("service", "${service}", "traceId", "${taskId}", "keywords", "migration,failed,error"))
                        .expectedOutput("Migration failure logs")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("获取告警上下文")
                        .action("Get alert context")
                        .tool("get_alert_context")
                        .params(Map.of("alertId", "${alertId}"))
                        .expectedOutput("Alert details")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(4)
                        .stepName("搜索迁移解决方案")
                        .action("Search wiki for migration solutions")
                        .tool("search_wiki")
                        .params(Map.of("query", "全量迁移失败 数据迁移错误", "limit", "5"))
                        .expectedOutput("Migration troubleshooting guides")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));

        DEFAULT_CHAIN_TEMPLATES.put("性能问题", Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询性能指标")
                        .action("Query performance metrics")
                        .tool("query_ops_platform")
                        .params(Map.of("service", "${service}", "queryType", "performance"))
                        .expectedOutput("Performance metrics and trends")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询慢日志")
                        .action("Query slow operation logs")
                        .tool("query_logs")
                        .params(Map.of("service", "${service}", "keywords", "slow,timeout,latency,performance"))
                        .expectedOutput("Slow operation logs")
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("获取告警信息")
                        .action("Get performance alert context")
                        .tool("get_alert_context")
                        .params(Map.of("alertId", "${alertId}"))
                        .expectedOutput("Performance alert details")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(4)
                        .stepName("搜索性能优化方案")
                        .action("Search wiki for performance solutions")
                        .tool("search_wiki")
                        .params(Map.of("query", "性能问题 超时 慢", "limit", "5"))
                        .expectedOutput("Performance optimization guides")
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        ));
    }

    /**
     * Execute the diagnosis chain based on intent result.
     *
     * @param intentResult Intent recognition result
     * @return DiagnosisChainResult containing execution results
     */
    public DiagnosisChainResult executeChain(IntentResult intentResult) {
        log.info("Executing diagnosis chain for problem type: {}", intentResult.getProblemType());

        long startTime = System.currentTimeMillis();

        // Step 1: Get the chain template
        List<DiagnosisStep> chainTemplate = getChainTemplate(intentResult);
        log.debug("Chain template has {} steps", chainTemplate.size());

        // Step 2: Initialize execution context
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("problemType", intentResult.getProblemType());
        context.put("originalMessage", intentResult.getOriginalMessage());
        if (intentResult.getContext() != null) {
            context.putAll(intentResult.getContext());
        }

        // Step 3: Execute each step
        List<StepResult> stepResults = new ArrayList<>();
        int successfulSteps = 0;
        int failedSteps = 0;
        boolean chainInterrupted = false;
        String interruptionReason = null;

        for (DiagnosisStep step : chainTemplate) {
            if (stepResults.size() >= MAX_CHAIN_STEPS) {
                chainInterrupted = true;
                interruptionReason = "Maximum chain steps reached";
                break;
            }

            StepResult stepResult = executeStep(step, context);
            stepResults.add(stepResult);

            if (stepResult.isSuccess()) {
                successfulSteps++;
                // Add step result data to context for subsequent steps
                if (stepResult.getData() != null) {
                    context.put("step_" + step.getStepOrder(), stepResult.getData());
                }
            } else {
                failedSteps++;
                // Check if this is a required step
                if (step.isRequired()) {
                    // Try fallback if available
                    if (step.getFallback() != null) {
                        log.warn("Required step {} failed, attempting fallback: {}",
                                step.getStepName(), step.getFallback());
                        // Mark as using fallback
                        stepResult.setUsedFallback(true);
                    } else if (shouldInterruptChain(step, stepResults)) {
                        chainInterrupted = true;
                        interruptionReason = "Required step failed: " + step.getStepName();
                        break;
                    }
                }
            }
        }

        long totalExecutionTime = System.currentTimeMillis() - startTime;

        // Step 4: Build the result
        DiagnosisChainResult result = DiagnosisChainResult.builder()
                .stepResults(stepResults)
                .success(failedSteps == 0 || !chainInterrupted)
                .error(chainInterrupted ? interruptionReason : null)
                .totalExecutionTime(totalExecutionTime)
                .successfulSteps(successfulSteps)
                .failedSteps(failedSteps)
                .aggregatedContext(context)
                .chainSource(chainTemplate.isEmpty() ? "DEFAULT" :
                        (chainTemplate.get(0).getStepName() != null ? "TEMPLATE" : "CLAUDE_GENERATED"))
                .interruptedEarly(chainInterrupted)
                .interruptionReason(interruptionReason)
                .summary(buildChainSummary(stepResults))
                .build();

        log.info("Diagnosis chain completed: {} successful, {} failed, {}ms total",
                successfulSteps, failedSteps, totalExecutionTime);

        return result;
    }

    /**
     * Get the diagnosis chain template based on intent result.
     *
     * @param intent Intent recognition result
     * @return List of diagnosis steps
     */
    private List<DiagnosisStep> getChainTemplate(IntentResult intent) {
        String problemType = intent.getProblemType();

        // Step 1: Check if there's a high-confidence experience match
        List<Experience> experiences = experienceRepository.findByProblemType(problemType);
        if (!experiences.isEmpty()) {
            Experience bestExperience = experiences.stream()
                    .max(Comparator.comparing(Experience::getConfidenceScore))
                    .orElse(null);

            if (bestExperience != null && bestExperience.getConfidenceScore() >= 0.8) {
                log.info("Using high-confidence experience chain for problem type: {}", problemType);
                List<DiagnosisStep> experienceChain = parseExperienceChain(bestExperience);
                if (!experienceChain.isEmpty()) {
                    return experienceChain;
                }
            }
        }

        // Step 2: Use default template for known problem types
        if (DEFAULT_CHAIN_TEMPLATES.containsKey(problemType)) {
            log.info("Using default chain template for problem type: {}", problemType);
            return DEFAULT_CHAIN_TEMPLATES.get(problemType);
        }

        // Step 3: Generate chain with Claude for unknown types
        if ("UNKNOWN".equals(problemType) || intent.getConfidence() < 0.5) {
            log.info("Generating diagnosis chain with Claude for unknown problem type");
            return generateChainWithClaude(intent);
        }

        // Step 4: Return a generic default chain
        return getDefaultUnknownChain();
    }

    /**
     * Parse experience's diagnosis chain into step list.
     *
     * @param experience Experience entity
     * @return List of diagnosis steps
     */
    private List<DiagnosisStep> parseExperienceChain(Experience experience) {
        if (experience.getDiagnosisChain() == null || experience.getDiagnosisChain().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<DiagnosisStep> steps = objectMapper.readValue(experience.getDiagnosisChain(),
                    new TypeReference<List<DiagnosisStep>>() {});
            return steps;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse experience diagnosis chain: {}", experience.getExperienceId(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Generate diagnosis chain using Claude API.
     *
     * @param intent Intent recognition result
     * @return List of generated diagnosis steps
     */
    private List<DiagnosisStep> generateChainWithClaude(IntentResult intent) {
        try {
            // Get available tools
            List<Map<String, Object>> availableTools = toolRegistry.getAvailableTools();
            String toolsJson = objectMapper.writeValueAsString(availableTools);

            // Build the prompt
            Map<String, String> params = new HashMap<>();
            params.put("problem_type", intent.getProblemType());
            params.put("context", intent.getContext() != null ?
                    objectMapper.writeValueAsString(intent.getContext()) : "{}");
            params.put("user_problem", intent.getOriginalMessage());
            params.put("available_tools", toolsJson);

            String prompt = promptTemplateService.getTemplate(
                    PromptTemplateService.DIAGNOSIS_ORCHESTRATOR, params);

            ClaudeResponse response = claudeService.sendMessage(prompt);
            String responseText = response.getTextContent();

            // Parse the response
            return parseClaudeGeneratedChain(responseText);

        } catch (Exception e) {
            log.error("Failed to generate chain with Claude", e);
            return getDefaultUnknownChain();
        }
    }

    /**
     * Parse Claude-generated diagnosis chain.
     *
     * @param responseText Claude response text
     * @return List of diagnosis steps
     */
    private List<DiagnosisStep> parseClaudeGeneratedChain(String responseText) {
        try {
            // Extract JSON if present
            String jsonPart = extractJson(responseText);
            if (jsonPart != null && !jsonPart.isEmpty()) {
                Map<String, Object> result = objectMapper.readValue(jsonPart,
                        new TypeReference<Map<String, Object>>() {});

                if (result.containsKey("steps")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> stepsList = (List<Map<String, Object>>) result.get("steps");

                    return stepsList.stream()
                            .map(this::mapToDiagnosisStep)
                            .filter(step -> step != null)
                            .collect(Collectors.toList());
                }
            }

            // Fallback: parse text-based step descriptions
            return parseTextBasedChain(responseText);

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Claude generated chain", e);
            return getDefaultUnknownChain();
        }
    }

    /**
     * Map a JSON object to DiagnosisStep.
     */
    @SuppressWarnings("unchecked")
    private DiagnosisStep mapToDiagnosisStep(Map<String, Object> stepMap) {
        try {
            int stepOrder = ((Number) stepMap.getOrDefault("stepOrder",
                    stepMap.getOrDefault("order", 0))).intValue();

            String tool = (String) stepMap.getOrDefault("tool",
                    stepMap.getOrDefault("toolName", ""));

            // Validate tool exists
            if (!toolRegistry.hasTool(tool)) {
                log.warn("Tool {} not found in registry, skipping step", tool);
                return null;
            }

            Map<String, String> params = new LinkedHashMap<>();
            Object paramsObj = stepMap.get("params");
            if (paramsObj instanceof Map) {
                ((Map<String, Object>) paramsObj).forEach((k, v) -> {
                    params.put(k, v != null ? v.toString() : "");
                });
            }

            return DiagnosisStep.builder()
                    .stepOrder(stepOrder)
                    .stepName((String) stepMap.getOrDefault("stepName",
                            stepMap.getOrDefault("name", "Step " + stepOrder)))
                    .action((String) stepMap.getOrDefault("action",
                            stepMap.getOrDefault("description", "")))
                    .tool(tool)
                    .params(params)
                    .expectedOutput((String) stepMap.getOrDefault("expectedOutput", ""))
                    .required((Boolean) stepMap.getOrDefault("required", true))
                    .timeoutMs(((Number) stepMap.getOrDefault("timeoutMs",
                            DEFAULT_STEP_TIMEOUT_MS)).longValue())
                    .build();

        } catch (Exception e) {
            log.warn("Failed to map step: {}", stepMap, e);
            return null;
        }
    }

    /**
     * Parse text-based chain description.
     */
    private List<DiagnosisStep> parseTextBasedChain(String text) {
        List<DiagnosisStep> steps = new ArrayList<>();
        String[] lines = text.split("\n");

        int order = 1;
        for (String line : lines) {
            if (line.contains("step") || line.contains("Step") || line.contains("步骤")) {
                // Try to extract tool name
                for (Map<String, Object> toolInfo : toolRegistry.getAvailableTools()) {
                    String toolName = (String) toolInfo.get("name");
                    if (line.toLowerCase().contains(toolName.toLowerCase())) {
                        steps.add(DiagnosisStep.builder()
                                .stepOrder(order++)
                                .stepName(line.trim())
                                .tool(toolName)
                                .params(new HashMap<>())
                                .required(true)
                                .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                                .build());
                        break;
                    }
                }
            }
        }

        return steps.isEmpty() ? getDefaultUnknownChain() : steps;
    }

    /**
     * Execute a single diagnosis step.
     *
     * @param step Diagnosis step definition
     * @param context Current execution context
     * @return StepResult containing execution result
     */
    private StepResult executeStep(DiagnosisStep step, Map<String, Object> context) {
        log.info("Executing step {}: {} with tool {}", step.getStepOrder(), step.getStepName(), step.getTool());

        long stepStartTime = System.currentTimeMillis();

        StepResult stepResult = StepResult.builder()
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .toolName(step.getTool())
                .action(step.getAction())
                .build();

        try {
            // Resolve parameters
            Map<String, Object> resolvedParams = resolveParams(step.getParams(), context);
            log.debug("Resolved params for step {}: {}", step.getStepOrder(), resolvedParams);

            // Validate parameters
            var validationResult = toolRegistry.validateParams(step.getTool(), resolvedParams);
            if (!validationResult.valid()) {
                log.warn("Parameter validation failed for tool {}: {}", step.getTool(), validationResult.errorMessage());
                stepResult.setSuccess(false);
                stepResult.setError(validationResult.errorMessage());
                stepResult.setExecutionTime(System.currentTimeMillis() - stepStartTime);
                return stepResult;
            }

            // Execute the tool
            ToolResult toolResult = toolRegistry.executeTool(step.getTool(), resolvedParams);

            stepResult.setSuccess(toolResult.isSuccess());
            stepResult.setData(toolResult.getData());
            stepResult.setError(toolResult.getError());
            stepResult.setRawOutput(toolResult.getData() != null ?
                    objectMapper.writeValueAsString(toolResult.getData()) : null);
            stepResult.setExecutionTime(toolResult.getExecutionTime());

            if (toolResult.isSuccess()) {
                log.info("Step {} executed successfully in {}ms", step.getStepOrder(), stepResult.getExecutionTime());
            } else {
                log.warn("Step {} failed: {}", step.getStepOrder(), toolResult.getError());
            }

        } catch (Exception e) {
            log.error("Error executing step {}: {}", step.getStepOrder(), e.getMessage(), e);
            stepResult.setSuccess(false);
            stepResult.setError("Execution error: " + e.getMessage());
            stepResult.setExecutionTime(System.currentTimeMillis() - stepStartTime);
        }

        return stepResult;
    }

    /**
     * Resolve parameters by replacing placeholders with actual values from context.
     *
     * @param params Parameter templates
     * @param context Execution context
     * @return Resolved parameter map
     */
    private Map<String, Object> resolveParams(Map<String, String> params, Map<String, Object> context) {
        Map<String, Object> resolved = new LinkedHashMap<>();

        if (params == null) {
            return resolved;
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();

            // Replace ${...} placeholders
            String resolvedValue = resolvePlaceholders(paramValue, context);

            // Try to convert to appropriate type
            resolved.put(paramName, convertToAppropriateType(resolvedValue));
        }

        return resolved;
    }

    /**
     * Resolve placeholder patterns like ${workflowId} with context values.
     */
    private String resolvePlaceholders(String template, Map<String, Object> context) {
        if (template == null) {
            return "";
        }

        String result = template;
        Pattern placeholderPattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = placeholderPattern.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = context.get(placeholder);
            if (value != null) {
                result = result.replace("${" + placeholder + "}", value.toString());
            } else {
                // Keep placeholder if no value found
                log.debug("No value found for placeholder: {}", placeholder);
            }
        }

        return result;
    }

    /**
     * Convert string value to appropriate type (number, boolean, etc.).
     */
    private Object convertToAppropriateType(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        // Try to parse as integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}

        // Try to parse as double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}

        // Check for boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;

        // Return as string
        return value;
    }

    /**
     * Extract JSON from response text.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /**
     * Determine if chain should be interrupted after a step failure.
     */
    private boolean shouldInterruptChain(DiagnosisStep failedStep, List<StepResult> previousResults) {
        // Interrupt if first required step fails
        if (failedStep.getStepOrder() == 1 && failedStep.isRequired()) {
            return true;
        }

        // Interrupt if multiple required steps have failed
        long requiredFailures = previousResults.stream()
                .filter(r -> !r.isSuccess())
                .filter(r -> r.getStepOrder() <= failedStep.getStepOrder())
                .count();

        return requiredFailures >= 2;
    }

    /**
     * Build summary of the chain execution.
     */
    private String buildChainSummary(List<StepResult> results) {
        if (results.isEmpty()) {
            return "No steps executed";
        }

        int successful = (int) results.stream().filter(StepResult::isSuccess).count();
        int total = results.size();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Executed %d steps (%d successful). ", total, successful));

        // Add key findings
        for (StepResult result : results) {
            if (result.isSuccess() && result.getData() != null) {
                sb.append(result.getStepName()).append(": OK. ");
            } else if (!result.isSuccess()) {
                sb.append(result.getStepName()).append(": Failed. ");
            }
        }

        return sb.toString();
    }

    /**
     * Get default chain for unknown problem types.
     */
    private List<DiagnosisStep> getDefaultUnknownChain() {
        return Arrays.asList(
                DiagnosisStep.builder()
                        .stepOrder(1)
                        .stepName("查询基本信息")
                        .action("Query basic information from operations platform")
                        .tool("query_ops_platform")
                        .params(new HashMap<>())
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(2)
                        .stepName("查询日志")
                        .action("Query logs for error information")
                        .tool("query_logs")
                        .params(Map.of("keywords", "error,failed,exception"))
                        .required(true)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build(),
                DiagnosisStep.builder()
                        .stepOrder(3)
                        .stepName("搜索知识库")
                        .action("Search wiki for related documentation")
                        .tool("search_wiki")
                        .params(Map.of("query", "问题诊断", "limit", "5"))
                        .required(false)
                        .timeoutMs(DEFAULT_STEP_TIMEOUT_MS)
                        .build()
        );
    }

    /**
     * Get chain template for a specific problem type.
     *
     * @param problemType Problem type
     * @return List of diagnosis steps
     */
    public List<DiagnosisStep> getChainTemplateForProblemType(String problemType) {
        return DEFAULT_CHAIN_TEMPLATES.getOrDefault(problemType, getDefaultUnknownChain());
    }
}