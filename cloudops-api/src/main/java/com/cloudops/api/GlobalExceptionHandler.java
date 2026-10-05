package com.cloudops.api;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.NoSuchElementException;

/**
 * Global exception handler — maps domain exceptions to RFC 7807 ProblemDetail responses.
 *
 * <p>Error type URNs:
 * <ul>
 *   <li>{@code urn:cloudops:error:not-found}              — 404 resource not found</li>
 *   <li>{@code urn:cloudops:error:bad-request}             — 400 illegal argument</li>
 *   <li>{@code urn:cloudops:error:invalid-state-transition} — 409 illegal state</li>
 *   <li>{@code urn:cloudops:error:validation}              — 400 bean-validation failure</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ──────────────────────────────────────────────────────

    @ApiResponse(
        responseCode = "404",
        description = "The requested resource does not exist",
        content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
    )
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:cloudops:error:not-found"));
        return pd;
    }

    // ── 400 Bad Request ────────────────────────────────────────────────────

    @ApiResponse(
        responseCode = "400",
        description = "The request contains an illegal argument",
        content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
    )
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("urn:cloudops:error:bad-request"));
        return pd;
    }

    // ── 409 Conflict ───────────────────────────────────────────────────────

    @ApiResponse(
        responseCode = "409",
        description = "The operation is not allowed in the current state (e.g. invalid status transition)",
        content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
    )
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:cloudops:error:invalid-state-transition"));
        return pd;
    }

    // ── 400 Validation ─────────────────────────────────────────────────────

    @ApiResponse(
        responseCode = "400",
        description = "One or more request fields failed bean validation",
        content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
    )
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("urn:cloudops:error:validation"));
        return pd;
    }
}
