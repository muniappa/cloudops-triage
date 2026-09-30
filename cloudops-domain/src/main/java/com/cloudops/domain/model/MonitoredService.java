package com.cloudops.domain.model;

import com.cloudops.domain.enums.ServiceHealthStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a microservice being monitored by CloudOps.
 * <p>
 * Health state machine: HEALTHY → DEGRADED → RECOVERED
 * RECOVERED is a transient state that reverts to HEALTHY after acknowledgment.
 * The rolling signal buffer retains the last N signals for correlation context.
 */
@Entity
@Table(name = "monitored_services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotBlank
    @Column(name = "team_owner", nullable = false)
    private String teamOwner;

    @Column(name = "description")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false)
    private ServiceHealthStatus healthStatus = ServiceHealthStatus.HEALTHY;

    @Column(name = "last_status_changed_at")
    private Instant lastStatusChangedAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    /**
     * Rolling buffer of the most recent health signals for this service.
     * Ordered by timestamp descending; signals are managed via cascade.
     */
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("recordedAt DESC")
    private List<HealthSignal> recentSignals = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.registeredAt = Instant.now();
        this.lastStatusChangedAt = Instant.now();
    }

    // ── State machine transitions ──────────────────────────────────────────

    /**
     * Transitions to DEGRADED. Only valid from HEALTHY.
     * Returns true if the transition occurred.
     */
    public boolean markDegraded() {
        if (this.healthStatus == ServiceHealthStatus.HEALTHY) {
            this.healthStatus = ServiceHealthStatus.DEGRADED;
            this.lastStatusChangedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Transitions to RECOVERED. Only valid from DEGRADED.
     * Returns true if the transition occurred.
     */
    public boolean markRecovered() {
        if (this.healthStatus == ServiceHealthStatus.DEGRADED) {
            this.healthStatus = ServiceHealthStatus.RECOVERED;
            this.lastStatusChangedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Transitions back to HEALTHY from RECOVERED.
     */
    public void markHealthy() {
        this.healthStatus = ServiceHealthStatus.HEALTHY;
        this.lastStatusChangedAt = Instant.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTeamOwner() { return teamOwner; }
    public void setTeamOwner(String teamOwner) { this.teamOwner = teamOwner; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ServiceHealthStatus getHealthStatus() { return healthStatus; }

    public Instant getLastStatusChangedAt() { return lastStatusChangedAt; }

    public Instant getRegisteredAt() { return registeredAt; }

    public List<HealthSignal> getRecentSignals() { return recentSignals; }
}
