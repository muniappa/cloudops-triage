package com.cloudops.service.advisor;

import com.cloudops.domain.enums.IncidentSeverity;

/**
 * Structured result returned by every {@link IncidentAdvisor} implementation.
 *
 * <p>All fields are written directly into the incident draft and its
 * associated {@link com.cloudops.domain.model.RemediationSuggestion}.
 * The {@code advisorType} field badges the on-call console so engineers
 * know whether the analysis came from the LLM or the rule-based fallback.
 *
 * @param severity            assessed severity (never null)
 * @param rootCauseHypothesis concise root cause hypothesis
 * @param recommendedAction   specific, actionable first remediation step
 * @param confidence          model confidence 0.0–1.0
 * @param reasoning           full chain-of-thought or rule trace
 * @param incidentTitle       suggested title for the draft incident
 * @param incidentSummary     suggested summary for the draft incident
 * @param advisorType         identifier of the advisor that produced this result
 */
public record AdvisorRecommendation(
        IncidentSeverity severity,
        String rootCauseHypothesis,
        String recommendedAction,
        double confidence,
        String reasoning,
        String incidentTitle,
        String incidentSummary,
        String advisorType
) {
    /**
     * Validates that this recommendation is safe to persist.
     * Called after LLM responses are deserialized before any DB writes.
     */
    public boolean isValid() {
        return severity != null
                && rootCauseHypothesis != null && !rootCauseHypothesis.isBlank()
                && recommendedAction   != null && !recommendedAction.isBlank()
                && confidence >= 0.0 && confidence <= 1.0;
    }
}
