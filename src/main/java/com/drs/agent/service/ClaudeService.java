package com.drs.agent.service;

import com.drs.agent.config.ClaudeConfig;
import com.drs.agent.model.ClaudeMessage;
import com.drs.agent.model.ClaudeRequest;
import com.drs.agent.model.ClaudeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Claude服务类
 * 提供与Claude API交互的核心功能
 */
@Service
public class ClaudeService {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeService.class);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    private final ClaudeConfig claudeConfig;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    public ClaudeService(ClaudeConfig claudeConfig, ObjectMapper objectMapper) {
        this.claudeConfig = claudeConfig;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        this.webClient = WebClient.builder()
                .baseUrl(CLAUDE_API_URL)
                .defaultHeader("x-api-key", config.getApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        logger.info("ClaudeService initialized with model: {}", config.getDefaultModel());
    }

    /**
     * 发送单条消息并获取响应
     *
     * @param userMessage 用户消息内容
     * @return Claude响应
     */
    public ClaudeResponse sendMessage(String userMessage) {
        return sendMessage(userMessage, null);
    }

    /**
     * 发送单条消息并获取响应（带系统提示词）
     *
     * @param userMessage   用户消息内容
     * @param systemPrompt  系统提示词
     * @return Claude响应
     */
    public ClaudeResponse sendMessage(String userMessage, String systemPrompt) {
        List<ClaudeMessage> messages = new ArrayList<>();
        messages.add(ClaudeMessage.userMessage(userMessage));
        return sendMessages(messages, systemPrompt);
    }

    /**
     * 发送消息列表并获取响应
     *
     * @param messages  消息列表
     * @return Claude响应
     */
    public ClaudeResponse sendMessages(List<ClaudeMessage> messages) {
        return sendMessages(messages, null);
    }

    /**
     * 发送消息列表并获取响应（带系统提示词）
     *
     * @param messages      消息列表
     * @param systemPrompt  系统提示词
     * @return Claude响应
     */
    public ClaudeResponse sendMessages(List<ClaudeMessage> messages, String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .messages(messages)
                .build();

        return executeRequest(request);
    }

    /**
     * 带历史对话的消息发送
     *
     * @param userMessage   用户消息内容
     * @param history       历史对话记录
     * @param systemPrompt  系统提示词
     * @return Claude响应
     */
    public ClaudeResponse sendMessagesWithHistory(String userMessage,
                                                   List<ClaudeMessage> history,
                                                   String systemPrompt) {
        List<ClaudeMessage> messages = new ArrayList<>(history);
        messages.add(ClaudeMessage.userMessage(userMessage));
        return sendMessages(messages, systemPrompt);
    }

    /**
     * 异步发送消息
     *
     * @param userMessage 用户消息内容
     * @return 异步响应
     */
    public CompletableFuture<ClaudeResponse> sendMessageAsync(String userMessage) {
        return sendMessageAsync(userMessage, null);
    }

    /**
     * 异步发送消息（带系统提示词）
     *
     * @param userMessage   用户消息内容
     * @param systemPrompt  系统提示词
     * @return 异步响应
     */
    public CompletableFuture<ClaudeResponse> sendMessageAsync(String userMessage, String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .addMessage(ClaudeMessage.userMessage(userMessage))
                .build();

        return executeRequestAsync(request);
    }

    /**
     * 异步发送消息列表
     *
     * @param messages      消息列表
     * @param systemPrompt  系统提示词
     * @return 异步响应
     */
    public CompletableFuture<ClaudeResponse> sendMessagesAsync(List<ClaudeMessage> messages,
                                                                String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .messages(messages)
                .build();

        return executeRequestAsync(request);
    }

    /**
     * 使用指定模型发送消息
     *
     * @param userMessage   用户消息
     * @param model         指定模型
     * @param systemPrompt  系统提示词
     * @return Claude响应
     */
    public ClaudeResponse sendMessageWithModel(String userMessage, String model, String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeRequest request = ClaudeRequest.builder()
                .model(model != null ? model : config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .addMessage(ClaudeMessage.userMessage(userMessage))
                .build();

        return executeRequest(request);
    }

    /**
     * 流式响应（用于长文本生成）
     *
     * @param userMessage   用户消息
     * @param systemPrompt  系统提示词
     * @return 流式响应
     */
    public Flux<String> sendMessageStream(String userMessage, String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .addMessage(ClaudeMessage.userMessage(userMessage))
                .stream(true)
                .build();

        return executeStreamRequest(request);
    }

    /**
     * 执行API请求
     */
    private ClaudeResponse executeRequest(ClaudeRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            logger.debug("Sending request to Claude API: {}", requestBody);

            String responseBody = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(claudeConfig.getTimeoutSeconds()))
                    .block();

            logger.debug("Received response from Claude API: {}", responseBody);
            return objectMapper.readValue(responseBody, ClaudeResponse.class);

        } catch (Exception e) {
            logger.error("Error calling Claude API: {}", e.getMessage(), e);
            throw new ClaudeServiceException("Failed to call Claude API", e);
        }
    }

    /**
     * 异步执行API请求
     */
    private CompletableFuture<ClaudeResponse> executeRequestAsync(ClaudeRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            logger.debug("Sending async request to Claude API: {}", requestBody);

            return webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(claudeConfig.getTimeoutSeconds()))
                    .map(responseBody -> {
                        try {
                            logger.debug("Received async response from Claude API: {}", responseBody);
                            return objectMapper.readValue(responseBody, ClaudeResponse.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse response", e);
                        }
                    })
                    .toFuture();

        } catch (Exception e) {
            logger.error("Error creating async request to Claude API: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new ClaudeServiceException("Failed to create async request", e));
        }
    }

    /**
     * 执行流式API请求
     */
    private Flux<String> executeStreamRequest(ClaudeRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            logger.debug("Sending stream request to Claude API: {}", requestBody);

            return webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(claudeConfig.getTimeoutSeconds()));

        } catch (Exception e) {
            logger.error("Error creating stream request to Claude API: {}", e.getMessage(), e);
            return Flux.error(new ClaudeServiceException("Failed to create stream request", e));
        }
    }

    /**
     * 多模态消息发送（支持文本和图片）
     *
     * @param textMessage   文本消息
     * @param imageBase64   图片Base64编码
     * @param mediaType     图片类型（如image/png）
     * @param systemPrompt  系统提示词
     * @return Claude响应
     */
    public ClaudeResponse sendMultimodalMessage(String textMessage,
                                                  String imageBase64,
                                                  String mediaType,
                                                  String systemPrompt) {
        ClaudeConfig.ClaudeClientConfig config = claudeConfig.claudeClientConfig();

        ClaudeMessage message = new ClaudeMessage(ClaudeMessage.Role.USER, textMessage);
        message.addImage(imageBase64, mediaType);

        List<ClaudeMessage> messages = new ArrayList<>();
        messages.add(message);

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getDefaultModel())
                .maxTokens(config.getMaxTokens())
                .system(systemPrompt)
                .messages(messages)
                .build();

        return executeRequest(request);
    }

    /**
     * Claude服务异常
     */
    public static class ClaudeServiceException extends RuntimeException {
        public ClaudeServiceException(String message) {
            super(message);
        }

        public ClaudeServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}