package com.cloudops.api.dto;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.IncidentStatus;
import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.enums.TriggerReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class IncidentDtos {

    // ── Response DTOs ──────────────────────────────────────────────────────

    @Schema(description = "A triage incident created when a monitored service degrades")
    public record IncidentResponse(
            @Schema(description = "Unique incident identifier") UUID id,
            @Schema(description = "ID of the affected service") UUID serviceId,
            @Schema(description = "Name of the affected service") String serviceName,
            @Schema(description = "Short AI-generated title summarising the incident") String title,
            @Schema(description = "Detailed AI-generated summary of the incident") String summary,
            @Schema(description = "Current lifecycle status of the incident") IncidentStatus status,
            @Schema(description = "Severity assessed by the AI advisor") IncidentSeverity severity,
            @Schema(description = "True when the incident was opened by automated detection") boolean autoDetected,
            @Schema(description = "When the incident was first opened") Instant createdAt,
            @Schema(description = "When the incident was acknowledged (null if not yet)") Instant acknowledgedAt,
            @Schema(description = "When the incident was resolved (null if still open)") Instant resolvedAt,
            @Schema(description = "Remediation suggestions associated with this incident") List<SuggestionResponse> suggestions
    ) {}

    @Schema(description = "An AI-generated remediation suggestion awaiting on-call approval")
    public record SuggestionResponse(
            @Schema(description = "Unique suggestion identifier") UUID id,
            @Schema(description = "ID of the parent incident") UUID incidentId,
            @Schema(description = "Recommended remediation action from the AI advisor") String recommendedAction,
            @Schema(description = "AI hypothesis for the root cause") String rootCauseHypothesis,
            @Schema(description = "AI confidence score between 0.0 and 1.0", minimum = "0", maximum = "1") double confidence,
            @Schema(description = "Step-by-step reasoning behind the recommendation") String reasoning,
            @Schema(description = "Current decision status of the suggestion") SuggestionStatus status,
            @Schema(description = "What triggered this suggestion (e.g. AUTOMATIC, MANUAL)") TriggerReason triggerReason,
            @Schema(description = "Optional note left by the on-call engineer") String onCallNote,
            @Schema(description = "When the suggestion was created") Instant createdAt,
            @Schema(description = "When the on-call decision was recorded (null if still pending)") Instant decidedAt
    ) {}

    // ── Request DTOs ───────────────────────────────────────────────────────

    @Schema(description = "Request body to accept or reject a remediation suggestion")
    public record UpdateSuggestionRequest(
            @Schema(description = "Decision: must be ACCEPTED or REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull SuggestionStatus action,
            @Schema(description = "Optional free-text note from the on-call engineer")
            String onCallNote
    ) {}
}
