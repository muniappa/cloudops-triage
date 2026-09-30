package com.cloudops.service.advisor;

/**
 * Thrown by {@link LlmIncidentAdvisor} on any failure path:
 * HTTP errors, timeouts, malformed JSON, or failed validation.
 *
 * <p>Always carries a descriptive message. The fallback advisor
 * {@link FallbackIncidentAdvisor} catches this to switch to rule-based.
 */
public class AdvisorException extends RuntimeException {

    public AdvisorException(String message) {
        super(message);
    }

    public AdvisorException(String message, Throwable cause) {
        super(message, cause);
    }
}
