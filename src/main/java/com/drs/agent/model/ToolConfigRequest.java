package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tool Configuration Request DTO
 *
 * Request body for creating or updating a dynamic MCP tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfigRequest {

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
    private List<ToolParamDefinition> inputParams;

    /**
     * Output format definition (JSON).
     */
    private String outputFormat;

    /**
     * Tool type: HTTP or MOCK.
     */
    private String toolType;

    /**
     * HTTP endpoint URL (for HTTP type).
     */
    private String endpointUrl;

    /**
     * HTTP method: GET, POST, PUT, DELETE.
     */
    private String httpMethod;

    /**
     * Authentication type: NONE, API_KEY, BASIC.
     */
    private String authType;

    /**
     * Authentication configuration (JSON).
     */
    private AuthConfig authConfig;

    /**
     * Request body template with ${params.xxx} placeholders.
     */
    private String requestTemplate;

    /**
     * JSONPath expression for response mapping.
     */
    private String responseMapping;

    /**
     * Custom HTTP headers.
     */
    private List<HeaderDefinition> headers;

    /**
     * Timeout in milliseconds.
     */
    private Integer timeoutMs;

    /**
     * Whether the tool is enabled.
     */
    private Boolean enabled;

    /**
     * Input parameter definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolParamDefinition {
        private String name;
        private String type;
        private Boolean required;
        private String description;
        private Object defaultValue;
    }

    /**
     * Authentication configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthConfig {
        private String header;      // For API_KEY: header name (e.g., X-API-Key)
        private String key;         // For API_KEY: API key value
        private String username;    // For BASIC: username
        private String password;    // For BASIC: password
    }

    /**
     * Header definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderDefinition {
        private String name;
        private String value;
    }
}