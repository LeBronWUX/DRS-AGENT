package com.drs.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Claude API客户端配置类
 * 用于配置Claude SDK的连接参数
 */
@Configuration
public class ClaudeConfig {

    /**
     * Claude API密钥，从环境变量ANTHROPIC_API_KEY读取
     */
    @Value("${anthropic.api.key:${ANTHROPIC_API_KEY:}}")
    private String apiKey;

    /**
     * 默认使用的Claude模型
     */
    @Value("${anthropic.model:claude-sonnet-4-6}")
    private String defaultModel;

    /**
     * API请求超时时间(秒)
     */
    @Value("${anthropic.timeout:120}")
    private int timeoutSeconds;

    /**
     * 最大重试次数
     */
    @Value("${anthropic.max-retries:3}")
    private int maxRetries;

    /**
     * 默认最大token数
     */
    @Value("${anthropic.max-tokens:4096}")
    private int maxTokens;

    /**
     * 创建Claude客户端配置Bean
     */
    @Bean
    public ClaudeClientConfig claudeClientConfig() {
        return ClaudeClientConfig.builder()
                .apiKey(apiKey)
                .defaultModel(defaultModel)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .maxTokens(maxTokens)
                .build();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Claude客户端配置内部类
     */
    public static class ClaudeClientConfig {
        private final String apiKey;
        private final String defaultModel;
        private final Duration timeout;
        private final int maxRetries;
        private final int maxTokens;

        private ClaudeClientConfig(Builder builder) {
            this.apiKey = builder.apiKey;
            this.defaultModel = builder.defaultModel;
            this.timeout = builder.timeout;
            this.maxRetries = builder.maxRetries;
            this.maxTokens = builder.maxTokens;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public static class Builder {
            private String apiKey;
            private String defaultModel = "claude-sonnet-4-6";
            private Duration timeout = Duration.ofSeconds(120);
            private int maxRetries = 3;
            private int maxTokens = 4096;

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder defaultModel(String defaultModel) {
                this.defaultModel = defaultModel;
                return this;
            }

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder maxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public ClaudeClientConfig build() {
                return new ClaudeClientConfig(this);
            }
        }
    }
}