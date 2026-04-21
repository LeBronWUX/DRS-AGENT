package com.drs.agent.config;

import com.drs.agent.service.AuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Initialize default admin account on startup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminInitializer {

    private final AuthService authService;

    @PostConstruct
    public void init() {
        log.info("Initializing default admin account...");
        authService.initDefaultAdmin();
    }
}