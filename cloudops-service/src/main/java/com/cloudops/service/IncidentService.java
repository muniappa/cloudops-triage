package com.cloudops.service;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.IncidentStatus;
import com.cloudops.domain.model.Incident;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Business logic for incident lifecycle management.
 *
 * <p>All public methods run within a Spring-managed transaction.
 * Write methods use the default {@code REQUIRED} propagation.
 * Read-only methods are annotated with {@code readOnly = true} so the
 * persistence provider can apply read optimisations (no dirty-checking,
 * read replicas, etc.).
 *
 * <p>Repositories are Spring Data JPA interfaces — all DB access goes
 * through JPA ({@code EntityManager} / Hibernate) and is covered by the
 * transaction boundary opened here.
 */
@Service
@Transactional
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    /** Statuses considered "open" for the deduplication guard. */
    private static final List<IncidentStatus> OPEN_STATUSES =
            List.of(IncidentStatus.DRAFT, IncidentStatus.ACKNOWLEDGED);

    private final IncidentRepository incidentRepo;

    public IncidentService(IncidentRepository incidentRepo) {
        this.incidentRepo = incidentRepo;
    }

    // ── Incident creation ──────────────────────────────────────────────────

    /**
     * Idempotent DRAFT creation for the agentic loop.
     *
     * <p>If an open (DRAFT or ACKNOWLEDGED) incident already exists for this
     * service, returns {@link IncidentDraftResult#skipped} — the caller must
     * not attach another {@link com.cloudops.domain.model.RemediationSuggestion}.
     * This is the noisy-signal guard: repeated DEGRADED transitions must not
     * spam duplicate drafts or suggestions.
     *
     * <p>On-demand (MANUAL) callers that want to re-enrich an existing incident
     * should call {@link #applyRecommendation} directly instead.
     */
    public IncidentDraftResult createDraft(MonitoredService service,
                                           String title,
                                           String summary,
                                           IncidentSeverity severity,
                                           boolean autoDetected) {

        List<Incident> open = incidentRepo.findOpenByServiceId(service.getId(), OPEN_STATUSES);
        if (!open.isEmpty()) {
            Incident existing = open.get(0);
            log.info("[IncidentService] Open incident {} already exists for '{}' — skipping new draft",
                     existing.getId(), service.getName());
            return IncidentDraftResult.skipped(existing);
        }

        Incident incident = new Incident();
        incident.setService(service);
        incident.setTitle(title);
        incident.setSummary(summary);
        incident.setSeverity(severity);
        incident.setAutoDetected(autoDetected);
        Incident saved = incidentRepo.save(incident);
        log.info("[IncidentService] Created DRAFT incident {} for '{}'",
                 saved.getId(), service.getName());
        return IncidentDraftResult.created(saved);
    }

    // ── GET /incidents?status= ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Incident> findAll(IncidentStatus status) {
        if (status != null) {
            return incidentRepo.findAllByStatusOrderByCreatedAtDesc(status);
        }
        return incidentRepo.findAllByOrderByCreatedAtDesc();
    }

    // ── Auto-resolve on RECOVERED ──────────────────────────────────────────

    /**
     * Resolves all DRAFT incidents for the service when it transitions to RECOVERED.
     * ACKNOWLEDGED incidents are NOT auto-resolved — on-call owns those.
     */
    public List<Incident> autoResolveDrafts(UUID serviceId) {
        List<Incident> drafts = incidentRepo.findByServiceIdAndStatus(
                serviceId, IncidentStatus.DRAFT);
        drafts.forEach(i -> {
            i.resolve();
            i.setSummary((i.getSummary() != null ? i.getSummary() : "")
                         + "\n\n[Auto-resolved] Service transitioned to RECOVERED.");
        });
        return incidentRepo.saveAll(drafts);
    }

    // ── Update from AI recommendation ──────────────────────────────────────

    /**
     * Applies the latest AI recommendation fields (title, summary, severity)
     * to an existing incident and persists the update within the current transaction.
     */
    public Incident applyRecommendation(UUID incidentId,
                                         com.cloudops.service.advisor.AdvisorRecommendation rec) {
        Incident incident = findOrThrow(incidentId);
        incident.setTitle(rec.incidentTitle());
        incident.setSummary(rec.incidentSummary());
        incident.setSeverity(rec.severity());
        return incidentRepo.save(incident);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Incident findOrThrow(UUID id) {
        return incidentRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));
    }
}
