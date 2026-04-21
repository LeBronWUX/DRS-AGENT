package com.drs.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Frontend Static Resources Configuration
 *
 * Serves frontend static files from the backend.
 * Enables single-process deployment without separate frontend server.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve frontend static files with SPA fallback
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/frontend/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(
                            String resourcePath, org.springframework.core.io.Resource location) throws IOException {
                        org.springframework.core.io.Resource requestedResource = location.createRelative(resourcePath);

                        // If resource exists and is a file, return it
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // For SPA routing, return index.html for non-API paths
                        // API paths (/api/**, /v1/**, /actuator/**) are handled by controllers
                        if (!resourcePath.startsWith("api/") &&
                            !resourcePath.startsWith("v1/") &&
                            !resourcePath.startsWith("actuator/") &&
                            !resourcePath.startsWith("h2-console")) {
                            return new ClassPathResource("/frontend/index.html");
                        }

                        return null;
                    }
                });
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward root path to index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}