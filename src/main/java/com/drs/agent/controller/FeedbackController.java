package com.drs.agent.controller;

import com.drs.agent.model.FeedbackRequest;
import com.drs.agent.service.ExperienceLearningService;
import com.drs.agent.service.dto.PendingConfirmation;
import com.drs.agent.service.dto.UserFeedback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FeedbackController - 用户反馈接口
 *
 * 处理用户反馈相关的API端点：
 * - POST /v1/feedback/{sessionId} - 提交反馈
 * - POST /v1/learning/confirm/{confirmationId} - 确认并录入
 * - GET /v1/learning/pending - 待确认列表
 * - GET /v1/learning/confirm/{confirmationId} - 获取确认详情
 * - POST /v1/learning/experience/{experienceId}/rate - 评分更新
 * - POST /v1/learning/experience/{experienceId}/optimize - 优化低分经验
 */
@RestController
@RequestMapping("/v1")
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);

    private final ExperienceLearningService learningService;

    public FeedbackController(ExperienceLearningService learningService) {
        this.learningService = learningService;
    }

    /**
     * 提交反馈
     *
     * POST /v1/feedback/{sessionId}
     *
     * @param sessionId 会话ID
     * @param feedback  反馈请求
     * @return 反馈处理结果
     */
    @PostMapping("/feedback/{sessionId}")
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

        try {
            // Process feedback
            learningService.processFeedback(sessionId, feedback);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Feedback submitted and processed successfully");
            result.put("sessionId", sessionId);

            // Check if confirmation was triggered
            if (!Boolean.TRUE.equals(feedback.getIsCorrect()) && feedback.getActualRootCause() != null) {
                result.put("experienceAdded", true);
                result.put("message", "Feedback processed. New experience added to knowledge base.");
            }

            return ResponseEntity.ok(result);

        } catch (ExperienceLearningService.SessionNotFoundException e) {
            logger.warn("Session not found: {}", sessionId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Session not found: " + sessionId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to process feedback", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process feedback: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 确认并录入经验
     *
     * POST /v1/learning/confirm/{confirmationId}
     *
     * @param confirmationId 确认任务ID
     * @param userFeedback   用户反馈
     * @return 确认结果
     */
    @PostMapping("/learning/confirm/{confirmationId}")
    public ResponseEntity<Map<String, Object>> confirmAndLearn(
            @PathVariable String confirmationId,
            @RequestBody UserFeedback userFeedback) {
        logger.info("Processing confirmation: {}, isCorrect: {}, rating: {}",
                confirmationId, userFeedback.isCorrect(), userFeedback.getRating());

        // Validate rating
        if (userFeedback.getRating() < 1 || userFeedback.getRating() > 5) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Rating must be between 1 and 5");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // Confirm and learn
            learningService.confirmAndLearn(confirmationId, userFeedback);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("confirmationId", confirmationId);
            result.put("message", "Confirmation processed. Experience added to knowledge base.");

            return ResponseEntity.ok(result);

        } catch (ExperienceLearningService.ConfirmationNotFoundException e) {
            logger.warn("Confirmation not found: {}", confirmationId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Confirmation not found: " + confirmationId);
            return ResponseEntity.notFound().build();

        } catch (ExperienceLearningService.ConfirmationExpiredException e) {
            logger.warn("Confirmation expired: {}", confirmationId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Confirmation has expired. Please submit a new diagnosis.");
            errorResponse.put("expired", true);
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (ExperienceLearningService.SessionNotFoundException e) {
            logger.warn("Session not found for confirmation: {}", confirmationId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Associated session not found");
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Failed to process confirmation", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process confirmation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取待确认列表
     *
     * GET /v1/learning/pending
     *
     * @return 待确认任务列表
     */
    @GetMapping("/learning/pending")
    public ResponseEntity<Map<String, Object>> getPendingConfirmations() {
        logger.info("Retrieving pending confirmations");

        try {
            List<PendingConfirmation> pendingList = learningService.getPendingConfirmations();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", pendingList.size());
            result.put("pendingConfirmations", pendingList);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to retrieve pending confirmations", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve pending confirmations: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取确认详情
     *
     * GET /v1/learning/confirm/{confirmationId}
     *
     * @param confirmationId 确认任务ID
     * @return 确认任务详情
     */
    @GetMapping("/learning/confirm/{confirmationId}")
    public ResponseEntity<Map<String, Object>> getConfirmationDetails(
            @PathVariable String confirmationId) {
        logger.info("Retrieving confirmation details: {}", confirmationId);

        Optional<PendingConfirmation> confirmation = learningService.getConfirmation(confirmationId);

        if (confirmation.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("confirmation", confirmation.get());
            return ResponseEntity.ok(result);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Confirmation not found: " + confirmationId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 评分更新经验权重
     *
     * POST /v1/learning/experience/{experienceId}/rate
     *
     * @param experienceId 经验ID
     * @param rating       评分(1-5)
     * @param comment      评论
     * @return 更新结果
     */
    @PostMapping("/learning/experience/{experienceId}/rate")
    public ResponseEntity<Map<String, Object>> rateExperience(
            @PathVariable String experienceId,
            @RequestParam Integer rating,
            @RequestParam(required = false) String comment) {
        logger.info("Rating experience: {}, rating: {}", experienceId, rating);

        // Validate rating
        if (rating == null || rating < 1 || rating > 5) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Rating must be between 1 and 5");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            learningService.updateExperienceScore(experienceId, rating, comment);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("experienceId", experienceId);
            result.put("rating", rating);
            result.put("message", "Experience rating updated successfully");

            return ResponseEntity.ok(result);

        } catch (ExperienceLearningService.ExperienceNotFoundException e) {
            logger.warn("Experience not found: {}", experienceId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Experience not found: " + experienceId);
            return ResponseEntity.notFound().build();

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid rating: {}", rating);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            logger.error("Failed to rate experience", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to rate experience: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 优化低分经验
     *
     * POST /v1/learning/optimize
     *
     * @return 优化结果
     */
    @PostMapping("/learning/optimize")
    public ResponseEntity<Map<String, Object>> optimizeLowScoreExperiences() {
        logger.info("Starting low score experience optimization");

        try {
            learningService.optimizeLowScoreExperiences();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Low score experience optimization completed");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to optimize experiences", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to optimize experiences: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 清理过期确认任务
     *
     * DELETE /v1/learning/pending/expired
     *
     * @return 清理结果
     */
    @DeleteMapping("/learning/pending/expired")
    public ResponseEntity<Map<String, Object>> cleanupExpiredConfirmations() {
        logger.info("Cleaning up expired confirmations");

        try {
            learningService.cleanupExpiredConfirmations();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Expired confirmations cleaned up");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to cleanup expired confirmations", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to cleanup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}