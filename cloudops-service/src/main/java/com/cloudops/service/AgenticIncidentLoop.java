package com.cloudops.service;

import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.enums.TriggerReason;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.repository.HealthSignalRepository;
import com.cloudops.repository.MonitoredServiceRepository;
import com.cloudops.service.advisor.AdvisorRecommendation;
import com.cloudops.service.advisor.AnalysisContext;
import com.cloudops.service.advisor.AnalysisContextBuilder;
import com.cloudops.service.advisor.IncidentAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agentic incident loop — reacts to service health status changes.
 *
 * <h3>DEGRADED path</h3>
 * <ol>
 *   <li>Re-fetch the service from DB (the event carries a detached snapshot).</li>
 *   <li>Pull the rolling signal buffer.</li>
 *   <li>Build a rich {@link AnalysisContext} with trends + breach summaries.</li>
 *   <li>Run the advisor (LLM with rule-based fallback; never silent-drop).</li>
 *   <li>Call {@link IncidentService#createDraft} — returns
 *       {@link IncidentDraftResult#isCreated()} or {@link IncidentDraftResult#isSkipped()}.</li>
 *   <li><b>Only if CREATED</b> — attach a PENDING {@link com.cloudops.domain.model.RemediationSuggestion}.
 *       Skipped result means an open incident already exists; no duplicate suggestion is created.</li>
 * </ol>
 *
 * <h3>RECOVERED path</h3>
 * Auto-resolves all DRAFT incidents. ACKNOWLEDGED incidents are left for on-call.
 *
 * <h3>Human checkpoint</h3>
 * The loop proposes (DRAFT incident + PENDING suggestion). On-call accepts or rejects
 * via {@code PATCH /suggestions/{id}}. Auto-paging is deferred to sprint 3.
 *
 * <h3>Idempotency</h3>
 * Repeated DEGRADED events for the same service are safe — if an open incident already
 * exists, the loop logs and exits without creating duplicates.
 */
@Component
public class AgenticIncidentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgenticIncidentLoop.class);

    private final MonitoredServiceRepository serviceRepo;
    private final HealthSignalRepository signalRepo;
    private final IncidentService incidentService;
    private final RemediationService remediationService;
    private final IncidentAdvisor advisor;
    private final AnalysisContextBuilder contextBuilder;

    public AgenticIncidentLoop(MonitoredServiceRepository serviceRepo,
                                HealthSignalRepository signalRepo,
                                IncidentService incidentService,
                                RemediationService remediationService,
                                IncidentAdvisor advisor,
                                AnalysisContextBuilder contextBuilder) {
        this.serviceRepo     = serviceRepo;
        this.signalRepo      = signalRepo;
        this.incidentService = incidentService;
        this.remediationService = remediationService;
        this.advisor         = advisor;
        this.contextBuilder  = contextBuilder;
    }

    // ── Event handler ──────────────────────────────────────────────────────

    @Async
    @EventListener
    public void onStatusChanged(ServiceStatusChangedEvent event) {
        ServiceHealthStatus newStatus = event.getNewStatus();

        log.info("[AgenticLoop] {} → {} for service '{}'",
                 event.getPreviousStatus(), newStatus, event.getService().getName());

        if (newStatus == ServiceHealthStatus.DEGRADED) {
            handleDegraded(event.getService().getId());
        } else if (newStatus == ServiceHealthStatus.RECOVERED) {
            handleRecovered(event.getService().getId(), event.getService().getName());
        }
    }

    // ── DEGRADED path ──────────────────────────────────────────────────────

    private void handleDegraded(java.util.UUID serviceId) {
        try {
            // Re-fetch from DB — the event snapshot is detached and may be stale
            MonitoredService service = serviceRepo.findById(serviceId).orElse(null);
            if (service == null) {
                log.error("[AgenticLoop] Service {} not found during triage — aborting", serviceId);
                return;
            }

            log.info("[AgenticLoop] Starting triage for '{}'", service.getName());

            // Pull rolling signal buffer
            List<HealthSignal> signals =
                    signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(serviceId);

            // Build rich multi-signal context snapshot
            AnalysisContext ctx = contextBuilder.build(service, signals, TriggerReason.AUTO_DEGRADED);

            // Advisor — FallbackIncidentAdvisor handles LLM failures; never silent-drop
            AdvisorRecommendation rec = advisor.analyse(ctx);

            log.info("[AgenticLoop] Recommendation for '{}': severity={}, advisor={}, confidence={}",
                     service.getName(), rec.severity(), rec.advisorType(),
                     String.format("%.2f", rec.confidence()));

            // Idempotent draft creation — SKIPPED if open incident already exists
            IncidentDraftResult result = incidentService.createDraft(
                    service,
                    rec.incidentTitle(),
                    rec.incidentSummary(),
                    rec.severity(),
                    true   // autoDetected = true
            );

            if (result.isSkipped()) {
                // Noisy-signal guard: open incident already exists — do not add another suggestion
                log.info("[AgenticLoop] Open incident {} already exists for '{}' — "
                         + "skipping suggestion creation (noisy-signal guard)",
                         result.incident().getId(), service.getName());
                return;
            }

            // New draft: attach PENDING remediation suggestion for on-call review
            remediationService.createFromRecommendation(
                    result.incident(), rec, TriggerReason.AUTO_DEGRADED);

            log.info("[AgenticLoop] Draft incident {} + PENDING suggestion created for '{}' "
                     + "(advisor={}) — awaiting on-call approval",
                     result.incident().getId(), service.getName(), rec.advisorType());

        } catch (Exception e) {
            // FallbackIncidentAdvisor prevents this for advisor failures.
            // DB or unexpected failures still land here — always log, never silent-drop.
            log.error("[AgenticLoop] Triage failed for service {}: {}",
                      serviceId, e.getMessage(), e);
        }
    }

    // ── RECOVERED path ─────────────────────────────────────────────────────

    private void handleRecovered(java.util.UUID serviceId, String serviceName) {
        log.info("[AgenticLoop] Auto-resolving DRAFT incidents for '{}'", serviceName);
        try {
            var resolved = incidentService.autoResolveDrafts(serviceId);
            if (resolved.isEmpty()) {
                log.info("[AgenticLoop] No DRAFT incidents to auto-resolve for '{}'", serviceName);
            } else {
                log.info("[AgenticLoop] Auto-resolved {} DRAFT incident(s) for '{}'",
                         resolved.size(), serviceName);
            }
        } catch (Exception e) {
            log.error("[AgenticLoop] Auto-resolve failed for '{}': {}",
                      serviceName, e.getMessage(), e);
        }
    }
}
