package com.drs.agent.mcp;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * System Information Tool
 *
 * Provides system and environment information.
 * This tool demonstrates a read-only tool with no input parameters.
 */
@Slf4j
@McpTool(
    name = "system_info",
    description = "Retrieves system and runtime information including Java version, OS details, memory usage, and system properties.",
    inputParams = "[]",
    outputFormat = "{\"javaVersion\":\"string\",\"osName\":\"string\",\"osVersion\":\"string\",\"availableProcessors\":\"number\",\"freeMemory\":\"number\",\"totalMemory\":\"number\"}"
)
public class SystemInfoTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing system_info tool");

        try {
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> systemInfo = new HashMap<>();

            // Java information
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("javaVendor", System.getProperty("java.vendor"));
            systemInfo.put("javaHome", System.getProperty("java.home"));

            // OS information
            systemInfo.put("osName", System.getProperty("os.name"));
            systemInfo.put("osVersion", System.getProperty("os.version"));
            systemInfo.put("osArch", System.getProperty("os.arch"));
            systemInfo.put("userDir", System.getProperty("user.dir"));
            systemInfo.put("userHome", System.getProperty("user.home"));

            // Memory information
            systemInfo.put("availableProcessors", runtime.availableProcessors());
            systemInfo.put("freeMemory", runtime.freeMemory());
            systemInfo.put("totalMemory", runtime.totalMemory());
            systemInfo.put("maxMemory", runtime.maxMemory());

            // Timestamp
            systemInfo.put("timestamp", Instant.now().toString());

            log.info("System info retrieved successfully");
            return ToolResult.success(systemInfo);

        } catch (Exception e) {
            log.error("Error retrieving system info", e);
            return ToolResult.failure("Failed to retrieve system information", e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        // This tool has no required parameters
        return ValidationResult.success();
    }
}