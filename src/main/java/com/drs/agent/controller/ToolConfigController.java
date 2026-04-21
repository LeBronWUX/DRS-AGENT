package com.drs.agent.controller;

import com.drs.agent.mcp.ToolResult;
import com.drs.agent.model.ToolConfigRequest;
import com.drs.agent.model.ToolConfigResponse;
import com.drs.agent.model.ToolTestRequest;
import com.drs.agent.service.McpToolConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool Configuration Controller
 *
 * REST API endpoints for managing MCP tools.
 * Allows creating, updating, deleting, and testing dynamic tools.
 */
@Slf4j
@RestController
@RequestMapping("/v1/tools")
@RequiredArgsConstructor
public class ToolConfigController {

    private final McpToolConfigService toolConfigService;

    /**
     * Get all tools (static + dynamic).
     *
     * @return List of all tools
     */
    @GetMapping
    public ResponseEntity<List<ToolConfigResponse>> getAllTools() {
        log.info("Getting all tools");
        List<ToolConfigResponse> tools = toolConfigService.getAllTools();
        return ResponseEntity.ok(tools);
    }

    /**
     * Get a specific tool by name.
     *
     * @param toolName Tool name
     * @return Tool configuration
     */
    @GetMapping("/{toolName}")
    public ResponseEntity<ToolConfigResponse> getTool(@PathVariable String toolName) {
        log.info("Getting tool: {}", toolName);
        return toolConfigService.getTool(toolName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new dynamic tool.
     *
     * @param request Tool configuration request
     * @return Created tool
     */
    @PostMapping
    public ResponseEntity<?> createTool(@RequestBody ToolConfigRequest request) {
        log.info("Creating tool: {}", request.getToolName());

        // Validate request
        if (request.getToolName() == null || request.getToolName().isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Tool name is required");
            return ResponseEntity.badRequest().body(error);
        }

        if (request.getToolType() == null || request.getToolType().isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Tool type is required");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            ToolConfigResponse created = toolConfigService.createTool(request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error creating tool: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create tool: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Update an existing dynamic tool.
     *
     * @param toolName Tool name
     * @param request Update request
     * @return Updated tool
     */
    @PutMapping("/{toolName}")
    public ResponseEntity<?> updateTool(@PathVariable String toolName, @RequestBody ToolConfigRequest request) {
        log.info("Updating tool: {}", toolName);

        try {
            return toolConfigService.updateTool(toolName, request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error updating tool: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to update tool: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Delete a dynamic tool.
     *
     * @param toolName Tool name
     * @return Success response
     */
    @DeleteMapping("/{toolName}")
    public ResponseEntity<Map<String, Object>> deleteTool(@PathVariable String toolName) {
        log.info("Deleting tool: {}", toolName);

        boolean deleted = toolConfigService.deleteTool(toolName);

        Map<String, Object> response = new HashMap<>();
        if (deleted) {
            response.put("success", true);
            response.put("message", "Tool deleted successfully");
            response.put("toolName", toolName);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Cannot delete tool (not found or is static)");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Test a tool with sample parameters.
     *
     * @param toolName Tool name
     * @param request Test request with parameters
     * @return Tool execution result
     */
    @PostMapping("/{toolName}/test")
    public ResponseEntity<ToolResult> testTool(@PathVariable String toolName, @RequestBody ToolTestRequest request) {
        log.info("Testing tool: {} with params: {}", toolName, request.getParams());

        Map<String, Object> params = request.getParams() != null ? request.getParams() : new HashMap<>();
        ToolResult result = toolConfigService.testTool(toolName, params);

        return ResponseEntity.ok(result);
    }

    /**
     * Refresh dynamic tools from database.
     *
     * @return Success response
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshTools() {
        log.info("Refreshing dynamic tools");

        toolConfigService.refreshTools();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Tools refreshed successfully");
        return ResponseEntity.ok(response);
    }
}