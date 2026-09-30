package com.cloudops.service.advisor;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.enums.TriggerReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LlmIncidentAdvisor}, {@link FallbackIncidentAdvisor},
 * and response validation logic.
 *
 * Uses MockWebServer (OkHttp) to simulate the LLM HTTP endpoint in-process.
 * No Spring context is loaded.
 */
class LlmIncidentAdvisorTest {

    private static MockWebServer mockServer;
    private static ObjectMapper objectMapper;
    private LlmIncidentAdvisor advisor;

    @BeforeAll
    static void startServer() throws Exception {
        mockServer    = new MockWebServer();
        objectMapper  = new ObjectMapper();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        mockServer.shutdown();
    }

    @BeforeEach
    void buildAdvisor() {
        String baseUrl = mockServer.url("/").toString();
        AdvisorProperties.LlmProperties config = new AdvisorProperties.LlmProperties(
                baseUrl, "test-key", "gpt-4o",
                java.time.Duration.ofSeconds(5), 512, 0.2
        );
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer test-key")
                .defaultHeader("Content-Type", "application/json")
                .build();
        advisor = new LlmIncidentAdvisor(client, config, objectMapper);
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid LLM response → AdvisorRecommendation with all fields")
    void happyPath_validResponse() throws Exception {
        enqueue(200, validLlmBody(
                "HIGH", "DB pool exhausted", "Scale replicas and check connection pool",
                0.88, "Latency rising for 3 consecutive readings, error rate following"
        ));

        AdvisorRecommendation rec = advisor.analyse(testContext());

        assertThat(rec.severity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(rec.rootCauseHypothesis()).isEqualTo("DB pool exhausted");
        assertThat(rec.recommendedAction()).isEqualTo("Scale replicas and check connection pool");
        assertThat(rec.confidence()).isEqualTo(0.88);
        assertThat(rec.advisorType()).isEqualTo(LlmIncidentAdvisor.ADVISOR_TYPE);
        assertThat(rec.incidentTitle()).contains("payment-api");

        RecordedRequest req = mockServer.takeRequest();
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key");
    }

    // ── Severity validation ────────────────────────────────────────────────

    @Test
    @DisplayName("invalid severity enum → AdvisorException")
    void invalidSeverity_throws() {
        // Single-line body — no literal newlines inside the JSON string value
        enqueue(200, buildChoicesResponse(
                "BLOCKER", "x", "do something useful here", 0.5, "r"
        ));
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("severity");
    }

    // ── Action plausibility ────────────────────────────────────────────────

    @Test
    @DisplayName("recommendedAction too short (< 20 chars) → AdvisorException")
    void shortAction_throws() {
        enqueue(200, buildChoicesResponse(
                "HIGH", "some root cause", "Do it", 0.7, "reasoning text"
        ));
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("implausible");
    }

    // ── Confidence range ───────────────────────────────────────────────────

    @Test
    @DisplayName("confidence > 1.0 → AdvisorException")
    void confidenceOutOfRange_throws() {
        enqueue(200, buildChoicesResponse(
                "MEDIUM", "some root cause", "Scale replicas and check connection pool", 1.5, "reasoning"
        ));
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("confidence");
    }

    // ── Malformed JSON ─────────────────────────────────────────────────────

    @Test
    @DisplayName("non-JSON content in message → AdvisorException")
    void malformedJson_throws() {
        enqueue(200, """
                {"choices":[{"message":{"content":"This is not JSON at all."}}]}
                """);
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("not valid JSON");
    }

    // ── HTTP errors ────────────────────────────────────────────────────────

    @Test
    @DisplayName("LLM returns 500 → AdvisorException with status code")
    void httpError500_throws() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("empty choices array → AdvisorException")
    void emptyChoices_throws() {
        enqueue(200, """
                {"choices":[]}
                """);
        assertThatThrownBy(() -> advisor.analyse(testContext()))
                .isInstanceOf(AdvisorException.class)
                .hasMessageContaining("choices");
    }

    // ── FallbackIncidentAdvisor ────────────────────────────────────────────

    @Nested
    @DisplayName("FallbackIncidentAdvisor")
    class FallbackTests {

        private FallbackIncidentAdvisor fallbackAdvisor;
        private RuleBasedIncidentAdvisor ruleBasedAdvisor;

        @BeforeEach
        void setup() {
            ruleBasedAdvisor = new RuleBasedIncidentAdvisor();
            fallbackAdvisor  = new FallbackIncidentAdvisor(advisor, ruleBasedAdvisor);
        }

        @Test
        @DisplayName("LLM fails → falls back to rule-based, returns valid recommendation")
        void llmFails_fallsBackToRuleBased() {
            // LLM returns 503 — simulates LLM being down during outage
            mockServer.enqueue(new MockResponse().setResponseCode(503));

            AdvisorRecommendation rec = fallbackAdvisor.analyse(testContext());

            assertThat(rec).isNotNull();
            assertThat(rec.severity()).isNotNull();
            assertThat(rec.advisorType()).isEqualTo(RuleBasedIncidentAdvisor.ADVISOR_TYPE);
            assertThat(rec.isValid()).isTrue();
        }

        @Test
        @DisplayName("LLM returns invalid recommendation → falls back to rule-based")
        void llmInvalidRec_fallsBackToRuleBased() {
            // Short action will fail validation → FallbackAdvisor switches to rule-based
            enqueue(200, buildChoicesResponse(
                    "HIGH", "root cause", "Too short", 0.9, "reasoning"
            ));

            AdvisorRecommendation rec = fallbackAdvisor.analyse(testContext());

            assertThat(rec.advisorType()).isEqualTo(RuleBasedIncidentAdvisor.ADVISOR_TYPE);
        }

        @Test
        @DisplayName("valid LLM response → primary result used, not fallback")
        void llmSucceeds_primaryUsed() throws Exception {
            enqueue(200, validLlmBody(
                    "CRITICAL", "Auth service unresponsive",
                    "Immediately restart auth-service pods and check upstream DNS",
                    0.91, "Three consecutive CRITICAL error rate breaches with RISING trend"
            ));

            AdvisorRecommendation rec = fallbackAdvisor.analyse(testContext());

            assertThat(rec.advisorType()).isEqualTo(LlmIncidentAdvisor.ADVISOR_TYPE);
            assertThat(rec.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        }
    }

    // ── AnalysisContextBuilder ─────────────────────────────────────────────

    @Nested
    @DisplayName("AnalysisContextBuilder")
    class ContextBuilderTests {

        private AnalysisContextBuilder builder;

        @BeforeEach
        void setup() { builder = new AnalysisContextBuilder(); }

        @Test
        @DisplayName("builds context with correct service fields")
        void buildsServiceFields() {
            AnalysisContext ctx = builder.build(testService(), List.of(), TriggerReason.MANUAL);
            assertThat(ctx.serviceName()).isEqualTo("payment-api");
            assertThat(ctx.teamOwner()).isEqualTo("platform-payments");
            assertThat(ctx.triggerReason()).isEqualTo(TriggerReason.MANUAL);
            assertThat(ctx.analysisRequestedAt()).isNotNull();
        }

        @Test
        @DisplayName("empty signals → empty timeline and summaries")
        void emptySignals_emptyCollections() {
            AnalysisContext ctx = builder.build(testService(), List.of(), TriggerReason.MANUAL);
            assertThat(ctx.signalTimeline()).isEmpty();
            assertThat(ctx.breachSummaries()).isEmpty();
        }

        @Test
        @DisplayName("signals are projected into timeline correctly")
        void signalsProjectedToTimeline() {
            var signals = List.of(
                    signal(com.cloudops.domain.enums.MetricType.ERROR_RATE, 8.5, true),
                    signal(com.cloudops.domain.enums.MetricType.LATENCY_P99, 1200.0, true)
            );
            AnalysisContext ctx = builder.build(testService(), signals, TriggerReason.AUTO_DEGRADED);

            assertThat(ctx.signalTimeline()).hasSize(2);
            assertThat(ctx.signalTimeline().get(0).metricType())
                    .isEqualTo(com.cloudops.domain.enums.MetricType.ERROR_RATE);
            assertThat(ctx.signalTimeline().get(0).thresholdBreached()).isTrue();
        }

        @Test
        @DisplayName("consecutive breaches are counted correctly")
        void consecutiveBreaches_counted() {
            var signals = List.of(
                    signal(com.cloudops.domain.enums.MetricType.ERROR_RATE, 9.0, true),  // newest
                    signal(com.cloudops.domain.enums.MetricType.ERROR_RATE, 7.0, true),
                    signal(com.cloudops.domain.enums.MetricType.ERROR_RATE, 1.5, false)  // no breach
            );
            AnalysisContext ctx = builder.build(testService(), signals, TriggerReason.AUTO_DEGRADED);

            AnalysisContext.MetricBreachSummary summary = ctx.breachSummaries().stream()
                    .filter(s -> s.metricType() == com.cloudops.domain.enums.MetricType.ERROR_RATE)
                    .findFirst().orElseThrow();

            assertThat(summary.consecutiveBreaches()).isEqualTo(2);
            assertThat(summary.peakValue()).isEqualTo(9.0);
            assertThat(summary.latestValue()).isEqualTo(9.0);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static AnalysisContext testContext() {
        return new AnalysisContext(
                "payment-api", "platform-payments",
                ServiceHealthStatus.DEGRADED,
                Instant.now().minusSeconds(180),
                TriggerReason.AUTO_DEGRADED,
                List.of(
                        new AnalysisContext.SignalSnapshot(
                                com.cloudops.domain.enums.MetricType.ERROR_RATE,
                                8.5, true, Instant.now().minusSeconds(10)),
                        new AnalysisContext.SignalSnapshot(
                                com.cloudops.domain.enums.MetricType.LATENCY_P99,
                                1350.0, true, Instant.now().minusSeconds(20))
                ),
                List.of(
                        new AnalysisContext.MetricBreachSummary(
                                com.cloudops.domain.enums.MetricType.ERROR_RATE,
                                2, 8.5, 8.5, 2.0, AnalysisContext.Trend.RISING)
                ),
                Instant.now()
        );
    }

    private static com.cloudops.domain.model.MonitoredService testService() {
        var svc = new com.cloudops.domain.model.MonitoredService();
        svc.setName("payment-api");
        svc.setTeamOwner("platform-payments");
        return svc;
    }

    private static com.cloudops.domain.model.HealthSignal signal(
            com.cloudops.domain.enums.MetricType type, double value, boolean breached) {
        var s = new com.cloudops.domain.model.HealthSignal();
        s.setMetricType(type);
        s.setValue(value);
        s.setThresholdBreached(breached);
        s.setRecordedAt(Instant.now());
        return s;
    }

    private void enqueue(int status, String body) {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private static String validLlmBody(String severity, String rootCause,
                                        String action, double confidence, String reasoning) {
        return buildChoicesResponse(severity, rootCause, action, confidence, reasoning);
    }

    private static String buildChoicesResponse(String severity, String rootCause,
                                                String action, double confidence, String reasoning) {
        String content = """
                {"severity":"%s","rootCause":"%s","recommendedAction":"%s",
                 "confidence":%s,"reasoning":"%s"}
                """.formatted(severity, rootCause, action, confidence, reasoning).strip();
        // Escape for embedding in outer JSON
        String escaped = content.replace("\"", "\\\"").replace("\n", "");
        return """
                {"choices":[{"message":{"content":"%s"}}]}
                """.formatted(escaped);
    }
}
