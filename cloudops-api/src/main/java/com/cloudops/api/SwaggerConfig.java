package com.cloudops.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration.
 * Accessible at: http://localhost:8080/swagger-ui.html
 * JSON spec at:  http://localhost:8080/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI cloudOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudOps Triage API")
                        .description("""
                                Autonomous incident triage platform for microservice health monitoring.
                                Provides endpoints for managing monitored services, ingesting health signals,
                                triaging incidents, and reviewing AI-generated remediation suggestions.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CloudOps Platform Team")
                                .email("cloudops-team@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development server")
                ));
    }
}
