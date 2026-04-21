package com.drs.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude消息模型
 * 支持文本和多模态内容
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeMessage {

    /**
     * 消息角色
     */
    @JsonProperty("role")
    private Role role;

    /**
     * 消息内容列表，支持多模态
     */
    @JsonProperty("content")
    private List<Content> content;

    public ClaudeMessage() {
        this.content = new ArrayList<>();
    }

    public ClaudeMessage(Role role, String text) {
        this.role = role;
        this.content = new ArrayList<>();
        this.content.add(new TextContent(text));
    }

    public ClaudeMessage(Role role, List<Content> content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 添加文本内容
     */
    public ClaudeMessage addText(String text) {
        if (this.content == null) {
            this.content = new ArrayList<>();
        }
        this.content.add(new TextContent(text));
        return this;
    }

    /**
     * 添加图片内容(Base64编码)
     */
    public ClaudeMessage addImage(String base64Data, String mediaType) {
        if (this.content == null) {
            this.content = new ArrayList<>();
        }
        this.content.add(new ImageContent(base64Data, mediaType));
        return this;
    }

    /**
     * 添加图片内容(URL)
     */
    public ClaudeMessage addImageUrl(String url) {
        if (this.content == null) {
            this.content = new ArrayList<>();
        }
        this.content.add(new ImageContent(url));
        return this;
    }

    // Getters and Setters
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public List<Content> getContent() {
        return content;
    }

    public void setContent(List<Content> content) {
        this.content = content;
    }

    /**
     * 消息角色枚举
     */
    public enum Role {
        @JsonProperty("user")
        USER("user"),

        @JsonProperty("assistant")
        ASSISTANT("assistant"),

        @JsonProperty("system")
        SYSTEM("system");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 内容基类
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class Content {
        @JsonProperty("type")
        private final String type;

        protected Content(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * 文本内容
     */
    public static class TextContent extends Content {
        @JsonProperty("text")
        private final String text;

        public TextContent(String text) {
            super("text");
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 图片内容
     */
    public static class ImageContent extends Content {
        @JsonProperty("source")
        private final ImageSource source;

        public ImageContent(String base64Data, String mediaType) {
            super("image");
            this.source = new ImageSource("base64", mediaType, base64Data);
        }

        public ImageContent(String url) {
            super("image");
            this.source = new ImageSource(url);
        }

        public ImageSource getSource() {
            return source;
        }
    }

    /**
     * 图片源
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageSource {
        @JsonProperty("type")
        private final String type;

        @JsonProperty("media_type")
        private String mediaType;

        @JsonProperty("data")
        private String data;

        @JsonProperty("url")
        private String url;

        public ImageSource(String type, String mediaType, String data) {
            this.type = type;
            this.mediaType = mediaType;
            this.data = data;
        }

        public ImageSource(String url) {
            this.type = "url";
            this.url = url;
        }

        public String getType() {
            return type;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getData() {
            return data;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * 创建用户消息
     */
    public static ClaudeMessage userMessage(String text) {
        return new ClaudeMessage(Role.USER, text);
    }

    /**
     * 创建助手消息
     */
    public static ClaudeMessage assistantMessage(String text) {
        return new ClaudeMessage(Role.ASSISTANT, text);
    }

    /**
     * 创建系统消息
     */
    public static ClaudeMessage systemMessage(String text) {
        return new ClaudeMessage(Role.SYSTEM, text);
    }
}