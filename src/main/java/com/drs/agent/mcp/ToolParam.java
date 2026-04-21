package com.drs.agent.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool Parameter Definition
 *
 * Represents a parameter definition for an MCP tool.
 * Used to describe the expected input parameters for tool execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolParam {

    /**
     * Name of the parameter.
     */
    private String name;

    /**
     * Type of the parameter (string, number, boolean, object, array).
     */
    private String type;

    /**
     * Whether this parameter is required.
     */
    private boolean required;

    /**
     * Human-readable description of the parameter.
     */
    private String description;

    /**
     * Default value if parameter is not provided.
     */
    private Object defaultValue;

    /**
     * JSON Schema for nested object validation.
     */
    private String schema;

    /**
     * Allowed values for enum-type parameters.
     */
    private Object[] enumValues;

    /**
     * Minimum value for numeric parameters.
     */
    private Number minimum;

    /**
     * Maximum value for numeric parameters.
     */
    private Number maximum;

    /**
     * Minimum length for string parameters.
     */
    private Integer minLength;

    /**
     * Maximum length for string parameters.
     */
    private Integer maxLength;

    /**
     * Regular expression pattern for string validation.
     */
    private String pattern;

    /**
     * Create a simple required string parameter.
     *
     * @param name Parameter name
     * @param description Parameter description
     * @return ToolParam instance
     */
    public static ToolParam requiredString(String name, String description) {
        return ToolParam.builder()
                .name(name)
                .type("string")
                .required(true)
                .description(description)
                .build();
    }

    /**
     * Create an optional string parameter with default value.
     *
     * @param name Parameter name
     * @param description Parameter description
     * @param defaultValue Default value
     * @return ToolParam instance
     */
    public static ToolParam optionalString(String name, String description, String defaultValue) {
        return ToolParam.builder()
                .name(name)
                .type("string")
                .required(false)
                .description(description)
                .defaultValue(defaultValue)
                .build();
    }

    /**
     * Create a required number parameter.
     *
     * @param name Parameter name
     * @param description Parameter description
     * @return ToolParam instance
     */
    public static ToolParam requiredNumber(String name, String description) {
        return ToolParam.builder()
                .name(name)
                .type("number")
                .required(true)
                .description(description)
                .build();
    }

    /**
     * Create a required boolean parameter.
     *
     * @param name Parameter name
     * @param description Parameter description
     * @return ToolParam instance
     */
    public static ToolParam requiredBoolean(String name, String description) {
        return ToolParam.builder()
                .name(name)
                .type("boolean")
                .required(true)
                .description(description)
                .build();
    }

    /**
     * Create an enum parameter with allowed values.
     *
     * @param name Parameter name
     * @param description Parameter description
     * @param enumValues Allowed values
     * @return ToolParam instance
     */
    public static ToolParam enumParam(String name, String description, Object[] enumValues) {
        return ToolParam.builder()
                .name(name)
                .type("string")
                .required(true)
                .description(description)
                .enumValues(enumValues)
                .build();
    }
}