package com.drs.agent.mcp;

import com.drs.agent.mcp.dto.WikiDoc;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Search Wiki Tool
 *
 * Searches wiki documents for keyword matches.
 * Currently implements a mock version - TODO: integrate with real wiki/knowledge base.
 */
@Slf4j
@McpTool(
    name = "search_wiki",
    description = "Search wiki documents for keyword matches to find relevant documentation and runbooks. Use this tool to find troubleshooting guides and documentation.",
    inputParams = "[{\"name\":\"keywords\",\"type\":\"array\",\"required\":true,\"description\":\"Keywords to search in wiki documents\"},{\"name\":\"category\",\"type\":\"string\",\"required\":false,\"description\":\"Filter by document category\"},{\"name\":\"limit\",\"type\":\"number\",\"required\":false,\"description\":\"Maximum number of documents to return\",\"defaultValue\":10}]",
    outputFormat = "{\"success\":\"boolean\",\"count\":\"number\",\"keywords\":\"array\",\"data\":\"array\"}"
)
public class SearchWikiTool implements McpToolHandler {

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("Executing search_wiki tool with parameters: {}", parameters);

        try {
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) parameters.get("keywords");

            if (keywords == null || keywords.isEmpty()) {
                return ToolResult.failure("keywords are required");
            }

            String category = (String) parameters.get("category");
            Integer limit = parameters.containsKey("limit")
                    ? ((Number) parameters.get("limit")).intValue()
                    : 10;

            // TODO: Integrate with real wiki/knowledge base system
            List<WikiDoc> docs = searchMockWiki(keywords, category, limit);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("count", docs.size());
            result.put("keywords", keywords);
            result.put("data", docs);

            log.info("Search wiki tool executed successfully, found {} documents", docs.size());
            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("Error executing search_wiki tool", e);
            return ToolResult.failure("Error searching wiki: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(Map<String, Object> parameters) {
        Object keywords = parameters.get("keywords");
        if (keywords == null) {
            return ValidationResult.failure("Missing required parameter: keywords");
        }
        if (!(keywords instanceof List)) {
            return ValidationResult.failure("Parameter 'keywords' must be an array");
        }
        if (((List<?>) keywords).isEmpty()) {
            return ValidationResult.failure("Parameter 'keywords' cannot be empty");
        }
        return ValidationResult.success();
    }

    /**
     * Search mock wiki documents for testing.
     * TODO: Replace with real wiki/knowledge base integration.
     */
    private List<WikiDoc> searchMockWiki(List<String> keywords, String category, int limit) {
        // Mock wiki documents database
        List<WikiDoc> mockDocs = new ArrayList<>();

        mockDocs.add(WikiDoc.builder()
                .docId("wiki-001")
                .title("Database Connection Timeout Troubleshooting")
                .content("This document covers common causes and solutions for database connection timeout errors. " +
                        "Check network connectivity, verify firewall rules, and ensure database server is running.")
                .category("troubleshooting")
                .tags(Arrays.asList("database", "timeout", "connection", "network"))
                .url("https://wiki.internal/docs/database-timeout-troubleshooting")
                .relevanceScore(0.95)
                .lastUpdated("2024-01-15")
                .build());

        mockDocs.add(WikiDoc.builder()
                .docId("wiki-002")
                .title("Service Health Check Runbook")
                .content("Standard procedures for checking service health including log analysis, " +
                        "metric monitoring, and dependency verification.")
                .category("runbook")
                .tags(Arrays.asList("health-check", "monitoring", "runbook", "service"))
                .url("https://wiki.internal/docs/service-health-runbook")
                .relevanceScore(0.87)
                .lastUpdated("2024-01-20")
                .build());

        mockDocs.add(WikiDoc.builder()
                .docId("wiki-003")
                .title("Error Code Reference Guide")
                .content("Comprehensive list of error codes and their meanings. " +
                        "ERR_TIMEOUT: Connection timeout error. ERR_NOT_FOUND: Resource not found.")
                .category("reference")
                .tags(Arrays.asList("error-codes", "reference", "debugging"))
                .url("https://wiki.internal/docs/error-code-reference")
                .relevanceScore(0.82)
                .lastUpdated("2024-01-10")
                .build());

        mockDocs.add(WikiDoc.builder()
                .docId("wiki-004")
                .title("Kubernetes Pod Troubleshooting")
                .content("Guide for troubleshooting Kubernetes pod issues including crash loops, " +
                        "OOM killed pods, and network policy issues.")
                .category("troubleshooting")
                .tags(Arrays.asList("kubernetes", "pod", "container", "troubleshooting"))
                .url("https://wiki.internal/docs/k8s-pod-troubleshooting")
                .relevanceScore(0.78)
                .lastUpdated("2024-01-18")
                .build());

        mockDocs.add(WikiDoc.builder()
                .docId("wiki-005")
                .title("API Gateway Error Handling")
                .content("Documentation for API gateway error handling patterns, " +
                        "circuit breaker configuration, and fallback strategies.")
                .category("architecture")
                .tags(Arrays.asList("api-gateway", "circuit-breaker", "error-handling"))
                .url("https://wiki.internal/docs/api-gateway-errors")
                .relevanceScore(0.75)
                .lastUpdated("2024-01-12")
                .build());

        // Filter by category if specified
        List<WikiDoc> filtered = mockDocs;
        if (category != null && !category.isBlank()) {
            filtered = mockDocs.stream()
                    .filter(doc -> category.equals(doc.getCategory()))
                    .toList();
        }

        // Score based on keyword matches
        List<WikiDoc> scored = filtered.stream()
                .map(doc -> {
                    double score = calculateRelevanceScore(doc, keywords);
                    doc.setRelevanceScore(score);
                    return doc;
                })
                .filter(doc -> doc.getRelevanceScore() > 0.3)
                .sorted(Comparator.comparingDouble(WikiDoc::getRelevanceScore).reversed())
                .limit(limit)
                .toList();

        return scored;
    }

    /**
     * Calculate relevance score based on keyword matches.
     */
    private double calculateRelevanceScore(WikiDoc doc, List<String> keywords) {
        String content = (doc.getTitle() + " " + doc.getContent() + " " +
                String.join(" ", doc.getTags())).toLowerCase();

        int matches = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase())) {
                matches++;
            }
        }

        return (double) matches / keywords.size();
    }
}