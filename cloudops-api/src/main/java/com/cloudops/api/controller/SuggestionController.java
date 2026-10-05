package com.cloudops.api.controller;

import com.cloudops.api.dto.IncidentDtos;
import com.cloudops.api.mapper.DtoMapper;
import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.model.RemediationSuggestion;
import com.cloudops.service.RemediationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoint:
 *   PATCH /suggestions/{id} — accept or reject a remediation suggestion
 */
@Tag(name = "Suggestions", description = "Human approval gate for AI-generated remediation suggestions")
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
    @Operation(
        summary = "Accept or reject a remediation suggestion",
        description = """
            Records the on-call engineer's decision on an AI-generated remediation suggestion.
            ACCEPTED transitions the suggestion to active remediation; REJECTED archives it.
            An optional free-text note can be left for audit purposes.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Decision recorded successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = IncidentDtos.SuggestionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid action value — must be ACCEPTED or REJECTED",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Suggestion not found",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Suggestion is not in PENDING state",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<IncidentDtos.SuggestionResponse> decide(
            @Parameter(description = "Suggestion UUID", required = true) @PathVariable UUID id,
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
