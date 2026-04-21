package com.drs.agent.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Alert Context DTO
 *
 * Represents alert context and related resources.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertContext {

    private String alertId;
    private String alertName;
    private String severity;
    private String status;
    private String source;
    private String service;
    private String environment;
    private LocalDateTime triggerTime;
    private String summary;
    private String description;
    private Map<String, Object> labels;
    private Map<String, Object> annotations;
    private List<RelatedResource> relatedResources;
    private List<String> runbookLinks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedResource {
        private String type;
        private String name;
        private String relationship;
        private Map<String, Object> properties;
    }
}