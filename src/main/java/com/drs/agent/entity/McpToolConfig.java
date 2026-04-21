package com.drs.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP Tool Configuration Entity
 *
 * Stores dynamic configuration for MCP tools that can be configured
 * through Web UI or API without writing code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mcp_tools_config")
public class McpToolConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_name", nullable = false, unique = true, length = 100)
    private String toolName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "input_params", columnDefinition = "JSON")
    private String inputParams;

    @Column(name = "output_format", columnDefinition = "JSON")
    private String outputFormat;

    @Column(name = "tool_type", nullable = false, length = 50)
    private String toolType;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "auth_type", length = 50)
    private String authType;

    @Column(name = "auth_config", columnDefinition = "JSON")
    private String authConfig;

    @Column(name = "request_template", columnDefinition = "JSON")
    private String requestTemplate;

    @Column(name = "response_mapping", length = 200)
    private String responseMapping;

    @Column(name = "headers", columnDefinition = "JSON")
    private String headers;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (httpMethod == null) {
            httpMethod = "GET";
        }
        if (authType == null) {
            authType = "NONE";
        }
        if (timeoutMs == null) {
            timeoutMs = 30000;
        }
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}