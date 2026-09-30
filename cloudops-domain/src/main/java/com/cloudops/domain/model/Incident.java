package com.cloudops.domain.model;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.IncidentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A production incident linked to a degraded service.
 * <p>
 * State machine: DRAFT → ACKNOWLEDGED → RESOLVED
 * <p>
 * DRAFT   — auto-created by the agentic loop; not yet seen by on-call.
 * ACKNOWLEDGED — on-call has reviewed and accepted ownership.
 * RESOLVED     — service returned to HEALTHY and incident closed.
 * <p>
 * Auto-detected incidents carry the autoDetected flag so the on-call
 * console can badge them distinctly from manually raised incidents.
 */
@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_service_status", columnList = "service_id, status"),
    @Index(name = "idx_incident_created_at", columnList = "created_at DESC")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private IncidentSeverity severity = IncidentSeverity.MEDIUM;

    /**
     * True when created automatically by the agentic loop on DEGRADED.
     * Displayed as a badge in the on-call console.
     */
    @Column(name = "auto_detected", nullable = false)
    private boolean autoDetected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<RemediationSuggestion> suggestions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── State machine transitions ──────────────────────────────────────────

    /**
     * Transitions DRAFT → ACKNOWLEDGED.
     */
    public boolean acknowledge() {
        if (this.status == IncidentStatus.DRAFT) {
            this.status = IncidentStatus.ACKNOWLEDGED;
            this.acknowledgedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Transitions ACKNOWLEDGED → RESOLVED (also allows DRAFT → RESOLVED
     * for auto-resolve on RECOVERED when no on-call action was taken).
     */
    public boolean resolve() {
        if (this.status == IncidentStatus.DRAFT || this.status == IncidentStatus.ACKNOWLEDGED) {
            this.status = IncidentStatus.RESOLVED;
            this.resolvedAt = Instant.now();
            return true;
        }
        return false;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public UUID getId() { return id; }

    public MonitoredService getService() { return service; }
    public void setService(MonitoredService service) { this.service = service; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public IncidentStatus getStatus() { return status; }

    public IncidentSeverity getSeverity() { return severity; }
    public void setSeverity(IncidentSeverity severity) { this.severity = severity; }

    public boolean isAutoDetected() { return autoDetected; }
    public void setAutoDetected(boolean autoDetected) { this.autoDetected = autoDetected; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public List<RemediationSuggestion> getSuggestions() { return suggestions; }
}
