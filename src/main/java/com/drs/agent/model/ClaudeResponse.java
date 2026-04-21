package com.drs.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Claude API响应模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private ClaudeMessage.Content[] content;

    @JsonProperty("model")
    private String model;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    @JsonProperty("usage")
    private Usage usage;

    /**
     * 获取文本内容
     */
    public String getTextContent() {
        if (content == null || content.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ClaudeMessage.Content c : content) {
            if (c instanceof ClaudeMessage.TextContent) {
                sb.append(((ClaudeMessage.TextContent) c).getText());
            }
        }
        return sb.toString();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public ClaudeMessage.Content[] getContent() {
        return content;
    }

    public void setContent(ClaudeMessage.Content[] content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(String stopSequence) {
        this.stopSequence = stopSequence;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /**
     * Token使用量
     */
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;

        @JsonProperty("output_tokens")
        private int outputTokens;

        @JsonProperty("cache_creation_input_tokens")
        private Integer cacheCreationInputTokens;

        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;

        public int getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
        }

        public int getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
        }

        public Integer getCacheCreationInputTokens() {
            return cacheCreationInputTokens;
        }

        public void setCacheCreationInputTokens(Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
        }

        public Integer getCacheReadInputTokens() {
            return cacheReadInputTokens;
        }

        public void setCacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
        }
    }
}