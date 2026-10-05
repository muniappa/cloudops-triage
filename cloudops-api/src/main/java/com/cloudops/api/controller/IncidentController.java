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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints:
 *   GET  /incidents?status=      — filterable incident list
 *   POST /incidents/{id}/analyze — on-demand AI analysis, creates new suggestion
 */
@Tag(name = "Incidents", description = "Manage and triage incidents detected by the monitoring platform")
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

    @Operation(
        summary = "List all incidents",
        description = "Returns a list of all incidents, optionally filtered by status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incidents retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = IncidentDtos.IncidentResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<IncidentDtos.IncidentResponse>> list(
            @Parameter(description = "Filter by incident status (e.g. OPEN, ACKNOWLEDGED, RESOLVED)")
            @RequestParam(required = false) IncidentStatus status) {

        List<IncidentDtos.IncidentResponse> result = incidentService.findAll(status)
                .stream()
                .map(mapper::toIncidentResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    // ── GET /incidents/{id} ────────────────────────────────────────────────

    @Operation(
        summary = "Get a single incident",
        description = "Returns the full incident detail including all remediation suggestions."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incident found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = IncidentDtos.IncidentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Incident not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<IncidentDtos.IncidentResponse> get(
            @Parameter(description = "Incident UUID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toIncidentResponse(incidentService.findOrThrow(id)));
    }

    // ── POST /incidents/{id}/analyze ───────────────────────────────────────

    /**
     * On-demand AI analysis for an existing incident.
     * Creates a new PENDING RemediationSuggestion with the latest AI recommendation.
     * Useful when on-call wants a fresh analysis after additional signals arrive.
     */
    @Operation(
        summary = "Trigger on-demand AI analysis",
        description = """
            Runs the AI advisor against the latest health signals for the incident's service and
            creates a new PENDING remediation suggestion. Use this when additional signals arrive
            and the on-call engineer wants a fresh analysis without waiting for the automatic loop.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analysis complete, new suggestion created",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = IncidentDtos.SuggestionResponse.class))),
        @ApiResponse(responseCode = "404", description = "Incident not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/analyze")
    public ResponseEntity<IncidentDtos.SuggestionResponse> analyze(
            @Parameter(description = "Incident UUID", required = true) @PathVariable UUID id) {
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
