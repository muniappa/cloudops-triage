package com.cloudops.api.controller;

import com.cloudops.api.dto.ServiceDtos;
import com.cloudops.api.mapper.DtoMapper;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.service.MonitoredServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints:
 *   GET   /services               — list all monitored services
 *   POST  /services               — register a monitored service
 *   GET   /services/{id}/signals  — recent signals for a service
 *   POST  /services/{id}/signals  — ingest a metric reading
 *   PATCH /services/{id}/status   — trigger DEGRADED or RECOVERED (fires agentic loop)
 */
@Tag(name = "Services", description = "Register and manage monitored microservices and ingest health signals")
@RestController
@RequestMapping("/services")
public class ServiceController {

    private final MonitoredServiceService serviceService;
    private final DtoMapper mapper;

    public ServiceController(MonitoredServiceService serviceService, DtoMapper mapper) {
        this.serviceService = serviceService;
        this.mapper         = mapper;
    }

    // ── GET /services ──────────────────────────────────────────────────────

    @Operation(
        summary = "List all monitored services",
        description = "Returns all services registered in the platform with their current health status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Services retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ServiceDtos.ServiceResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ServiceDtos.ServiceResponse>> list() {
        List<ServiceDtos.ServiceResponse> result = serviceService.findAll()
                .stream()
                .map(mapper::toServiceResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /services ─────────────────────────────────────────────────────

    @Operation(
        summary = "Register a new monitored service",
        description = "Registers a new microservice for health monitoring. The service starts with HEALTHY status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Service registered successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ServiceDtos.ServiceResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body (validation error)",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<ServiceDtos.ServiceResponse> register(
            @Valid @RequestBody ServiceDtos.RegisterServiceRequest request) {

        MonitoredService svc = serviceService.register(
                request.name(), request.teamOwner(), request.description());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toServiceResponse(svc));
    }

    // ── GET /services/{id}/signals ─────────────────────────────────────────

    @Operation(
        summary = "Get recent health signals",
        description = "Returns the most recent health signals ingested for the specified service."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Signals retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ServiceDtos.SignalResponse.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/signals")
    public ResponseEntity<List<ServiceDtos.SignalResponse>> getSignals(
            @Parameter(description = "Service UUID", required = true) @PathVariable UUID id) {
        List<ServiceDtos.SignalResponse> result = serviceService.getRecentSignals(id)
                .stream()
                .map(mapper::toSignalResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /services/{id}/signals ────────────────────────────────────────

    @Operation(
        summary = "Ingest a health signal",
        description = """
            Ingests a single metric reading for the service. If the value breaches the configured
            threshold, the agentic incident loop may be triggered automatically.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Signal ingested successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ServiceDtos.SignalResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid signal payload",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/signals")
    public ResponseEntity<ServiceDtos.SignalResponse> ingestSignal(
            @Parameter(description = "Service UUID", required = true) @PathVariable UUID id,
            @Valid @RequestBody ServiceDtos.IngestSignalRequest request) {

        HealthSignal signal = serviceService.ingestSignal(
                id, request.metricType(), request.value());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSignalResponse(signal));
    }

    // ── PATCH /services/{id}/status ────────────────────────────────────────

    @Operation(
        summary = "Update service health status",
        description = """
            Manually sets a service's health status to DEGRADED or RECOVERED.
            Transitioning to DEGRADED fires the agentic triage loop to open an incident;
            transitioning to HEALTHY marks any open incidents as resolved.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ServiceDtos.ServiceResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid status value or validation error",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Invalid state transition (e.g. already in target state)",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceDtos.ServiceResponse> updateStatus(
            @Parameter(description = "Service UUID", required = true) @PathVariable UUID id,
            @Valid @RequestBody ServiceDtos.UpdateStatusRequest request) {

        MonitoredService updated = serviceService.updateStatus(id, request.status());
        return ResponseEntity.ok(mapper.toServiceResponse(updated));
    }
}
