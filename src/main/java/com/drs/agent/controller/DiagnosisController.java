package com.drs.agent.controller;

import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.model.DiagnosisResponse;
import com.drs.agent.model.FeedbackRequest;
import com.drs.agent.service.DiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Diagnosis Controller
 *
 * Handles diagnosis-related API endpoints:
 * - POST /v1/diagnose - Trigger diagnosis
 * - GET /v1/diagnose/{sessionId} - Get diagnosis result
 * - POST /v1/diagnose/{sessionId}/feedback - Submit feedback
 */
@RestController
@RequestMapping("/v1")
public class DiagnosisController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisController.class);

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * Trigger a new diagnosis
     *
     * @param request Diagnosis request containing problem description
     * @return Diagnosis response with session ID and results
     */
    @PostMapping("/diagnose")
    public ResponseEntity<DiagnosisResponse> diagnose(@RequestBody DiagnosisRequest request) {
        logger.info("Received diagnosis request for problem: {}",
                request.getProblem() != null && request.getProblem().length() > 100
                        ? request.getProblem().substring(0, 100) + "..."
                        : request.getProblem());

        // Validate request
        if (request.getProblem() == null || request.getProblem().trim().isEmpty()) {
            DiagnosisResponse errorResponse = DiagnosisResponse.builder()
                    .status("FAILED")
                    .errorMessage("Problem description is required")
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Execute diagnosis
        DiagnosisResponse response = diagnosisService.diagnose(request);

        if ("COMPLETED".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get diagnosis result by session ID
     *
     * @param sessionId The session ID
     * @return Diagnosis response
     */
    @GetMapping("/diagnose/{sessionId}")
    public ResponseEntity<DiagnosisResponse> getDiagnosisResult(@PathVariable String sessionId) {
        logger.info("Getting diagnosis result for session: {}", sessionId);

        return diagnosisService.getDiagnosisResult(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Submit feedback for a diagnosis
     *
     * @param sessionId The session ID
     * @param feedback Feedback request containing rating and comments
     * @return Updated diagnosis response
     */
    @PostMapping("/diagnose/{sessionId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable String sessionId,
            @RequestBody FeedbackRequest feedback) {
        logger.info("Received feedback for session: {}, rating: {}, isCorrect: {}",
                sessionId, feedback.getRating(), feedback.getIsCorrect());

        // Validate feedback
        if (feedback.getRating() != null && (feedback.getRating() < 1 || feedback.getRating() > 5)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Rating must be between 1 and 5");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return diagnosisService.submitFeedback(sessionId, feedback)
                .map(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Feedback submitted successfully");
                    result.put("sessionId", sessionId);
                    result.put("status", response.getStatus());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}