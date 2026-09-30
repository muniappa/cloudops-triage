package com.cloudops.api.mapper;

import com.cloudops.api.dto.IncidentDtos;
import com.cloudops.api.dto.ServiceDtos;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.Incident;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.domain.model.RemediationSuggestion;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written mapper — no MapStruct processor needed for these
 * straightforward projections. Keeps the dependency surface small.
 */
@Component
public class DtoMapper {

    public ServiceDtos.ServiceResponse toServiceResponse(MonitoredService s) {
        return new ServiceDtos.ServiceResponse(
                s.getId(),
                s.getName(),
                s.getTeamOwner(),
                s.getDescription(),
                s.getHealthStatus(),
                s.getLastStatusChangedAt(),
                s.getRegisteredAt()
        );
    }

    public ServiceDtos.SignalResponse toSignalResponse(HealthSignal sig) {
        return new ServiceDtos.SignalResponse(
                sig.getId(),
                sig.getService().getId(),
                sig.getMetricType(),
                sig.getValue(),
                sig.isThresholdBreached(),
                sig.getRecordedAt()
        );
    }

    public IncidentDtos.IncidentResponse toIncidentResponse(Incident i) {
        List<IncidentDtos.SuggestionResponse> suggestions = i.getSuggestions().stream()
                .map(this::toSuggestionResponse)
                .toList();
        return new IncidentDtos.IncidentResponse(
                i.getId(),
                i.getService().getId(),
                i.getService().getName(),
                i.getTitle(),
                i.getSummary(),
                i.getStatus(),
                i.getSeverity(),
                i.isAutoDetected(),
                i.getCreatedAt(),
                i.getAcknowledgedAt(),
                i.getResolvedAt(),
                suggestions
        );
    }

    public IncidentDtos.SuggestionResponse toSuggestionResponse(RemediationSuggestion s) {
        return new IncidentDtos.SuggestionResponse(
                s.getId(),
                s.getIncident().getId(),
                s.getRecommendedAction(),
                s.getRootCauseHypothesis(),
                s.getConfidence(),
                s.getReasoning(),
                s.getStatus(),
                s.getTriggerReason(),
                s.getOnCallNote(),
                s.getCreatedAt(),
                s.getDecidedAt()
        );
    }
}
