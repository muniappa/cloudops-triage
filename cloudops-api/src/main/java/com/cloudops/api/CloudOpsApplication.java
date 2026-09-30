package com.cloudops.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * CloudOps Triage Platform — Spring Boot entry point.
 *
 * <p>{@code @EnableTransactionManagement} activates proxy-based transaction
 * management so {@code @Transactional} on service and repository methods is
 * honoured. Spring Boot auto-configures a {@code JpaTransactionManager} backed
 * by the Hikari connection pool, bound to the same {@code EntityManagerFactory}
 * used by Spring Data JPA repositories.
 */
@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
@EntityScan(basePackages = "com.cloudops.domain.model")
@EnableJpaRepositories(basePackages = "com.cloudops.repository")
@ComponentScan(basePackages = {
    "com.cloudops.api",
    "com.cloudops.service",
    "com.cloudops.repository"
})
public class CloudOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudOpsApplication.class, args);
    }
}
