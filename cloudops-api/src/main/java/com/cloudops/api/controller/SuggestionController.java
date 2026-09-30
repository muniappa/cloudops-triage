package com.cloudops.api.controller;

import com.cloudops.api.dto.IncidentDtos;
import com.cloudops.api.mapper.DtoMapper;
import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.model.RemediationSuggestion;
import com.cloudops.service.RemediationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoint:
 *   PATCH /suggestions/{id} — accept or reject a remediation suggestion
 */
@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

    private final RemediationService remediationService;
    private final DtoMapper mapper;

    public SuggestionController(RemediationService remediationService, DtoMapper mapper) {
        this.remediationService = remediationService;
        this.mapper             = mapper;
    }

    // ── PATCH /suggestions/{id} ────────────────────────────────────────────

    /**
     * On-call accepts or rejects the AI's remediation suggestion.
     * This is the human approval gate — the only place in the system
     * where a human decision is recorded before action is taken.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<IncidentDtos.SuggestionResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentDtos.UpdateSuggestionRequest request) {

        RemediationSuggestion updated;

        if (request.action() == SuggestionStatus.ACCEPTED) {
            updated = remediationService.accept(id, request.onCallNote());
        } else if (request.action() == SuggestionStatus.REJECTED) {
            updated = remediationService.reject(id, request.onCallNote());
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(mapper.toSuggestionResponse(updated));
    }
}
