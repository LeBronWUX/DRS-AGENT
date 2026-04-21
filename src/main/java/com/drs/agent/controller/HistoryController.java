package com.drs.agent.controller;

import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.service.DiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * History Controller
 *
 * Handles diagnosis history API endpoints:
 * - GET /v1/history - Query diagnosis history
 * - GET /v1/history/{sessionId} - Get history detail
 * - GET /v1/history/stats - Get statistics
 */
@RestController
@RequestMapping("/v1/history")
public class HistoryController {

    private static final Logger logger = LoggerFactory.getLogger(HistoryController.class);

    private final DiagnosisService diagnosisService;

    public HistoryController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * Query diagnosis history with pagination and filtering
     *
     * @param userId Filter by user ID (optional)
     * @param problemType Filter by problem type (optional)
     * @param startDate Filter by start date (optional)
     * @param endDate Filter by end date (optional)
     * @param page Page number (default 0)
     * @param size Page size (default 10)
     * @return Page of diagnosis history
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        logger.info("Querying history - userId: {}, problemType: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                userId, problemType, startDate, endDate, page, size);

        // Validate pagination parameters
        if (page < 0) {
            page = 0;
        }
        if (size < 1 || size > 100) {
            size = 10;
        }

        Page<DiagnosisResponse> historyPage = diagnosisService.getDiagnosisHistory(
                userId, problemType, startDate, endDate, page, size);

        // Convert to summary format
        List<Map<String, Object>> summaries = historyPage.getContent().stream()
                .map(this::convertToSummary)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", summaries);
        response.put("totalElements", historyPage.getTotalElements());
        response.put("totalPages", historyPage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get diagnosis history detail by session ID
     *
     * @param sessionId Session ID
     * @return Diagnosis detail
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<DiagnosisResponse> getHistoryDetail(@PathVariable String sessionId) {
        logger.info("Getting history detail for session: {}", sessionId);

        return diagnosisService.getDiagnosisResult(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get diagnosis statistics
     *
     * @return Statistics data
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        logger.info("Getting diagnosis statistics");

        Map<String, Object> stats = diagnosisService.getStatistics();

        // Add additional computed statistics
        Long totalDiagnoses = (Long) stats.getOrDefault("totalDiagnoses", 0L);
        Long correctDiagnoses = (Long) stats.getOrDefault("correctDiagnoses", 0L);

        if (totalDiagnoses > 0 && correctDiagnoses > 0) {
            double accuracy = (double) correctDiagnoses / totalDiagnoses * 100;
            stats.put("accuracy", Math.round(accuracy * 100.0) / 100.0);
        } else {
            stats.put("accuracy", 0.0);
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Get recent diagnosis history (last N records)
     *
     * @param limit Number of records to return (default 5, max 20)
     * @return List of recent diagnoses
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentHistory(
            @RequestParam(defaultValue = "5") int limit) {

        logger.info("Getting recent history, limit: {}", limit);

        // Validate limit
        if (limit < 1 || limit > 20) {
            limit = 5;
        }

        Page<DiagnosisResponse> historyPage = diagnosisService.getDiagnosisHistory(
                null, null, null, null, 0, limit);

        List<Map<String, Object>> recent = historyPage.getContent().stream()
                .map(this::convertToSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(recent);
    }

    /**
     * Get history summary by user
     *
     * @param userId User ID
     * @return User's diagnosis history summary
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserHistorySummary(@PathVariable String userId) {
        logger.info("Getting history summary for user: {}", userId);

        Page<DiagnosisResponse> historyPage = diagnosisService.getDiagnosisHistory(
                userId, null, null, null, 0, 100);

        Map<String, Object> summary = new HashMap<>();
        summary.put("userId", userId);
        summary.put("totalDiagnoses", historyPage.getTotalElements());

        // Calculate problem type distribution
        Map<String, Long> problemTypeCount = historyPage.getContent().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getProblemType() != null ? r.getProblemType() : "UNKNOWN",
                        Collectors.counting()
                ));
        summary.put("problemTypeDistribution", problemTypeCount);

        // Calculate average confidence
        double avgConfidence = historyPage.getContent().stream()
                .filter(r -> r.getConfidence() != null)
                .mapToDouble(DiagnosisResponse::getConfidence)
                .average()
                .orElse(0.0);
        summary.put("averageConfidence", Math.round(avgConfidence * 100.0) / 100.0);

        return ResponseEntity.ok(summary);
    }

    /**
     * Convert DiagnosisResponse to summary map
     */
    private Map<String, Object> convertToSummary(DiagnosisResponse response) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("sessionId", response.getSessionId());
        summary.put("problemType", response.getProblemType());
        summary.put("confidence", response.getConfidence());
        summary.put("status", response.getStatus());

        // Truncate root cause for summary
        if (response.getRootCause() != null && response.getRootCause().length() > 100) {
            summary.put("rootCausePreview", response.getRootCause().substring(0, 100) + "...");
        } else {
            summary.put("rootCausePreview", response.getRootCause());
        }

        return summary;
    }
}