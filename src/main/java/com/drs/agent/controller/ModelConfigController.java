package com.drs.agent.controller;

import com.drs.agent.entity.AiModelConfig;
import com.drs.agent.model.ModelConfigRequest;
import com.drs.agent.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Model Configuration Controller
 *
 * REST API for managing AI model configurations.
 */
@Slf4j
@RestController
@RequestMapping("/v1/models")
@RequiredArgsConstructor
public class ModelConfigController {

    private final AiModelConfigService modelConfigService;

    /**
     * Get all model configurations.
     */
    @GetMapping
    public ResponseEntity<List<AiModelConfig>> getAllModels() {
        List<AiModelConfig> models = modelConfigService.getAllModels();
        return ResponseEntity.ok(models);
    }

    /**
     * Get enabled models.
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<AiModelConfig>> getEnabledModels() {
        List<AiModelConfig> models = modelConfigService.getEnabledModels();
        return ResponseEntity.ok(models);
    }

    /**
     * Get the default model.
     */
    @GetMapping("/default")
    public ResponseEntity<?> getDefaultModel() {
        return modelConfigService.getDefaultModel()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get supported providers info.
     */
    @GetMapping("/providers")
    public ResponseEntity<List<Map<String, Object>>> getSupportedProviders() {
        return ResponseEntity.ok(modelConfigService.getSupportedProviders());
    }

    /**
     * Get a specific model.
     */
    @GetMapping("/{modelName}")
    public ResponseEntity<?> getModel(@PathVariable String modelName) {
        return modelConfigService.getModel(modelName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new model configuration.
     */
    @PostMapping
    public ResponseEntity<?> createModel(@RequestBody ModelConfigRequest request) {
        log.info("Creating model: {}", request.getModelName());

        if (request.getModelName() == null || request.getModelName().isBlank()) {
            return badRequest("Model name is required");
        }
        if (request.getProvider() == null || request.getProvider().isBlank()) {
            return badRequest("Provider is required");
        }
        if (request.getApiEndpoint() == null || request.getApiEndpoint().isBlank()) {
            return badRequest("API endpoint is required");
        }
        if (request.getModelId() == null || request.getModelId().isBlank()) {
            return badRequest("Model ID is required");
        }

        AiModelConfig config = AiModelConfig.builder()
                .modelName(request.getModelName())
                .provider(request.getProvider())
                .apiEndpoint(request.getApiEndpoint())
                .apiKey(request.getApiKey())
                .modelId(request.getModelId())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .timeoutSeconds(request.getTimeoutSeconds())
                .maxRetries(request.getMaxRetries())
                .isDefault(request.getIsDefault())
                .enabled(request.getEnabled())
                .build();

        try {
            AiModelConfig created = modelConfigService.createModel(config);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    /**
     * Update a model configuration.
     */
    @PutMapping("/{modelName}")
    public ResponseEntity<?> updateModel(@PathVariable String modelName, @RequestBody ModelConfigRequest request) {
        log.info("Updating model: {}", modelName);

        AiModelConfig updates = AiModelConfig.builder()
                .apiEndpoint(request.getApiEndpoint())
                .apiKey(request.getApiKey())
                .modelId(request.getModelId())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .timeoutSeconds(request.getTimeoutSeconds())
                .maxRetries(request.getMaxRetries())
                .isDefault(request.getIsDefault())
                .enabled(request.getEnabled())
                .build();

        return modelConfigService.updateModel(modelName, updates)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Set a model as default.
     */
    @PostMapping("/{modelName}/default")
    public ResponseEntity<Map<String, Object>> setDefaultModel(@PathVariable String modelName) {
        log.info("Setting default model: {}", modelName);

        boolean success = modelConfigService.setDefaultModel(modelName);
        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("success", true);
            response.put("message", "Default model set to: " + modelName);
            response.put("modelName", modelName);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Model not found: " + modelName);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Delete a model configuration.
     */
    @DeleteMapping("/{modelName}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable String modelName) {
        log.info("Deleting model: {}", modelName);

        boolean deleted = modelConfigService.deleteModel(modelName);
        Map<String, Object> response = new HashMap<>();
        if (deleted) {
            response.put("success", true);
            response.put("message", "Model deleted: " + modelName);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Model not found: " + modelName);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Test model connection.
     */
    @PostMapping("/{modelName}/test")
    public ResponseEntity<Map<String, Object>> testModel(@PathVariable String modelName) {
        log.info("Testing model: {}", modelName);
        Map<String, Object> result = modelConfigService.testModel(modelName);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}