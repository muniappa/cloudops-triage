package com.cloudops.api.dto;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.IncidentStatus;
import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.enums.TriggerReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class IncidentDtos {

    // ── Response DTOs ──────────────────────────────────────────────────────

    public record IncidentResponse(
            UUID id,
            UUID serviceId,
            String serviceName,
            String title,
            String summary,
            IncidentStatus status,
            IncidentSeverity severity,
            boolean autoDetected,
            Instant createdAt,
            Instant acknowledgedAt,
            Instant resolvedAt,
            List<SuggestionResponse> suggestions
    ) {}

    public record SuggestionResponse(
            UUID id,
            UUID incidentId,
            String recommendedAction,
            String rootCauseHypothesis,
            double confidence,
            String reasoning,
            SuggestionStatus status,
            TriggerReason triggerReason,
            String onCallNote,
            Instant createdAt,
            Instant decidedAt
    ) {}

    // ── Request DTOs ───────────────────────────────────────────────────────

    public record UpdateSuggestionRequest(
            @jakarta.validation.constraints.NotNull SuggestionStatus action,
            String onCallNote
    ) {}
}
