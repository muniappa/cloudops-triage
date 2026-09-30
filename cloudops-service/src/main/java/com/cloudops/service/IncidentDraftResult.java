package com.cloudops.service;

import com.cloudops.domain.model.Incident;

/**
 * Outcome of {@link IncidentService#createDraft}.
 *
 * <p>The agentic loop uses the {@link Outcome} to decide whether to attach
 * a new {@link com.cloudops.domain.model.RemediationSuggestion}:
 * <ul>
 *   <li>{@link Outcome#CREATED}  — new DRAFT incident; loop proceeds to create suggestion.</li>
 *   <li>{@link Outcome#SKIPPED}  — open incident already existed for this service;
 *       loop skips suggestion creation to avoid duplicates (noisy-signal guard).</li>
 * </ul>
 *
 * @param outcome  what happened
 * @param incident the incident — either newly created or the pre-existing one
 */
public record IncidentDraftResult(Outcome outcome, Incident incident) {

    public enum Outcome { CREATED, SKIPPED }

    public boolean isCreated() { return outcome == Outcome.CREATED; }
    public boolean isSkipped() { return outcome == Outcome.SKIPPED; }

    public static IncidentDraftResult created(Incident i)  { return new IncidentDraftResult(Outcome.CREATED, i); }
    public static IncidentDraftResult skipped(Incident i)  { return new IncidentDraftResult(Outcome.SKIPPED, i); }
}
