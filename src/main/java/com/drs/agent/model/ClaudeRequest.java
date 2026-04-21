package com.drs.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude API请求模型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    @JsonProperty("messages")
    private List<ClaudeMessage> messages;

    @JsonProperty("system")
    private String system;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("stream")
    private Boolean stream;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    @JsonProperty("tools")
    private List<Tool> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    private ClaudeRequest() {
        this.messages = new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<ClaudeMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ClaudeMessage> messages) {
        this.messages = messages;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    /**
     * 工具定义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("input_schema")
        private Object inputSchema;

        public Tool(String name, String description, Object inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Object inputSchema) {
            this.inputSchema = inputSchema;
        }
    }

    /**
     * Builder模式
     */
    public static class Builder {
        private final ClaudeRequest request;

        public Builder() {
            request = new ClaudeRequest();
        }

        public Builder model(String model) {
            request.model = model;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            request.maxTokens = maxTokens;
            return this;
        }

        public Builder system(String system) {
            request.system = system;
            return this;
        }

        public Builder messages(List<ClaudeMessage> messages) {
            request.messages = messages;
            return this;
        }

        public Builder addMessage(ClaudeMessage message) {
            request.messages.add(message);
            return this;
        }

        public Builder addMessage(ClaudeMessage.Role role, String text) {
            request.messages.add(new ClaudeMessage(role, text));
            return this;
        }

        public Builder temperature(Double temperature) {
            request.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            request.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            request.topK = topK;
            return this;
        }

        public Builder stream(Boolean stream) {
            request.stream = stream;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            request.stopSequences = stopSequences;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            request.tools = tools;
            return this;
        }

        public Builder toolChoice(Object toolChoice) {
            request.toolChoice = toolChoice;
            return this;
        }

        public ClaudeRequest build() {
            return request;
        }
    }
}