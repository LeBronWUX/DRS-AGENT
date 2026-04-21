package com.drs.agent.mcp;

import com.drs.agent.entity.McpToolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic HTTP Tool Handler
 *
 * Executes HTTP calls based on dynamic configuration stored in database.
 * Implements McpToolHandler interface so it can be used by the registry.
 */
@Slf4j
public class DynamicHttpToolHandler implements McpToolHandler {

    private final McpToolConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z0-9_]+)}");

    public DynamicHttpToolHandler(McpToolConfig config, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.config = config;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing dynamic HTTP tool: {} with params: {}", config.getToolName(), parameters);
        long startTime = System.currentTimeMillis();

        try {
            // Build URL with parameter substitution
            String url = substituteParams(config.getEndpointUrl(), parameters);

            // Build headers
            HttpHeaders headers = buildHeaders(parameters);

            // Build request body if needed
            Object body = null;
            if (HttpMethod.POST.name().equals(config.getHttpMethod()) ||
                HttpMethod.PUT.name().equals(config.getHttpMethod()) ||
                HttpMethod.PATCH.name().equals(config.getHttpMethod())) {
                body = buildRequestBody(parameters);
            }

            // Execute HTTP request
            String response = executeHttpRequest(url, headers, body);

            // Apply response mapping
            Object resultData = applyResponseMapping(response);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Dynamic HTTP tool {} executed successfully in {}ms", config.getToolName(), executionTime);

            return ToolResult.success(resultData, executionTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Error executing dynamic HTTP tool {}: {}", config.getToolName(), e.getMessage(), e);
            return ToolResult.failure("HTTP request failed: " + e.getMessage(), executionTime);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        // Validate required parameters from config
        try {
            if (config.getInputParams() != null && !config.getInputParams().isBlank()) {
                JsonNode paramsNode = objectMapper.readTree(config.getInputParams());
                for (JsonNode param : paramsNode) {
                    if (param.has("required") && param.get("required").asBoolean()) {
                        String name = param.get("name").asText();
                        if (!parameters.containsKey(name)) {
                            return ValidationResult.failure("Missing required parameter: " + name);
                        }
                    }
                }
            }
            return ValidationResult.success();
        } catch (Exception e) {
            log.warn("Failed to parse input params config: {}", e.getMessage());
            return ValidationResult.success();
        }
    }

    /**
     * Substitute ${params.xxx} placeholders with actual parameter values.
     */
    private String substituteParams(String template, Map<String, Object> params) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Build HTTP headers including authentication.
     */
    private HttpHeaders buildHeaders(Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();

        // Default content type
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Custom headers from config
        try {
            if (config.getHeaders() != null && !config.getHeaders().isBlank()) {
                JsonNode headersNode = objectMapper.readTree(config.getHeaders());
                headersNode.fields().forEachRemaining(entry -> {
                    String value = substituteParams(entry.getValue().asText(), params);
                    headers.add(entry.getKey(), value);
                });
            }
        } catch (Exception e) {
            log.warn("Failed to parse headers config: {}", e.getMessage());
        }

        // Authentication headers
        try {
            if (config.getAuthConfig() != null && !config.getAuthConfig().isBlank()) {
                JsonNode authConfig = objectMapper.readTree(config.getAuthConfig());
                String authType = config.getAuthType();

                if ("API_KEY".equals(authType)) {
                    String headerName = authConfig.has("header") ? authConfig.get("header").asText() : "X-API-Key";
                    String apiKey = authConfig.has("key") ? authConfig.get("key").asText() : "";
                    headers.add(headerName, apiKey);
                } else if ("BASIC".equals(authType)) {
                    String username = authConfig.has("username") ? authConfig.get("username").asText() : "";
                    String password = authConfig.has("password") ? authConfig.get("password").asText() : "";
                    // Basic auth encoding would be done by WebClient
                    // For simplicity, we add as header
                    String encoded = java.util.Base64.getEncoder()
                            .encodeToString((username + ":" + password).getBytes());
                    headers.add("Authorization", "Basic " + encoded);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse auth config: {}", e.getMessage());
        }

        return headers;
    }

    /**
     * Build request body from template.
     */
    private Object buildRequestBody(Map<String, Object> params) {
        try {
            if (config.getRequestTemplate() != null && !config.getRequestTemplate().isBlank()) {
                // Substitute params in template
                String template = substituteParams(config.getRequestTemplate(), params);
                // Parse as JSON
                return objectMapper.readValue(template, Map.class);
            }
            // Default: use params directly
            return new HashMap<>(params);
        } catch (Exception e) {
            log.warn("Failed to build request body: {}", e.getMessage());
            return params;
        }
    }

    /**
     * Execute the HTTP request.
     */
    private String executeHttpRequest(String url, HttpHeaders headers, Object body) {
        HttpMethod method = HttpMethod.valueOf(config.getHttpMethod().toUpperCase());
        int timeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : 30000;

        WebClient.RequestBodySpec request = webClient.method(method)
                .uri(url)
                .headers(httpHeaders -> httpHeaders.addAll(headers));

        if (body != null) {
            request.bodyValue(body);
        }

        return request.retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(e -> {
                    log.error("HTTP request error: {}", e.getMessage());
                    return Mono.error(e);
                })
                .block();
    }

    /**
     * Apply JSONPath response mapping to extract result.
     */
    private Object applyResponseMapping(String response) {
        try {
            if (config.getResponseMapping() == null || config.getResponseMapping().isBlank()) {
                // Return full response
                return objectMapper.readValue(response, Object.class);
            }

            JsonNode responseNode = objectMapper.readTree(response);
            String mapping = config.getResponseMapping();

            // Simple path navigation (e.g., "data.result" -> response.data.result)
            String[] parts = mapping.split("\\.");
            JsonNode current = responseNode;
            for (String part : parts) {
                if (current.has(part)) {
                    current = current.get(part);
                } else {
                    log.warn("Response mapping path not found: {} at part {}", mapping, part);
                    break;
                }
            }

            return objectMapper.treeToValue(current, Object.class);

        } catch (Exception e) {
            log.warn("Failed to apply response mapping: {}", e.getMessage());
            return response;
        }
    }

    /**
     * Get the tool configuration.
     */
    public McpToolConfig getConfig() {
        return config;
    }
}