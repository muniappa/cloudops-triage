package com.cloudops.service;

import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.enums.TriggerReason;
import com.cloudops.domain.model.Incident;
import com.cloudops.domain.model.RemediationSuggestion;
import com.cloudops.repository.RemediationSuggestionRepository;
import com.cloudops.service.advisor.AdvisorRecommendation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class RemediationService {

    private final RemediationSuggestionRepository suggestionRepo;

    public RemediationService(RemediationSuggestionRepository suggestionRepo) {
        this.suggestionRepo = suggestionRepo;
    }

    // ── Create from AI recommendation ─────────────────────────────────────

    public RemediationSuggestion createFromRecommendation(Incident incident,
                                                           AdvisorRecommendation rec,
                                                           TriggerReason triggerReason) {
        RemediationSuggestion suggestion = new RemediationSuggestion();
        suggestion.setIncident(incident);
        suggestion.setRecommendedAction(rec.recommendedAction());
        suggestion.setRootCauseHypothesis(rec.rootCauseHypothesis());
        suggestion.setConfidence(rec.confidence());
        suggestion.setReasoning(rec.reasoning());
        suggestion.setTriggerReason(triggerReason);
        return suggestionRepo.save(suggestion);
    }

    // ── PATCH /suggestions/{id} ────────────────────────────────────────────

    /**
     * Accepts a PENDING suggestion.
     * The on-call engineer may provide an optional note.
     */
    public RemediationSuggestion accept(UUID id, String onCallNote) {
        RemediationSuggestion suggestion = findOrThrow(id);
        if (!suggestion.accept(onCallNote)) {
            throw new IllegalStateException(
                "Suggestion %s is already %s".formatted(id, suggestion.getStatus()));
        }
        return suggestionRepo.save(suggestion);
    }

    /**
     * Rejects a PENDING suggestion.
     */
    public RemediationSuggestion reject(UUID id, String onCallNote) {
        RemediationSuggestion suggestion = findOrThrow(id);
        if (!suggestion.reject(onCallNote)) {
            throw new IllegalStateException(
                "Suggestion %s is already %s".formatted(id, suggestion.getStatus()));
        }
        return suggestionRepo.save(suggestion);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RemediationSuggestion findOrThrow(UUID id) {
        return suggestionRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Suggestion not found: " + id));
    }
}
