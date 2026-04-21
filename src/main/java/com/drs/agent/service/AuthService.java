package com.drs.agent.service;

import com.drs.agent.entity.UserAccount;
import com.drs.agent.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication Service
 *
 * Simple token-based authentication for admin access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;

    // In-memory token store (session tokens)
    private final Map<String, UserSession> tokenStore = new ConcurrentHashMap<>();

    // Token expiration time (24 hours)
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000;

    /**
     * Initialize default admin account if not exists.
     */
    @Transactional
    public void initDefaultAdmin() {
        if (!userAccountRepository.existsByUsername("admin")) {
            // Create default admin with password "admin123"
            String passwordHash = hashPassword("admin123");
            UserAccount admin = UserAccount.builder()
                    .username("admin")
                    .passwordHash(passwordHash)
                    .role("ADMIN")
                    .displayName("系统管理员")
                    .enabled(true)
                    .build();
            userAccountRepository.save(admin);
            log.info("Default admin account created with password: admin123");
        }
    }

    /**
     * Login with username and password.
     *
     * @param username Username
     * @param password Password
     * @return Login response with token if successful
     */
    public Map<String, Object> login(String username, String password) {
        log.info("Login attempt for user: {}", username);

        Optional<UserAccount> userOpt = userAccountRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", username);
            return Map.of("success", false, "message", "用户不存在");
        }

        UserAccount user = userOpt.get();

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("User disabled: {}", username);
            return Map.of("success", false, "message", "账号已被禁用");
        }

        if (!verifyPassword(password, user.getPasswordHash())) {
            log.warn("Password incorrect for user: {}", username);
            return Map.of("success", false, "message", "密码错误");
        }

        // Generate token
        String token = generateToken();
        UserSession session = new UserSession(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getDisplayName(),
                System.currentTimeMillis() + TOKEN_EXPIRY_MS
        );
        tokenStore.put(token, session);

        // Update last login time
        user.setLastLogin(LocalDateTime.now());
        userAccountRepository.save(user);

        log.info("User logged in successfully: {}", username);

        return Map.of(
                "success", true,
                "message", "登录成功",
                "token", token,
                "username", user.getUsername(),
                "role", user.getRole(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
        );
    }

    /**
     * Logout and invalidate token.
     */
    public boolean logout(String token) {
        if (token != null && tokenStore.remove(token) != null) {
            log.info("Token invalidated: {}", token.substring(0, 8));
            return true;
        }
        return false;
    }

    /**
     * Validate token and get user info.
     */
    public Optional<UserSession> validateToken(String token) {
        if (token == null) return Optional.empty();

        UserSession session = tokenStore.get(token);
        if (session == null) return Optional.empty();

        if (session.expiryTime < System.currentTimeMillis()) {
            tokenStore.remove(token);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    /**
     * Check if user is admin.
     */
    public boolean isAdmin(String token) {
        return validateToken(token)
                .map(s -> "ADMIN".equals(s.role))
                .orElse(false);
    }

    /**
     * Get current user info from token.
     */
    public Optional<Map<String, Object>> getCurrentUser(String token) {
        return validateToken(token).map(session -> Map.of(
                "username", session.username,
                "role", session.role,
                "displayName", session.displayName
        ));
    }

    /**
     * Generate random token.
     */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Simple password hashing (for demo purposes).
     * In production, use BCrypt or similar.
     */
    private String hashPassword(String password) {
        // Simple hash for demo - use BCrypt in production
        return "hash:" + Base64.getEncoder().encodeToString(password.getBytes());
    }

    /**
     * Verify password against hash.
     */
    private boolean verifyPassword(String password, String hash) {
        if (hash.startsWith("hash:")) {
            String encoded = hash.substring(5);
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            String decoded = new String(decodedBytes);
            return password.equals(decoded);
        }
        // For BCrypt-style hashes, would use BCrypt.checkpw()
        return false;
    }

    /**
     * User session info.
     */
    public record UserSession(
            Long userId,
            String username,
            String role,
            String displayName,
            long expiryTime
    ) {}
}