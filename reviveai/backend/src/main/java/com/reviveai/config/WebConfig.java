package com.reviveai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Without this, the React dev server (a different origin: localhost:5173
 * vs. the API's localhost:8080) cannot call the API at all — the browser
 * blocks the request before it even reaches the network tab as a readable
 * error. reviveai.frontend.origin is configurable via CORS_ALLOWED_ORIGIN
 * for whatever origin the built frontend is actually served from in
 * non-local environments.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ReviveAiProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getFrontend().getOrigin())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
