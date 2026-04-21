package com.drs.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Model Configuration Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigRequest {

    private String modelName;
    private String provider;
    private String apiEndpoint;
    private String apiKey;
    private String modelId;
    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Boolean isDefault;
    private Boolean enabled;
    private Object extraParams;
}