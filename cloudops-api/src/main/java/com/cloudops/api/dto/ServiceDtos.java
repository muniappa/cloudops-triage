package com.cloudops.api.dto;

import com.cloudops.domain.enums.ServiceHealthStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class ServiceDtos {

    // ── Request DTOs ───────────────────────────────────────────────────────

    @Schema(description = "Request body to register a new monitored service")
    public record RegisterServiceRequest(
            @Schema(description = "Unique display name of the service", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String name,
            @Schema(description = "Name of the team responsible for this service", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String teamOwner,
            @Schema(description = "Optional human-readable description of what the service does")
            String description
    ) {}

    @Schema(description = "Request body to ingest a single health metric reading")
    public record IngestSignalRequest(
            @Schema(description = "Type of metric being reported (e.g. CPU_USAGE, ERROR_RATE)", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull com.cloudops.domain.enums.MetricType metricType,
            @Schema(description = "Numeric metric value (e.g. 95.4 for 95.4% CPU)", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull double value
    ) {}

    @Schema(description = "Request body to manually update a service's health status")
    public record UpdateStatusRequest(
            @Schema(description = "New health status for the service", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ServiceHealthStatus status
    ) {}

    // ── Response DTOs ──────────────────────────────────────────────────────

    @Schema(description = "A monitored microservice registered in the platform")
    public record ServiceResponse(
            @Schema(description = "Unique service identifier") UUID id,
            @Schema(description = "Display name of the service") String name,
            @Schema(description = "Team responsible for this service") String teamOwner,
            @Schema(description = "Human-readable description of the service") String description,
            @Schema(description = "Current health status of the service") ServiceHealthStatus healthStatus,
            @Schema(description = "When the health status last changed") Instant lastStatusChangedAt,
            @Schema(description = "When the service was first registered") Instant registeredAt
    ) {}

    @Schema(description = "A single health metric reading ingested for a service")
    public record SignalResponse(
            @Schema(description = "Unique signal identifier") UUID id,
            @Schema(description = "ID of the service this signal belongs to") UUID serviceId,
            @Schema(description = "Type of metric reported") com.cloudops.domain.enums.MetricType metricType,
            @Schema(description = "Numeric value of the metric") double value,
            @Schema(description = "True when the value exceeded the configured threshold") boolean thresholdBreached,
            @Schema(description = "When the signal was recorded") Instant recordedAt
    ) {}
}
