package com.drs.agent.mcp;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MCP Tool Annotation
 *
 * Marks a class as an MCP (Model Context Protocol) tool.
 * Classes annotated with @McpTool will be automatically discovered
 * and registered by the McpToolRegistry.
 *
 * Usage example:
 * <pre>
 * &#64;McpTool(
 *     name = "weather_query",
 *     description = "Query weather information for a given location",
 *     inputParams = "[{\"name\":\"location\",\"type\":\"string\",\"required\":true,\"description\":\"City name\"}]",
 *     outputFormat = "{\"temperature\":\"number\",\"humidity\":\"number\",\"condition\":\"string\"}"
 * )
 * &#64;Component
 * public class WeatherTool implements McpToolHandler {
 *     // implementation
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface McpTool {

    /**
     * Unique name of the tool.
     * This name is used to identify and invoke the tool.
     *
     * @return the tool name
     */
    String name();

    /**
     * Human-readable description of what the tool does.
     * This description helps the AI understand when to use the tool.
     *
     * @return the tool description
     */
    String description();

    /**
     * Input parameter definitions in JSON format.
     * Format: [{"name":"param1","type":"string","required":true,"description":"...","defaultValue":"..."}]
     *
     * @return JSON array of parameter definitions
     */
    String inputParams() default "[]";

    /**
     * Output format definition in JSON format.
     * Describes the structure of the tool's output.
     *
     * @return JSON object describing output format
     */
    String outputFormat() default "{}";
}