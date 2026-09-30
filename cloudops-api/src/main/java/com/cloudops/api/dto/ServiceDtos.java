package com.cloudops.api.dto;

import com.cloudops.domain.enums.ServiceHealthStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

// ── Request DTOs ───────────────────────────────────────────────────────────

public class ServiceDtos {

    public record RegisterServiceRequest(
            @NotBlank String name,
            @NotBlank String teamOwner,
            String description
    ) {}

    public record IngestSignalRequest(
            @jakarta.validation.constraints.NotNull
            com.cloudops.domain.enums.MetricType metricType,
            @jakarta.validation.constraints.NotNull double value
    ) {}

    public record UpdateStatusRequest(
            @jakarta.validation.constraints.NotNull ServiceHealthStatus status
    ) {}

    // ── Response DTOs ──────────────────────────────────────────────────────

    public record ServiceResponse(
            UUID id,
            String name,
            String teamOwner,
            String description,
            ServiceHealthStatus healthStatus,
            Instant lastStatusChangedAt,
            Instant registeredAt
    ) {}

    public record SignalResponse(
            UUID id,
            UUID serviceId,
            com.cloudops.domain.enums.MetricType metricType,
            double value,
            boolean thresholdBreached,
            Instant recordedAt
    ) {}
}
