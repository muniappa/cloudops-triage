package com.cloudops.api.controller;

import com.cloudops.api.dto.ServiceDtos;
import com.cloudops.api.mapper.DtoMapper;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.service.MonitoredServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    public ResponseEntity<List<ServiceDtos.ServiceResponse>> list() {
        List<ServiceDtos.ServiceResponse> result = serviceService.findAll()
                .stream()
                .map(mapper::toServiceResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /services ─────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ServiceDtos.ServiceResponse> register(
            @Valid @RequestBody ServiceDtos.RegisterServiceRequest request) {

        MonitoredService svc = serviceService.register(
                request.name(), request.teamOwner(), request.description());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toServiceResponse(svc));
    }

    // ── GET /services/{id}/signals ─────────────────────────────────────────

    @GetMapping("/{id}/signals")
    public ResponseEntity<List<ServiceDtos.SignalResponse>> getSignals(@PathVariable UUID id) {
        List<ServiceDtos.SignalResponse> result = serviceService.getRecentSignals(id)
                .stream()
                .map(mapper::toSignalResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /services/{id}/signals ────────────────────────────────────────

    @PostMapping("/{id}/signals")
    public ResponseEntity<ServiceDtos.SignalResponse> ingestSignal(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceDtos.IngestSignalRequest request) {

        HealthSignal signal = serviceService.ingestSignal(
                id, request.metricType(), request.value());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSignalResponse(signal));
    }

    // ── PATCH /services/{id}/status ────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceDtos.ServiceResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceDtos.UpdateStatusRequest request) {

        MonitoredService updated = serviceService.updateStatus(id, request.status());
        return ResponseEntity.ok(mapper.toServiceResponse(updated));
    }
}
