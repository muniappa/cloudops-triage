package com.cloudops.service;

import com.cloudops.domain.enums.MetricType;
import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.repository.HealthSignalRepository;
import com.cloudops.repository.MonitoredServiceRepository;
import com.cloudops.service.correlation.CompositeSignalAnalyzer;
import com.cloudops.service.correlation.SignalAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Business logic for monitored-service registration, signal ingestion,
 * and health-status transitions.
 *
 * <p>All public methods run within a Spring-managed transaction (default
 * {@code REQUIRED} propagation). Read-only query methods are annotated
 * {@code readOnly = true}. Repositories are Spring Data JPA interfaces —
 * all DB access goes through the JPA {@code EntityManager}.
 *
 * <p>Signal ingest runs the correlation engine in the same transaction so
 * a DEGRADED transition (if triggered) is committed atomically with the
 * signal record and the resulting domain event fires only after commit.
 */
@Service
@Transactional
public class MonitoredServiceService {

    private static final Logger log = LoggerFactory.getLogger(MonitoredServiceService.class);

    /**
     * Maximum number of signals retained per service in the rolling buffer.
     * Older signals are deleted by {@link #trimSignalBuffer} after each ingest.
     */
    private static final int SIGNAL_BUFFER_SIZE = 20;

    private final MonitoredServiceRepository serviceRepo;
    private final HealthSignalRepository signalRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final CompositeSignalAnalyzer correlationEngine;

    public MonitoredServiceService(MonitoredServiceRepository serviceRepo,
                                   HealthSignalRepository signalRepo,
                                   ApplicationEventPublisher eventPublisher,
                                   CompositeSignalAnalyzer correlationEngine) {
        this.serviceRepo       = serviceRepo;
        this.signalRepo        = signalRepo;
        this.eventPublisher    = eventPublisher;
        this.correlationEngine = correlationEngine;
    }

    // ── POST /services ─────────────────────────────────────────────────────

    public MonitoredService register(String name, String teamOwner, String description) {
        if (serviceRepo.existsByName(name)) {
            throw new IllegalArgumentException("Service already registered: " + name);
        }
        MonitoredService svc = new MonitoredService();
        svc.setName(name);
        svc.setTeamOwner(teamOwner);
        svc.setDescription(description);
        return serviceRepo.save(svc);
    }

    // ── POST /services/{id}/signals ────────────────────────────────────────

    /**
     * Ingests a health signal, persists it, then runs the correlation engine
     * against the updated rolling buffer.
     *
     * <p>If the engine flags a DEGRADED condition <em>and</em> the service is
     * currently HEALTHY, the transition is applied automatically and the
     * agentic loop event fires — no manual PATCH needed.
     *
     * @return the persisted {@link HealthSignal}
     */
    public HealthSignal ingestSignal(UUID serviceId, MetricType metricType, double value) {
        MonitoredService service = findOrThrow(serviceId);

        boolean breached = isThresholdBreached(metricType, value);

        HealthSignal signal = new HealthSignal();
        signal.setService(service);
        signal.setMetricType(metricType);
        signal.setValue(value);
        signal.setThresholdBreached(breached);

        signalRepo.save(signal);

        // Trim rolling buffer — keep only latest SIGNAL_BUFFER_SIZE signals
        trimSignalBuffer(serviceId);

        // Run correlation engine against the fresh buffer
        runCorrelation(service);

        return signal;
    }

    // ── PATCH /services/{id}/status ────────────────────────────────────────

    /**
     * Applies a status transition and publishes a domain event so the
     * agentic loop can react asynchronously.
     */
    public MonitoredService updateStatus(UUID serviceId, ServiceHealthStatus newStatus) {
        MonitoredService service = findOrThrow(serviceId);
        ServiceHealthStatus current = service.getHealthStatus();

        // Idempotent: same-state transition is a no-op — return current state, no event fired.
        // This prevents duplicate agentic loop triggers from repeated PATCH calls.
        if (current == newStatus) {
            log.info("[ServiceStatus] '{}' already in state {} — no-op", service.getName(), newStatus);
            return service;
        }

        boolean transitioned = switch (newStatus) {
            case DEGRADED  -> service.markDegraded();
            case RECOVERED -> service.markRecovered();
            case HEALTHY   -> { service.markHealthy(); yield true; }
        };

        if (!transitioned) {
            throw new IllegalStateException(
                "Invalid transition from %s to %s for service '%s'"
                    .formatted(current, newStatus, service.getName()));
        }

        MonitoredService saved = serviceRepo.save(service);

        // Publish event for the agentic loop to consume asynchronously
        eventPublisher.publishEvent(new ServiceStatusChangedEvent(saved, current, newStatus));

        return saved;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MonitoredService findOrThrow(UUID id) {
        return serviceRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Service not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MonitoredService> findAll() {
        return serviceRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<HealthSignal> getRecentSignals(UUID serviceId) {
        return signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(serviceId);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Runs the composite correlation engine against the current signal buffer.
     * If flagged and service is HEALTHY, auto-transitions to DEGRADED.
     * Never throws — a correlation failure must not abort the signal ingest.
     */
    private void runCorrelation(MonitoredService service) {
        try {
            List<HealthSignal> buffer =
                    signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(service.getId());

            SignalAnalysis analysis = correlationEngine.evaluate(service, buffer);

            if (analysis.flagged()
                    && service.getHealthStatus() == ServiceHealthStatus.HEALTHY) {

                log.info("[CorrelationEngine] Flagging '{}' DEGRADED — trigger: {} {} "
                         + "(threshold {}), severity: {}",
                         service.getName(),
                         analysis.triggerMetric(),
                         String.format("%.2f", analysis.triggerValue()),
                         String.format("%.2f", analysis.thresholdValue()),
                         analysis.severity());

                // Reuse updateStatus so the agentic loop fires via ApplicationEvent
                updateStatus(service.getId(), ServiceHealthStatus.DEGRADED);
            }
        } catch (Exception ex) {
            log.error("[CorrelationEngine] Evaluation failed for '{}': {}",
                      service.getName(), ex.getMessage(), ex);
        }
    }

    private boolean isThresholdBreached(MetricType type, double value) {
        return switch (type) {
            case ERROR_RATE    -> value >= 2.0;    // ≥2% error rate
            case LATENCY_P99   -> value >= 500.0;  // ≥500ms p99
            case CPU_USAGE     -> value >= 80.0;   // ≥80% CPU
            case MEMORY_USAGE  -> value >= 85.0;   // ≥85% memory
            case REQUEST_RATE  -> false;            // high request rate alone is not a breach
        };
    }

    /**
     * Deletes signals older than the (SIGNAL_BUFFER_SIZE + 1)-th newest reading
     * so the buffer stays at most {@value #SIGNAL_BUFFER_SIZE} entries.
     *
     * Uses a {@code @Modifying} JPQL delete — runs inside the current
     * {@code @Transactional} boundary opened by the caller.
     */
    private void trimSignalBuffer(UUID serviceId) {
        List<HealthSignal> buffer =
                signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(serviceId);
        if (buffer.size() >= SIGNAL_BUFFER_SIZE) {
            // Use the timestamp of the oldest retained signal as the cutoff
            Instant cutoff = buffer.get(buffer.size() - 1).getRecordedAt();
            int deleted = signalRepo.deleteOlderThan(serviceId, cutoff);
            if (deleted > 0) {
                log.debug("[SignalBuffer] Trimmed {} old signal(s) for service {}",
                          deleted, serviceId);
            }
        }
    }
}
