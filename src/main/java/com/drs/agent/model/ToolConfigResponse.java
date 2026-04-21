package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tool Configuration Response DTO
 *
 * Response body for tool configuration API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfigResponse {

    /**
     * Database ID.
     */
    private Long id;

    /**
     * Unique name of the tool.
     */
    private String toolName;

    /**
     * Human-readable description.
     */
    private String description;

    /**
     * Input parameter definitions.
     */
    private Object inputParams;

    /**
     * Output format definition (JSON).
     */
    private Object outputFormat;

    /**
     * Tool type: HTTP or MOCK.
     */
    private String toolType;

    /**
     * HTTP endpoint URL (for HTTP type).
     */
    private String endpointUrl;

    /**
     * HTTP method.
     */
    private String httpMethod;

    /**
     * Authentication type.
     */
    private String authType;

    /**
     * Authentication configuration.
     */
    private Object authConfig;

    /**
     * Request body template.
     */
    private Object requestTemplate;

    /**
     * JSONPath expression for response mapping.
     */
    private String responseMapping;

    /**
     * Custom HTTP headers.
     */
    private Object headers;

    /**
     * Timeout in milliseconds.
     */
    private Integer timeoutMs;

    /**
     * Whether the tool is enabled.
     */
    private Boolean enabled;

    /**
     * Whether this is a dynamic tool (configured via database).
     */
    private Boolean isDynamic;

    /**
     * Creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Convert from entity.
     */
    public static ToolConfigResponse fromEntity(com.drs.agent.entity.McpToolConfig entity, boolean isDynamic) {
        return ToolConfigResponse.builder()
                .id(entity.getId())
                .toolName(entity.getToolName())
                .description(entity.getDescription())
                .inputParams(parseJsonOrNull(entity.getInputParams()))
                .outputFormat(parseJsonOrNull(entity.getOutputFormat()))
                .toolType(entity.getToolType())
                .endpointUrl(entity.getEndpointUrl())
                .httpMethod(entity.getHttpMethod())
                .authType(entity.getAuthType())
                .authConfig(parseJsonOrNull(entity.getAuthConfig()))
                .requestTemplate(parseJsonOrNull(entity.getRequestTemplate()))
                .responseMapping(entity.getResponseMapping())
                .headers(parseJsonOrNull(entity.getHeaders()))
                .timeoutMs(entity.getTimeoutMs())
                .enabled(entity.getEnabled())
                .isDynamic(isDynamic)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static Object parseJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}