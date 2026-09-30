package com.cloudops.api.controller;

import com.cloudops.api.dto.IncidentDtos;
import com.cloudops.api.mapper.DtoMapper;
import com.cloudops.domain.enums.IncidentStatus;
import com.cloudops.domain.enums.TriggerReason;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.Incident;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.repository.HealthSignalRepository;
import com.cloudops.service.IncidentService;
import com.cloudops.service.MonitoredServiceService;
import com.cloudops.service.RemediationService;
import com.cloudops.service.advisor.AdvisorRecommendation;
import com.cloudops.service.advisor.AnalysisContext;
import com.cloudops.service.advisor.AnalysisContextBuilder;
import com.cloudops.service.advisor.IncidentAdvisor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints:
 *   GET  /incidents?status=      — filterable incident list
 *   POST /incidents/{id}/analyze — on-demand AI analysis, creates new suggestion
 */
@RestController
@RequestMapping("/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final MonitoredServiceService serviceService;
    private final RemediationService remediationService;
    private final HealthSignalRepository signalRepo;
    private final IncidentAdvisor advisor;
    private final AnalysisContextBuilder contextBuilder;
    private final DtoMapper mapper;

    public IncidentController(IncidentService incidentService,
                               MonitoredServiceService serviceService,
                               RemediationService remediationService,
                               HealthSignalRepository signalRepo,
                               IncidentAdvisor advisor,
                               AnalysisContextBuilder contextBuilder,
                               DtoMapper mapper) {
        this.incidentService    = incidentService;
        this.serviceService     = serviceService;
        this.remediationService = remediationService;
        this.signalRepo         = signalRepo;
        this.advisor            = advisor;
        this.contextBuilder     = contextBuilder;
        this.mapper             = mapper;
    }

    // ── GET /incidents?status= ─────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<IncidentDtos.IncidentResponse>> list(
            @RequestParam(required = false) IncidentStatus status) {

        List<IncidentDtos.IncidentResponse> result = incidentService.findAll(status)
                .stream()
                .map(mapper::toIncidentResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    // ── GET /incidents/{id} ────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDtos.IncidentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toIncidentResponse(incidentService.findOrThrow(id)));
    }

    // ── POST /incidents/{id}/analyze ───────────────────────────────────────

    /**
     * On-demand AI analysis for an existing incident.
     * Creates a new PENDING RemediationSuggestion with the latest AI recommendation.
     * Useful when on-call wants a fresh analysis after additional signals arrive.
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<IncidentDtos.SuggestionResponse> analyze(@PathVariable UUID id) {
        Incident incident = incidentService.findOrThrow(id);
        MonitoredService service = incident.getService();

        List<HealthSignal> signals = signalRepo
                .findTop20ByServiceIdOrderByRecordedAtDesc(service.getId());

        // Build rich multi-signal context snapshot (MANUAL trigger = on-demand analysis)
        AnalysisContext ctx = contextBuilder.build(service, signals, TriggerReason.MANUAL);

        AdvisorRecommendation rec = advisor.analyse(ctx);

        // Persist updated title/summary/severity via service (managed transaction)
        Incident updated = incidentService.applyRecommendation(id, rec);

        var suggestion = remediationService.createFromRecommendation(
                updated, rec, TriggerReason.MANUAL);

        return ResponseEntity.ok(mapper.toSuggestionResponse(suggestion));
    }
}
