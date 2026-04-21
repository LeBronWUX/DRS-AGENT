package com.drs.agent.service;

import com.drs.agent.entity.AiModelConfig;
import com.drs.agent.repository.AiModelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * AI Model Configuration Service
 *
 * Manages multiple AI model configurations and provides model switching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper;

    private AiModelConfig currentDefaultModel;
    private WebClient currentWebClient;

    /**
     * Get all model configurations.
     */
    public List<AiModelConfig> getAllModels() {
        return modelConfigRepository.findAll();
    }

    /**
     * Get enabled models only.
     */
    public List<AiModelConfig> getEnabledModels() {
        return modelConfigRepository.findByEnabled(true);
    }

    /**
     * Get a specific model by name.
     */
    public Optional<AiModelConfig> getModel(String modelName) {
        return modelConfigRepository.findByModelName(modelName);
    }

    /**
     * Get the current default model.
     */
    public Optional<AiModelConfig> getDefaultModel() {
        return modelConfigRepository.findByIsDefaultTrueAndEnabledTrue().stream().findFirst();
    }

    /**
     * Create a new model configuration.
     */
    @Transactional
    public AiModelConfig createModel(AiModelConfig config) {
        log.info("Creating model configuration: {}", config.getModelName());

        if (modelConfigRepository.existsByModelName(config.getModelName())) {
            throw new IllegalArgumentException("Model name already exists: " + config.getModelName());
        }

        // If this is set as default, unset other defaults
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            unsetOtherDefaults();
        }

        return modelConfigRepository.save(config);
    }

    /**
     * Update an existing model configuration.
     */
    @Transactional
    public Optional<AiModelConfig> updateModel(String modelName, AiModelConfig updates) {
        return modelConfigRepository.findByModelName(modelName).map(config -> {
            if (updates.getApiEndpoint() != null) config.setApiEndpoint(updates.getApiEndpoint());
            if (updates.getApiKey() != null) config.setApiKey(updates.getApiKey());
            if (updates.getModelId() != null) config.setModelId(updates.getModelId());
            if (updates.getMaxTokens() != null) config.setMaxTokens(updates.getMaxTokens());
            if (updates.getTemperature() != null) config.setTemperature(updates.getTemperature());
            if (updates.getTopP() != null) config.setTopP(updates.getTopP());
            if (updates.getTimeoutSeconds() != null) config.setTimeoutSeconds(updates.getTimeoutSeconds());
            if (updates.getMaxRetries() != null) config.setMaxRetries(updates.getMaxRetries());
            if (updates.getEnabled() != null) config.setEnabled(updates.getEnabled());
            if (updates.getExtraParams() != null) config.setExtraParams(updates.getExtraParams());

            // Handle default model setting
            if (Boolean.TRUE.equals(updates.getIsDefault()) && !Boolean.TRUE.equals(config.getIsDefault())) {
                unsetOtherDefaults();
                config.setIsDefault(true);
            }

            return modelConfigRepository.save(config);
        });
    }

    /**
     * Set a model as default.
     */
    @Transactional
    public boolean setDefaultModel(String modelName) {
        Optional<AiModelConfig> model = modelConfigRepository.findByModelName(modelName);
        if (model.isEmpty()) return false;

        unsetOtherDefaults();
        AiModelConfig config = model.get();
        config.setIsDefault(true);
        config.setEnabled(true);
        modelConfigRepository.save(config);

        currentDefaultModel = config;
        log.info("Set default model to: {}", modelName);
        return true;
    }

    /**
     * Delete a model configuration.
     */
    @Transactional
    public boolean deleteModel(String modelName) {
        if (!modelConfigRepository.existsByModelName(modelName)) return false;
        modelConfigRepository.deleteByModelName(modelName);
        log.info("Deleted model: {}", modelName);
        return true;
    }

    /**
     * Test model connection by sending a simple request.
     */
    public Map<String, Object> testModel(String modelName) {
        Optional<AiModelConfig> modelOpt = modelConfigRepository.findByModelName(modelName);
        if (modelOpt.isEmpty()) {
            return Map.of("success", false, "error", "Model not found: " + modelName);
        }

        AiModelConfig model = modelOpt.get();
        return testModelConnection(model);
    }

    /**
     * Test model connection with custom parameters.
     */
    public Map<String, Object> testModelConnection(AiModelConfig model) {
        log.info("Testing model: {}", model.getModelName());

        try {
            WebClient client = buildWebClient(model);
            String requestBody = buildTestRequestBody(model);

            String response = client.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info("Model test successful: {}", model.getModelName());
            return Map.of(
                    "success", true,
                    "modelName", model.getModelName(),
                    "provider", model.getProvider(),
                    "modelId", model.getModelId(),
                    "responsePreview", response != null && response.length() > 200
                            ? response.substring(0, 200) + "..." : response
            );

        } catch (Exception e) {
            log.error("Model test failed: {}", model.getModelName(), e);
            return Map.of(
                    "success", false,
                    "modelName", model.getModelName(),
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Unset all other default models.
     */
    private void unsetOtherDefaults() {
        modelConfigRepository.findAllByIsDefaultTrue().forEach(m -> {
            m.setIsDefault(false);
            modelConfigRepository.save(m);
        });
    }

    /**
     * Build WebClient for the model.
     */
    private WebClient buildWebClient(AiModelConfig model) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(model.getApiEndpoint())
                .defaultHeader("Content-Type", "application/json");

        // Add auth headers based on provider
        String provider = model.getProvider().toUpperCase();
        switch (provider) {
            case "CLAUDE":
                if (model.getApiKey() != null) {
                    builder.defaultHeader("x-api-key", model.getApiKey());
                    builder.defaultHeader("anthropic-version", "2023-06-01");
                }
                break;
            case "GLM":
                if (model.getApiKey() != null) {
                    builder.defaultHeader("Authorization", "Bearer " + model.getApiKey());
                }
                break;
            case "OPENAI":
                if (model.getApiKey() != null) {
                    builder.defaultHeader("Authorization", "Bearer " + model.getApiKey());
                }
                break;
            default:
                if (model.getApiKey() != null) {
                    builder.defaultHeader("Authorization", "Bearer " + model.getApiKey());
                }
        }

        return builder.build();
    }

    /**
     * Build test request body based on provider.
     */
    private String buildTestRequestBody(AiModelConfig model) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model.getModelId());
        request.put("max_tokens", 50);

        String provider = model.getProvider().toUpperCase();
        switch (provider) {
            case "CLAUDE":
                request.put("messages", List.of(Map.of("role", "user", "content", "Say 'OK' if you can respond.")));
                break;
            case "GLM":
                request.put("messages", List.of(Map.of("role", "user", "content", "请回复OK")));
                request.put("temperature", model.getTemperature());
                request.put("top_p", model.getTopP());
                break;
            case "OPENAI":
                request.put("messages", List.of(Map.of("role", "user", "content", "Say OK")));
                request.put("temperature", model.getTemperature());
                break;
            default:
                request.put("messages", List.of(Map.of("role", "user", "content", "Hello")));
        }

        return objectMapper.writeValueAsString(request);
    }

    /**
     * Get supported providers list.
     */
    public List<Map<String, Object>> getSupportedProviders() {
        return List.of(
                Map.of("name", "GLM", "displayName", "智谱AI (GLM)",
                        "models", List.of("glm-4-plus", "glm-4-0520", "glm-4-air", "glm-4-airx", "glm-4-flash", "glm-5", "glm-4.7"),
                        "defaultEndpoint", "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                Map.of("name", "CLAUDE", "displayName", "Anthropic Claude",
                        "models", List.of("claude-opus-4-7", "claude-sonnet-4-6", "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307"),
                        "defaultEndpoint", "https://api.anthropic.com/v1/messages"),
                Map.of("name", "OPENAI", "displayName", "OpenAI",
                        "models", List.of("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo", "o1-preview"),
                        "defaultEndpoint", "https://api.openai.com/v1/chat/completions")
        );
    }
}