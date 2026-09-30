package com.cloudops.domain.model;

import com.cloudops.domain.enums.MetricType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * A single metric observation for a monitored service.
 * <p>
 * Represents one data point in the rolling signal buffer used by the
 * correlation engine to determine whether a threshold breach warrants
 * an incident draft.
 */
@Entity
@Table(name = "health_signals", indexes = {
    @Index(name = "idx_signal_service_recorded", columnList = "service_id, recorded_at DESC"),
    @Index(name = "idx_signal_metric_type", columnList = "metric_type")
})
public class HealthSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false)
    private MetricType metricType;

    /**
     * Raw metric value. Interpretation depends on metricType:
     * ERROR_RATE  — percentage (0.0–100.0)
     * LATENCY_P99 — milliseconds
     * CPU         — percentage (0.0–100.0)
     */
    @NotNull
    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "threshold_breached", nullable = false)
    private boolean thresholdBreached = false;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public UUID getId() { return id; }

    public MonitoredService getService() { return service; }
    public void setService(MonitoredService service) { this.service = service; }

    public MetricType getMetricType() { return metricType; }
    public void setMetricType(MetricType metricType) { this.metricType = metricType; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public boolean isThresholdBreached() { return thresholdBreached; }
    public void setThresholdBreached(boolean thresholdBreached) {
        this.thresholdBreached = thresholdBreached;
    }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
