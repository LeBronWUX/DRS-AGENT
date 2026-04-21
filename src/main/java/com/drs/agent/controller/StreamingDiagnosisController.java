package com.drs.agent.controller;

import com.drs.agent.model.DiagnosisRequest;
import com.drs.agent.service.StreamingDiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Streaming Diagnosis Controller
 *
 * Provides real-time diagnosis process output via SSE (Server-Sent Events).
 * Each step of the diagnosis is pushed to the client as it happens.
 */
@Slf4j
@RestController
@RequestMapping("/v1/diagnose")
@RequiredArgsConstructor
public class StreamingDiagnosisController {

    private final StreamingDiagnosisService streamingDiagnosisService;

    /**
     * Streaming diagnosis endpoint.
     * Returns SSE stream with real-time diagnosis progress.
     *
     * @param request Diagnosis request
     * @return SseEmitter for streaming events
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDiagnosis(@RequestBody DiagnosisRequest request) {
        log.info("Starting streaming diagnosis for problem: {}", request.getProblem());

        // Create SseEmitter with timeout of 5 minutes
        SseEmitter emitter = new SseEmitter(300000L);

        // Handle completion and errors
        emitter.onCompletion(() -> log.debug("SSE completed for session"));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout");
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE error: {}", e.getMessage());
            emitter.completeWithError(e);
        });

        // Start streaming diagnosis asynchronously
        streamingDiagnosisService.streamDiagnosisSse(request, emitter);

        return emitter;
    }
}