package com.drs.agent.service;

import com.drs.agent.entity.McpToolConfig;
import com.drs.agent.mcp.McpToolRegistry;
import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.ToolConfigRequest;
import com.drs.agent.model.ToolConfigResponse;
import com.drs.agent.repository.McpToolConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MCP Tool Configuration Service
 *
 * Handles CRUD operations and testing for dynamic MCP tool configurations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolConfigService {

    private final McpToolConfigRepository toolConfigRepository;
    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Get all tools (static + dynamic).
     *
     * @return List of all tools
     */
    public List<ToolConfigResponse> getAllTools() {
        // Get all tools from registry
        List<Map<String, Object>> registryTools = toolRegistry.getAvailableTools();

        return registryTools.stream()
                .map(tool -> {
                    String name = (String) tool.get("name");
                    Boolean isDynamic = (Boolean) tool.get("isDynamic");

                    if (Boolean.TRUE.equals(isDynamic)) {
                        // Get full config from database
                        return toolConfigRepository.findByToolName(name)
                                .map(config -> ToolConfigResponse.fromEntity(config, true))
                                .orElse(null);
                    } else {
                        // Create response from registry info for static tools
                        return ToolConfigResponse.builder()
                                .toolName(name)
                                .description((String) tool.get("description"))
                                .inputParams(tool.get("inputParams"))
                                .outputFormat(tool.get("outputFormat"))
                                .toolType("STATIC")
                                .isDynamic(false)
                                .build();
                    }
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific tool by name.
     *
     * @param toolName Tool name
     * @return Tool configuration or null
     */
    public Optional<ToolConfigResponse> getTool(String toolName) {
        // Check if it's a dynamic tool
        if (toolRegistry.isDynamicTool(toolName)) {
            return toolConfigRepository.findByToolName(toolName)
                    .map(config -> ToolConfigResponse.fromEntity(config, true));
        } else if (toolRegistry.hasTool(toolName)) {
            // Static tool
            Map<String, Object> toolInfo = toolRegistry.getToolInfo(toolName);
            if (toolInfo != null) {
                return Optional.of(ToolConfigResponse.builder()
                        .toolName(toolName)
                        .description((String) toolInfo.get("description"))
                        .inputParams(toolInfo.get("inputParams"))
                        .outputFormat(toolInfo.get("outputFormat"))
                        .toolType("STATIC")
                        .isDynamic(false)
                        .build());
            }
        }
        return Optional.empty();
    }

    /**
     * Create a new dynamic tool.
     *
     * @param request Tool configuration request
     * @return Created tool configuration
     */
    @Transactional
    public ToolConfigResponse createTool(ToolConfigRequest request) {
        log.info("Creating dynamic tool: {}", request.getToolName());

        // Validate tool name uniqueness
        if (toolRegistry.hasTool(request.getToolName())) {
            throw new IllegalArgumentException("Tool name already exists: " + request.getToolName());
        }

        // Build entity
        McpToolConfig config = McpToolConfig.builder()
                .toolName(request.getToolName())
                .description(request.getDescription())
                .inputParams(toJson(request.getInputParams()))
                .outputFormat(request.getOutputFormat())
                .toolType(request.getToolType())
                .endpointUrl(request.getEndpointUrl())
                .httpMethod(request.getHttpMethod() != null ? request.getHttpMethod() : "GET")
                .authType(request.getAuthType() != null ? request.getAuthType() : "NONE")
                .authConfig(toJson(request.getAuthConfig()))
                .requestTemplate(request.getRequestTemplate())
                .responseMapping(request.getResponseMapping())
                .headers(toJson(request.getHeaders()))
                .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        // Save to database
        config = toolConfigRepository.save(config);

        // Register in tool registry
        toolRegistry.registerDynamicTool(config);

        log.info("Dynamic tool created successfully: {}", config.getToolName());
        return ToolConfigResponse.fromEntity(config, true);
    }

    /**
     * Update an existing dynamic tool.
     *
     * @param toolName Tool name
     * @param request Update request
     * @return Updated tool configuration
     */
    @Transactional
    public Optional<ToolConfigResponse> updateTool(String toolName, ToolConfigRequest request) {
        log.info("Updating dynamic tool: {}", toolName);

        return toolConfigRepository.findByToolName(toolName).map(config -> {
            // Update fields
            if (request.getDescription() != null) {
                config.setDescription(request.getDescription());
            }
            if (request.getInputParams() != null) {
                config.setInputParams(toJson(request.getInputParams()));
            }
            if (request.getOutputFormat() != null) {
                config.setOutputFormat(request.getOutputFormat());
            }
            if (request.getToolType() != null) {
                config.setToolType(request.getToolType());
            }
            if (request.getEndpointUrl() != null) {
                config.setEndpointUrl(request.getEndpointUrl());
            }
            if (request.getHttpMethod() != null) {
                config.setHttpMethod(request.getHttpMethod());
            }
            if (request.getAuthType() != null) {
                config.setAuthType(request.getAuthType());
            }
            if (request.getAuthConfig() != null) {
                config.setAuthConfig(toJson(request.getAuthConfig()));
            }
            if (request.getRequestTemplate() != null) {
                config.setRequestTemplate(request.getRequestTemplate());
            }
            if (request.getResponseMapping() != null) {
                config.setResponseMapping(request.getResponseMapping());
            }
            if (request.getHeaders() != null) {
                config.setHeaders(toJson(request.getHeaders()));
            }
            if (request.getTimeoutMs() != null) {
                config.setTimeoutMs(request.getTimeoutMs());
            }
            if (request.getEnabled() != null) {
                config.setEnabled(request.getEnabled());
            }

            // Save to database
            config = toolConfigRepository.save(config);

            // Re-register in tool registry
            toolRegistry.registerDynamicTool(config);

            log.info("Dynamic tool updated successfully: {}", toolName);
            return ToolConfigResponse.fromEntity(config, true);
        });
    }

    /**
     * Delete a dynamic tool.
     *
     * @param toolName Tool name
     * @return true if deleted
     */
    @Transactional
    public boolean deleteTool(String toolName) {
        log.info("Deleting dynamic tool: {}", toolName);

        if (!toolRegistry.isDynamicTool(toolName)) {
            log.warn("Cannot delete static tool: {}", toolName);
            return false;
        }

        // Remove from registry
        toolRegistry.removeDynamicTool(toolName);

        // Delete from database
        toolConfigRepository.deleteByToolName(toolName);

        log.info("Dynamic tool deleted successfully: {}", toolName);
        return true;
    }

    /**
     * Test a tool with sample parameters.
     *
     * @param toolName Tool name
     * @param params Test parameters
     * @return Tool execution result
     */
    public ToolResult testTool(String toolName, Map<String, Object> params) {
        log.info("Testing tool: {} with params: {}", toolName, params);
        return toolRegistry.testTool(toolName, params);
    }

    /**
     * Refresh dynamic tools from database.
     */
    public void refreshTools() {
        toolRegistry.refreshDynamicTools();
    }

    /**
     * Convert object to JSON string.
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }
}