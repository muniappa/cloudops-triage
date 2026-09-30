package com.cloudops.repository;

import com.cloudops.domain.enums.SuggestionStatus;
import com.cloudops.domain.model.RemediationSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RemediationSuggestionRepository extends JpaRepository<RemediationSuggestion, UUID> {

    List<RemediationSuggestion> findAllByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<RemediationSuggestion> findAllByIncidentIdAndStatus(UUID incidentId, SuggestionStatus status);
}
