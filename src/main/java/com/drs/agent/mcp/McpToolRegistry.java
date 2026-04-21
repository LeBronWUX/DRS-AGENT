package com.drs.agent.mcp;

import com.drs.agent.entity.McpToolConfig;
import com.drs.agent.repository.McpToolConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * MCP Tool Registry
 *
 * Manages registration, discovery, and execution of MCP tools.
 * Automatically scans and registers all classes annotated with @McpTool.
 * Also loads dynamic tools from database configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolRegistry {

    private final ObjectMapper objectMapper;
    private final McpToolConfigRepository toolConfigRepository;
    private final WebClient.Builder webClientBuilder;

    /**
     * Registry of all available tools.
     * Key: tool name, Value: ToolInfo containing handler and metadata
     */
    @Getter
    private final Map<String, ToolInfo> toolRegistry = new HashMap<>();

    /**
     * Registry of dynamic tools (separate for management).
     */
    @Getter
    private final Map<String, ToolInfo> dynamicToolRegistry = new HashMap<>();

    /**
     * List of all McpToolHandler beans injected by Spring.
     */
    private final List<McpToolHandler> handlers;

    /**
     * Tool metadata container.
     */
    public record ToolInfo(
            String name,
            String description,
            List<ToolParam> inputParams,
            String outputFormat,
            McpToolHandler handler,
            Class<?> handlerClass
    ) {}

    /**
     * Initialize the registry by scanning and registering all @McpTool annotated classes.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing MCP Tool Registry...");
        scanAndRegisterTools();
        loadDynamicTools();
        log.info("MCP Tool Registry initialized with {} static + {} dynamic tools: {}",
                toolRegistry.size() - dynamicToolRegistry.size(),
                dynamicToolRegistry.size(),
                toolRegistry.keySet());
    }

    /**
     * Load dynamic tools from database configuration.
     */
    private void loadDynamicTools() {
        try {
            List<McpToolConfig> configs = toolConfigRepository.findByEnabled(true);
            for (McpToolConfig config : configs) {
                registerDynamicTool(config);
            }
            log.info("Loaded {} dynamic tools from database", configs.size());
        } catch (Exception e) {
            log.warn("Failed to load dynamic tools from database: {}", e.getMessage());
        }
    }

    /**
     * Register a dynamic tool from database configuration.
     *
     * @param config Tool configuration from database
     */
    public void registerDynamicTool(McpToolConfig config) {
        try {
            DynamicHttpToolHandler handler = new DynamicHttpToolHandler(config, webClientBuilder, objectMapper);

            // Parse input parameters
            List<ToolParam> inputParams = parseInputParams(config.getInputParams());

            ToolInfo toolInfo = new ToolInfo(
                    config.getToolName(),
                    config.getDescription(),
                    inputParams,
                    config.getOutputFormat(),
                    handler,
                    DynamicHttpToolHandler.class
            );

            toolRegistry.put(config.getToolName(), toolInfo);
            dynamicToolRegistry.put(config.getToolName(), toolInfo);
            log.debug("Registered dynamic tool: {} -> {}", config.getToolName(), config.getToolType());

        } catch (Exception e) {
            log.error("Failed to register dynamic tool {}: {}", config.getToolName(), e.getMessage());
        }
    }

    /**
     * Remove a dynamic tool from registry.
     *
     * @param toolName Name of the tool to remove
     */
    public void removeDynamicTool(String toolName) {
        if (dynamicToolRegistry.containsKey(toolName)) {
            toolRegistry.remove(toolName);
            dynamicToolRegistry.remove(toolName);
            log.info("Removed dynamic tool: {}", toolName);
        } else {
            log.warn("Cannot remove tool {} - not a dynamic tool", toolName);
        }
    }

    /**
     * Test a tool with given parameters without affecting registry.
     *
     * @param toolName Tool name to test
     * @param params Test parameters
     * @return ToolResult from test execution
     */
    public ToolResult testTool(String toolName, Map<String, Object> params) {
        ToolInfo toolInfo = toolRegistry.get(toolName);
        if (toolInfo == null) {
            return ToolResult.failure("Tool not found: " + toolName);
        }

        log.info("Testing tool: {} with params: {}", toolName, params);
        return executeTool(toolName, params);
    }

    /**
     * Check if a tool is dynamic (configured via database).
     *
     * @param toolName Tool name
     * @return true if dynamic tool
     */
    public boolean isDynamicTool(String toolName) {
        return dynamicToolRegistry.containsKey(toolName);
    }

    /**
     * Refresh dynamic tools from database.
     */
    public void refreshDynamicTools() {
        // Remove all existing dynamic tools
        dynamicToolRegistry.keySet().forEach(toolRegistry::remove);
        dynamicToolRegistry.clear();

        // Reload from database
        loadDynamicTools();
        log.info("Dynamic tools refreshed");
    }

    /**
     * Scan the MCP package for @McpTool annotated classes and register them.
     */
    private void scanAndRegisterTools() {
        // Scan for @McpTool annotated classes
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(McpTool.class));

        String basePackage = "com.drs.agent.mcp";
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

        for (BeanDefinition bd : candidates) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                registerToolClass(clazz);
            } catch (ClassNotFoundException e) {
                log.error("Could not load tool class: {}", bd.getBeanClassName(), e);
            }
        }
    }

    /**
     * Register a tool class with its annotation metadata.
     *
     * @param clazz The class annotated with @McpTool
     */
    private void registerToolClass(Class<?> clazz) {
        McpTool annotation = clazz.getAnnotation(McpTool.class);
        if (annotation == null) {
            log.warn("Class {} is not annotated with @McpTool", clazz.getName());
            return;
        }

        String toolName = annotation.name();

        // Find the handler for this tool class
        McpToolHandler handler = findHandlerForClass(clazz);
        if (handler == null) {
            log.warn("No handler bean found for tool class: {}", clazz.getName());
            return;
        }

        // Parse input parameters
        List<ToolParam> inputParams = parseInputParams(annotation.inputParams());

        ToolInfo toolInfo = new ToolInfo(
                toolName,
                annotation.description(),
                inputParams,
                annotation.outputFormat(),
                handler,
                clazz
        );

        toolRegistry.put(toolName, toolInfo);
        log.debug("Registered tool: {} -> {}", toolName, clazz.getSimpleName());
    }

    /**
     * Find the handler instance for a given tool class.
     *
     * @param clazz The tool class
     * @return The handler instance or null if not found
     */
    private McpToolHandler findHandlerForClass(Class<?> clazz) {
        return handlers.stream()
                .filter(h -> clazz.isInstance(h))
                .findFirst()
                .orElse(null);
    }

    /**
     * Parse input parameters from JSON string.
     *
     * @param inputParamsJson JSON string of parameters
     * @return List of ToolParam objects
     */
    private List<ToolParam> parseInputParams(String inputParamsJson) {
        if (inputParamsJson == null || inputParamsJson.isBlank() || "[]".equals(inputParamsJson)) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(inputParamsJson, new TypeReference<List<ToolParam>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse input params: {}", inputParamsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * Execute a tool by name with the given parameters.
     *
     * @param toolName Name of the tool to execute
     * @param params Parameters to pass to the tool
     * @return ToolResult containing the execution result
     */
    public ToolResult executeTool(String toolName, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        log.info("Executing tool: {} with params: {}", toolName, params);

        ToolInfo toolInfo = toolRegistry.get(toolName);
        if (toolInfo == null) {
            log.warn("Tool not found: {}", toolName);
            return ToolResult.failure("Tool not found: " + toolName);
        }

        try {
            // Validate parameters
            McpToolHandler.ValidationResult validation = toolInfo.handler().validate(params);
            if (!validation.valid()) {
                log.warn("Parameter validation failed for tool {}: {}", toolName, validation.errorMessage());
                return ToolResult.failure(validation.errorMessage(), System.currentTimeMillis() - startTime);
            }

            // Execute the tool
            ToolResult result = toolInfo.handler().execute(params);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            log.info("Tool {} executed successfully in {}ms", toolName, result.getExecutionTime());
            return result;

        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return ToolResult.failure("Execution error: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Get a list of all available tools.
     *
     * @return List of tool information maps
     */
    public List<Map<String, Object>> getAvailableTools() {
        return toolRegistry.values().stream()
                .map(toolInfo -> {
                    Map<String, Object> descriptor = toToolDescriptor(toolInfo);
                    descriptor.put("isDynamic", isDynamicTool(toolInfo.name()));
                    return descriptor;
                })
                .toList();
    }

    /**
     * Convert ToolInfo to a tool descriptor map.
     *
     * @param toolInfo Tool information
     * @return Map containing tool descriptor
     */
    private Map<String, Object> toToolDescriptor(ToolInfo toolInfo) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", toolInfo.name());
        descriptor.put("description", toolInfo.description());
        descriptor.put("inputParams", toolInfo.inputParams());
        descriptor.put("outputFormat", toolInfo.outputFormat());
        return descriptor;
    }

    /**
     * Get the description of a specific tool.
     *
     * @param toolName Name of the tool
     * @return Tool description or null if not found
     */
    public String getToolDescription(String toolName) {
        ToolInfo toolInfo = toolRegistry.get(toolName);
        return toolInfo != null ? toolInfo.description() : null;
    }

    /**
     * Get detailed information about a specific tool.
     *
     * @param toolName Name of the tool
     * @return Tool descriptor map or null if not found
     */
    public Map<String, Object> getToolInfo(String toolName) {
        ToolInfo toolInfo = toolRegistry.get(toolName);
        return toolInfo != null ? toToolDescriptor(toolInfo) : null;
    }

    /**
     * Validate parameters for a specific tool.
     *
     * @param toolName Name of the tool
     * @param params Parameters to validate
     * @return ValidationResult indicating if parameters are valid
     */
    public McpToolHandler.ValidationResult validateParams(String toolName, Map<String, Object> params) {
        ToolInfo toolInfo = toolRegistry.get(toolName);
        if (toolInfo == null) {
            return McpToolHandler.ValidationResult.failure("Tool not found: " + toolName);
        }

        // Check required parameters
        List<String> missingParams = new ArrayList<>();
        for (ToolParam param : toolInfo.inputParams()) {
            if (param.isRequired() && !params.containsKey(param.getName())) {
                missingParams.add(param.getName());
            }
        }

        if (!missingParams.isEmpty()) {
            return McpToolHandler.ValidationResult.failure(
                    "Missing required parameters: " + String.join(", ", missingParams));
        }

        // Type validation
        for (ToolParam param : toolInfo.inputParams()) {
            Object value = params.get(param.getName());
            if (value != null) {
                McpToolHandler.ValidationResult typeValidation = validateParamType(param, value);
                if (!typeValidation.valid()) {
                    return typeValidation;
                }
            }
        }

        // Use custom validator if provided
        return toolInfo.handler().validate(params);
    }

    /**
     * Validate a parameter value against its type definition.
     *
     * @param param Parameter definition
     * @param value Value to validate
     * @return ValidationResult
     */
    private McpToolHandler.ValidationResult validateParamType(ToolParam param, Object value) {
        String expectedType = param.getType();
        String actualType = getTypeName(value);

        if (!isTypeCompatible(expectedType, actualType)) {
            return McpToolHandler.ValidationResult.failure(
                    String.format("Parameter '%s' expects type '%s' but got '%s'",
                            param.getName(), expectedType, actualType));
        }

        // Validate enum values
        if (param.getEnumValues() != null && param.getEnumValues().length > 0) {
            boolean validEnum = Arrays.asList(param.getEnumValues()).contains(value);
            if (!validEnum) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' must be one of: %s",
                                param.getName(), Arrays.toString(param.getEnumValues())));
            }
        }

        // Validate numeric ranges
        if ("number".equals(expectedType) && value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            if (param.getMinimum() != null && numValue < param.getMinimum().doubleValue()) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' must be >= %s", param.getName(), param.getMinimum()));
            }
            if (param.getMaximum() != null && numValue > param.getMaximum().doubleValue()) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' must be <= %s", param.getName(), param.getMaximum()));
            }
        }

        // Validate string length
        if ("string".equals(expectedType) && value instanceof String str) {
            if (param.getMinLength() != null && str.length() < param.getMinLength()) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' must be at least %d characters",
                                param.getName(), param.getMinLength()));
            }
            if (param.getMaxLength() != null && str.length() > param.getMaxLength()) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' must be at most %d characters",
                                param.getName(), param.getMaxLength()));
            }
            if (param.getPattern() != null && !str.matches(param.getPattern())) {
                return McpToolHandler.ValidationResult.failure(
                        String.format("Parameter '%s' does not match required pattern: %s",
                                param.getName(), param.getPattern()));
            }
        }

        return McpToolHandler.ValidationResult.success();
    }

    /**
     * Get the type name for a value.
     */
    private String getTypeName(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Long ||
            value instanceof Double || value instanceof Float ||
            value instanceof Short || value instanceof Byte) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Map) return "object";
        if (value instanceof List || value instanceof Object[]) return "array";
        return "unknown";
    }

    /**
     * Check if types are compatible.
     */
    private boolean isTypeCompatible(String expected, String actual) {
        if (expected.equals(actual)) return true;
        // Allow integer to be passed as number
        if ("number".equals(expected) && "integer".equals(actual)) return true;
        return false;
    }

    /**
     * Check if a tool exists.
     *
     * @param toolName Name of the tool
     * @return true if tool exists
     */
    public boolean hasTool(String toolName) {
        return toolRegistry.containsKey(toolName);
    }

    /**
     * Get the number of registered tools.
     *
     * @return Number of tools
     */
    public int getToolCount() {
        return toolRegistry.size();
    }
}