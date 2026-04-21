package com.drs.agent.controller;

import com.drs.agent.model.LoginRequest;
import com.drs.agent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login endpoint.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        log.info("Login request for user: {}", request.getUsername());
        Map<String, Object> result = authService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(result);
    }

    /**
     * Logout endpoint.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String token = extractToken(request);
        boolean success = authService.logout(token);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "已退出登录" : "无效的token");
        return ResponseEntity.ok(response);
    }

    /**
     * Get current user info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        return authService.getCurrentUser(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "未登录"
                )));
    }

    /**
     * Check if authenticated.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAuth(HttpServletRequest request) {
        String token = extractToken(request);
        boolean isAuthenticated = authService.validateToken(token).isPresent();
        boolean isAdmin = authService.isAdmin(token);

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", isAuthenticated);
        response.put("isAdmin", isAdmin);
        return ResponseEntity.ok(response);
    }

    /**
     * Extract token from request header.
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}