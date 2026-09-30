package com.cloudops.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the React dev server (default: http://localhost:5173) and any
 * configured production origin to call the API.
 *
 * The allowed origin is externalised via {@code cloudops.cors.allowed-origins}
 * so production deployments can lock it down without code changes.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cloudops.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
