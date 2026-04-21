package com.drs.agent.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wiki Document DTO
 *
 * Represents a wiki document search result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WikiDoc {

    private String docId;
    private String title;
    private String content;
    private String category;
    private List<String> tags;
    private String url;
    private Double relevanceScore;
    private String lastUpdated;
}