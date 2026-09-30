package com.cloudops.domain.model;

import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.enums.TriggerReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI-generated remediation suggestion linked to an incident.
 * <p>
 * State machine: PENDING → ACCEPTED | REJECTED
 * <p>
 * The on-call engineer reviews the AI's root cause hypothesis,
 * recommended action, confidence score, and reasoning before deciding
 * to accept or reject. Acceptance does NOT automatically execute any
 * action — it records on-call intent for audit and metrics.
 */
@Entity
@Table(name = "remediation_suggestions", indexes = {
    @Index(name = "idx_suggestion_incident", columnList = "incident_id"),
    @Index(name = "idx_suggestion_status", columnList = "status")
})
public class RemediationSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    /**
     * Short, actionable remediation step (e.g., "Restart auth-service pods in prod-auth namespace").
     */
    @Column(name = "recommended_action", nullable = false, columnDefinition = "TEXT")
    private String recommendedAction;

    /**
     * AI's hypothesis about the likely root cause of the incident.
     */
    @Column(name = "root_cause_hypothesis", nullable = false, columnDefinition = "TEXT")
    private String rootCauseHypothesis;

    /**
     * Confidence score from the AI model, 0.0 to 1.0.
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(name = "confidence", nullable = false)
    private Double confidence;

    /**
     * Full AI reasoning chain — the "why" behind the hypothesis and action.
     */
    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false)
    private TriggerReason triggerReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Free-text note from the on-call engineer when accepting or rejecting. */
    @Column(name = "on_call_note", columnDefinition = "TEXT")
    private String onCallNote;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── State machine transitions ──────────────────────────────────────────

    public boolean accept(String note) {
        if (this.status == SuggestionStatus.PENDING) {
            this.status = SuggestionStatus.ACCEPTED;
            this.decidedAt = Instant.now();
            this.onCallNote = note;
            return true;
        }
        return false;
    }

    public boolean reject(String note) {
        if (this.status == SuggestionStatus.PENDING) {
            this.status = SuggestionStatus.REJECTED;
            this.decidedAt = Instant.now();
            this.onCallNote = note;
            return true;
        }
        return false;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public UUID getId() { return id; }

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getRootCauseHypothesis() { return rootCauseHypothesis; }
    public void setRootCauseHypothesis(String rootCauseHypothesis) {
        this.rootCauseHypothesis = rootCauseHypothesis;
    }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public SuggestionStatus getStatus() { return status; }

    public TriggerReason getTriggerReason() { return triggerReason; }
    public void setTriggerReason(TriggerReason triggerReason) {
        this.triggerReason = triggerReason;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }

    public String getOnCallNote() { return onCallNote; }
    public void setOnCallNote(String onCallNote) { this.onCallNote = onCallNote; }
}
