package com.cloudops.service;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.enums.TriggerReason;
import com.cloudops.domain.model.Incident;
import com.cloudops.domain.model.MonitoredService;
import com.cloudops.repository.HealthSignalRepository;
import com.cloudops.repository.MonitoredServiceRepository;
import com.cloudops.service.advisor.AdvisorRecommendation;
import com.cloudops.service.advisor.AnalysisContext;
import com.cloudops.service.advisor.AnalysisContextBuilder;
import com.cloudops.service.advisor.IncidentAdvisor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AgenticIncidentLoop}.
 *
 * All collaborators are mocked — no Spring context, no DB.
 * Verifies: DEGRADED creates draft+suggestion, idempotency skip,
 * AI-failure falls back to rule-based, RECOVERED auto-resolves,
 * RECOVERED with no open drafts is silent no-op.
 */
@ExtendWith(MockitoExtension.class)
class AgenticIncidentLoopTest {

    @Mock MonitoredServiceRepository serviceRepo;
    @Mock HealthSignalRepository signalRepo;
    @Mock IncidentService incidentService;
    @Mock RemediationService remediationService;
    @Mock IncidentAdvisor advisor;
    @Mock AnalysisContextBuilder contextBuilder;

    AgenticIncidentLoop loop;

    private static final UUID SERVICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        loop = new AgenticIncidentLoop(
                serviceRepo, signalRepo, incidentService, remediationService,
                advisor, contextBuilder);
    }

    // ── DEGRADED: happy path ───────────────────────────────────────────────

    @Test
    @DisplayName("DEGRADED → creates DRAFT incident and PENDING suggestion")
    void degraded_createsIncidentAndSuggestion() {
        MonitoredService svc = service(ServiceHealthStatus.DEGRADED);
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(SERVICE_ID))
                .thenReturn(List.of());
        when(contextBuilder.build(any(), any(), eq(TriggerReason.AUTO_DEGRADED)))
                .thenReturn(dummyContext());
        when(advisor.analyse(any())).thenReturn(recommendation("HIGH"));

        Incident newIncident = incident();
        when(incidentService.createDraft(any(), any(), any(), any(), eq(true)))
                .thenReturn(IncidentDraftResult.created(newIncident));

        loop.onStatusChanged(degradedEvent(svc));

        verify(incidentService).createDraft(eq(svc), any(), any(),
                eq(IncidentSeverity.HIGH), eq(true));
        verify(remediationService).createFromRecommendation(
                eq(newIncident), any(), eq(TriggerReason.AUTO_DEGRADED));
    }

    // ── Idempotency / noisy-signal guard ──────────────────────────────────

    @Test
    @DisplayName("DEGRADED repeated — open incident already exists → suggestion skipped")
    void degraded_openIncidentExists_skipssuggestion() {
        MonitoredService svc = service(ServiceHealthStatus.DEGRADED);
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(SERVICE_ID))
                .thenReturn(List.of());
        when(contextBuilder.build(any(), any(), any())).thenReturn(dummyContext());
        when(advisor.analyse(any())).thenReturn(recommendation("MEDIUM"));

        Incident existing = incident();
        when(incidentService.createDraft(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(IncidentDraftResult.skipped(existing));

        loop.onStatusChanged(degradedEvent(svc));

        // Suggestion must NOT be created — noisy-signal guard
        verifyNoInteractions(remediationService);
    }

    @Test
    @DisplayName("DEGRADED twice in a row — createDraft called twice, suggestion only on first")
    void degraded_twiceCalled_onlyFirstCreatessuggestion() {
        MonitoredService svc = service(ServiceHealthStatus.DEGRADED);
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(SERVICE_ID))
                .thenReturn(List.of());
        when(contextBuilder.build(any(), any(), any())).thenReturn(dummyContext());
        when(advisor.analyse(any())).thenReturn(recommendation("HIGH"));

        Incident newIncident = incident();
        // First call → CREATED; second call → SKIPPED
        when(incidentService.createDraft(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(IncidentDraftResult.created(newIncident))
                .thenReturn(IncidentDraftResult.skipped(newIncident));

        loop.onStatusChanged(degradedEvent(svc));
        loop.onStatusChanged(degradedEvent(svc));

        verify(remediationService, times(1))
                .createFromRecommendation(any(), any(), any());
    }

    // ── AI failure → rule-based fallback ──────────────────────────────────

    @Test
    @DisplayName("AI advisor throws → FallbackAdvisor returns rule-based rec, draft still created")
    void degraded_aiThrows_fallbackRuleBasedStillCreatesIncident() {
        MonitoredService svc = service(ServiceHealthStatus.DEGRADED);
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(SERVICE_ID))
                .thenReturn(List.of());
        when(contextBuilder.build(any(), any(), any())).thenReturn(dummyContext());

        // Simulate FallbackIncidentAdvisor returning rule-based rec despite LLM failure
        AdvisorRecommendation ruleBasedRec = recommendation("MEDIUM");
        when(advisor.analyse(any())).thenReturn(ruleBasedRec);

        Incident newIncident = incident();
        when(incidentService.createDraft(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(IncidentDraftResult.created(newIncident));

        loop.onStatusChanged(degradedEvent(svc));

        // Draft and suggestion created with rule-based recommendation
        verify(incidentService).createDraft(any(), any(), any(),
                eq(IncidentSeverity.MEDIUM), eq(true));
        verify(remediationService).createFromRecommendation(
                eq(newIncident), eq(ruleBasedRec), eq(TriggerReason.AUTO_DEGRADED));
    }

    @Test
    @DisplayName("advisor throws uncaught exception → error logged, no suggestion, no crash")
    void degraded_advisorThrowsUncaught_loopDoesNotCrash() {
        MonitoredService svc = service(ServiceHealthStatus.DEGRADED);
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(signalRepo.findTop20ByServiceIdOrderByRecordedAtDesc(SERVICE_ID))
                .thenReturn(List.of());
        when(contextBuilder.build(any(), any(), any())).thenReturn(dummyContext());
        when(advisor.analyse(any())).thenThrow(new RuntimeException("unexpected"));

        // Should not throw out of the loop
        loop.onStatusChanged(degradedEvent(svc));

        verifyNoInteractions(remediationService);
    }

    // ── Service not found (defensive) ─────────────────────────────────────

    @Test
    @DisplayName("service deleted before async fires → logged, no NPE")
    void degraded_serviceNotFound_abortsSilently() {
        when(serviceRepo.findById(SERVICE_ID)).thenReturn(Optional.empty());
        MonitoredService ghost = service(ServiceHealthStatus.DEGRADED);

        loop.onStatusChanged(degradedEvent(ghost));

        verifyNoInteractions(incidentService, remediationService, advisor);
    }

    // ── RECOVERED path ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("RECOVERED path")
    class RecoveredTests {

        @Test
        @DisplayName("RECOVERED → auto-resolves DRAFT incidents")
        void recovered_resolvesDrafts() {
            MonitoredService svc = service(ServiceHealthStatus.RECOVERED);
            Incident draft = incident();
            when(incidentService.autoResolveDrafts(SERVICE_ID)).thenReturn(List.of(draft));

            loop.onStatusChanged(recoveredEvent(svc));

            verify(incidentService).autoResolveDrafts(SERVICE_ID);
        }

        @Test
        @DisplayName("RECOVERED with no open drafts — autoResolveDrafts called, empty list is ok")
        void recovered_noDrafts_noError() {
            MonitoredService svc = service(ServiceHealthStatus.RECOVERED);
            when(incidentService.autoResolveDrafts(SERVICE_ID))
                    .thenReturn(List.of());

            loop.onStatusChanged(recoveredEvent(svc));

            verify(incidentService).autoResolveDrafts(SERVICE_ID);
        }

        @Test
        @DisplayName("RECOVERED auto-resolve throws → error logged, loop does not crash")
        void recovered_autoResolveThrows_doesNotCrash() {
            MonitoredService svc = service(ServiceHealthStatus.RECOVERED);
            when(incidentService.autoResolveDrafts(SERVICE_ID))
                    .thenThrow(new RuntimeException("db error"));

            loop.onStatusChanged(recoveredEvent(svc));
            // No exception propagated
        }

        @Test
        @DisplayName("RECOVERED does not call incidentService.createDraft or remediationService")
        void recovered_doesNotCreateDraftOrSuggestion() {
            MonitoredService svc = service(ServiceHealthStatus.RECOVERED);
            when(incidentService.autoResolveDrafts(SERVICE_ID)).thenReturn(List.of());

            loop.onStatusChanged(recoveredEvent(svc));

            verify(incidentService, never()).createDraft(any(), any(), any(), any(), anyBoolean());
            verifyNoInteractions(remediationService);
        }
    }

    // ── HEALTHY transition (no-op for loop) ───────────────────────────────

    @Test
    @DisplayName("HEALTHY event — loop does nothing")
    void healthy_eventIgnored() {
        MonitoredService svc = service(ServiceHealthStatus.HEALTHY);
        loop.onStatusChanged(new ServiceStatusChangedEvent(
                svc, ServiceHealthStatus.RECOVERED, ServiceHealthStatus.HEALTHY));

        verifyNoInteractions(serviceRepo, incidentService, remediationService, advisor);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private MonitoredService service(ServiceHealthStatus status) {
        MonitoredService s = new MonitoredService();
        s.setName("payment-api");
        s.setTeamOwner("platform");
        // Force the UUID via reflection since JPA normally assigns it
        try {
            var f = MonitoredService.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, SERVICE_ID);
        } catch (Exception e) { throw new RuntimeException(e); }
        return s;
    }

    private Incident incident() {
        Incident i = new Incident();
        try {
            var f = Incident.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(i, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
        return i;
    }

    private ServiceStatusChangedEvent degradedEvent(MonitoredService svc) {
        return new ServiceStatusChangedEvent(
                svc, ServiceHealthStatus.HEALTHY, ServiceHealthStatus.DEGRADED);
    }

    private ServiceStatusChangedEvent recoveredEvent(MonitoredService svc) {
        return new ServiceStatusChangedEvent(
                svc, ServiceHealthStatus.DEGRADED, ServiceHealthStatus.RECOVERED);
    }

    private AnalysisContext dummyContext() {
        return new AnalysisContext(
                "payment-api", "platform", ServiceHealthStatus.DEGRADED,
                Instant.now(), TriggerReason.AUTO_DEGRADED,
                List.of(), List.of(), Instant.now());
    }

    private AdvisorRecommendation recommendation(String severity) {
        return new AdvisorRecommendation(
                IncidentSeverity.valueOf(severity),
                "root cause",
                "restart the pods and check upstream dependencies",
                0.85,
                "reasoning",
                "[" + severity + "] payment-api — AUTO_DEGRADED degradation detected",
                "summary",
                "rule-based"
        );
    }
}
