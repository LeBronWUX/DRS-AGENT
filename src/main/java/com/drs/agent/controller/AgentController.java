package com.drs.agent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent Controller
 *
 * Handles AI agent conversation requests and task execution.
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Chat endpoint ready");
        response.put("request", request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> executeTask(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Task execution endpoint ready");
        response.put("request", request);
        return ResponseEntity.ok(response);
    }
}