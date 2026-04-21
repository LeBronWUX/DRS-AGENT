package com.drs.agent.config;

import com.drs.agent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web Configuration for authentication interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthService authService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(authService))
                // Admin-only paths (require authentication)
                .addPathPatterns("/v1/models/**", "/v1/tools/**", "/v1/experiences/**", "/v1/history/**")
                // Public paths (no auth required)
                .excludePathPatterns("/v1/auth/**", "/v1/diagnose/**", "/actuator/**", "/h2-console/**");
    }

    /**
     * Authentication interceptor.
     */
    static class AuthInterceptor implements HandlerInterceptor {

        private final AuthService authService;

        AuthInterceptor(AuthService authService) {
            this.authService = authService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String token = extractToken(request);

            if (!authService.validateToken(token).isPresent()) {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"请先登录\",\"code\":401}");
                return false;
            }

            return true;
        }

        private String extractToken(HttpServletRequest request) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return null;
        }
    }
}