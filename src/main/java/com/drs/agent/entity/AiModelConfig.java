package com.drs.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Model Configuration Entity
 *
 * Stores configuration for different AI model providers (Claude, GLM, OpenAI, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_model_config")
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, unique = true, length = 100)
    private String modelName;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "api_endpoint", nullable = false, length = 500)
    private String apiEndpoint;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "extra_params", columnDefinition = "JSON")
    private String extraParams;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (maxTokens == null) maxTokens = 4096;
        if (temperature == null) temperature = 0.7;
        if (topP == null) topP = 0.9;
        if (timeoutSeconds == null) timeoutSeconds = 120;
        if (maxRetries == null) maxRetries = 3;
        if (isDefault == null) isDefault = false;
        if (enabled == null) enabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}