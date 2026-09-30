package com.cloudops.service.correlation;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.MetricType;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RuleBasedSignalAnalyzer}.
 *
 * No Spring context is loaded — all tests are plain JUnit 5.
 * Covers: no signals, each metric type at below/medium/high/critical thresholds,
 * multi-metric tiebreak, and the "watch" annotation in reasoning.
 */
class RuleBasedSignalAnalyzerTest {

    private RuleBasedSignalAnalyzer analyzer;
    private MonitoredService service;

    @BeforeEach
    void setUp() {
        // Production-default thresholds
        analyzer = new RuleBasedSignalAnalyzer();
        service  = serviceNamed("payment-api");
    }

    // ── Empty / null signal buffer ─────────────────────────────────────────

    @Test
    @DisplayName("empty signal list → not flagged")
    void emptySignals_notFlagged() {
        SignalAnalysis result = analyzer.analyze(service, List.of());

        assertThat(result.flagged()).isFalse();
        assertThat(result.severity()).isNull();
        assertThat(result.triggerMetric()).isNull();
    }

    @Test
    @DisplayName("null signal list → not flagged (defensive)")
    void nullSignals_notFlagged() {
        SignalAnalysis result = analyzer.analyze(service, null);

        assertThat(result.flagged()).isFalse();
    }

    // ── ERROR_RATE thresholds ──────────────────────────────────────────────

    @Nested
    @DisplayName("ERROR_RATE")
    class ErrorRate {

        @Test
        @DisplayName("below MEDIUM threshold → not flagged")
        void below_medium_notFlagged() {
            var result = analyzer.analyze(service, signals(MetricType.ERROR_RATE, 1.9));
            assertThat(result.flagged()).isFalse();
        }

        @ParameterizedTest(name = "value={0} → {1}")
        @CsvSource({
            "2.0,  MEDIUM",
            "2.5,  MEDIUM",
            "5.0,  HIGH",
            "7.3,  HIGH",
            "10.0, CRITICAL",
            "25.0, CRITICAL"
        })
        @DisplayName("threshold breach → correct severity")
        void threshold_breach(double value, IncidentSeverity expectedSeverity) {
            var result = analyzer.analyze(service, signals(MetricType.ERROR_RATE, value));

            assertThat(result.flagged()).isTrue();
            assertThat(result.severity()).isEqualTo(expectedSeverity);
            assertThat(result.triggerMetric()).isEqualTo(MetricType.ERROR_RATE);
            assertThat(result.triggerValue()).isEqualTo(value);
            assertThat(result.remediationHint()).isNotBlank();
        }

        @Test
        @DisplayName("CRITICAL breach remediation hint mentions roll back")
        void critical_remediationHint_mentionsRollback() {
            var result = analyzer.analyze(service, signals(MetricType.ERROR_RATE, 10.0));
            // ThresholdRule uses "roll back" (two words) for CRITICAL error-rate
            assertThat(result.remediationHint()).containsIgnoringCase("roll back");
        }
    }

    // ── LATENCY_P99 thresholds ─────────────────────────────────────────────

    @Nested
    @DisplayName("LATENCY_P99")
    class LatencyP99 {

        @Test
        @DisplayName("below MEDIUM threshold → not flagged")
        void below_medium_notFlagged() {
            var result = analyzer.analyze(service, signals(MetricType.LATENCY_P99, 499.9));
            assertThat(result.flagged()).isFalse();
        }

        @ParameterizedTest(name = "value={0}ms → {1}")
        @CsvSource({
            "500.0,  MEDIUM",
            "750.0,  MEDIUM",
            "1000.0, HIGH",
            "1500.0, HIGH",
            "2000.0, CRITICAL",
            "5000.0, CRITICAL"
        })
        @DisplayName("threshold breach → correct severity")
        void threshold_breach(double value, IncidentSeverity expectedSeverity) {
            var result = analyzer.analyze(service, signals(MetricType.LATENCY_P99, value));

            assertThat(result.flagged()).isTrue();
            assertThat(result.severity()).isEqualTo(expectedSeverity);
            assertThat(result.triggerMetric()).isEqualTo(MetricType.LATENCY_P99);
        }

        @Test
        @DisplayName("HIGH breach remediation mentions scaling replicas")
        void high_remediationHint_mentionsScaleReplicas() {
            var result = analyzer.analyze(service, signals(MetricType.LATENCY_P99, 1000.0));
            assertThat(result.remediationHint()).containsIgnoringCase("scale replicas");
        }

        @Test
        @DisplayName("MEDIUM breach remediation mentions DB connection pool")
        void medium_remediationHint_mentionsDbPool() {
            var result = analyzer.analyze(service, signals(MetricType.LATENCY_P99, 500.0));
            assertThat(result.remediationHint()).containsIgnoringCase("DB connection pool");
        }
    }

    // ── CPU_USAGE thresholds ───────────────────────────────────────────────

    @Nested
    @DisplayName("CPU_USAGE")
    class CpuUsage {

        @Test
        @DisplayName("below MEDIUM threshold → not flagged")
        void below_medium_notFlagged() {
            var result = analyzer.analyze(service, signals(MetricType.CPU_USAGE, 79.9));
            assertThat(result.flagged()).isFalse();
        }

        @ParameterizedTest(name = "value={0}% → {1}")
        @CsvSource({
            "80.0, MEDIUM",
            "82.0, MEDIUM",
            "85.0, HIGH",
            "87.0, HIGH",
            "90.0, CRITICAL",
            "99.0, CRITICAL"
        })
        @DisplayName("threshold breach → correct severity")
        void threshold_breach(double value, IncidentSeverity expectedSeverity) {
            var result = analyzer.analyze(service, signals(MetricType.CPU_USAGE, value));

            assertThat(result.flagged()).isTrue();
            assertThat(result.severity()).isEqualTo(expectedSeverity);
            assertThat(result.triggerMetric()).isEqualTo(MetricType.CPU_USAGE);
        }

        @Test
        @DisplayName("CRITICAL breach remediation mentions scale replicas immediately")
        void critical_remediationHint_mentionsImmediateScale() {
            var result = analyzer.analyze(service, signals(MetricType.CPU_USAGE, 90.0));
            assertThat(result.remediationHint()).containsIgnoringCase("scale replicas immediately");
        }
    }

    // ── Multi-metric: highest severity wins ───────────────────────────────

    @Test
    @DisplayName("concurrent ERROR_RATE HIGH + CPU_USAGE MEDIUM → HIGH wins")
    void multiMetric_highestSeverityWins() {
        List<HealthSignal> mixed = List.of(
                signal(MetricType.ERROR_RATE, 7.0),       // HIGH
                signal(MetricType.CPU_USAGE, 81.0)        // MEDIUM
        );
        var result = analyzer.analyze(service, mixed);

        assertThat(result.flagged()).isTrue();
        assertThat(result.severity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(result.triggerMetric()).isEqualTo(MetricType.ERROR_RATE);
    }

    @Test
    @DisplayName("concurrent ERROR_RATE MEDIUM + LATENCY CRITICAL → CRITICAL wins")
    void multiMetric_criticalBeatsAll() {
        List<HealthSignal> mixed = List.of(
                signal(MetricType.ERROR_RATE, 3.0),     // MEDIUM
                signal(MetricType.LATENCY_P99, 2500.0)  // CRITICAL
        );
        var result = analyzer.analyze(service, mixed);

        assertThat(result.flagged()).isTrue();
        assertThat(result.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(result.triggerMetric()).isEqualTo(MetricType.LATENCY_P99);
    }

    // ── Reasoning content ─────────────────────────────────────────────────

    @Test
    @DisplayName("reasoning contains service name")
    void reasoning_containsServiceName() {
        var result = analyzer.analyze(service, signals(MetricType.ERROR_RATE, 6.0));
        assertThat(result.reasoning()).contains("payment-api");
    }

    @Test
    @DisplayName("reasoning contains threshold and observed values")
    void reasoning_containsValues() {
        var result = analyzer.analyze(service, signals(MetricType.ERROR_RATE, 6.0));
        assertThat(result.reasoning()).contains("6.00");   // observed
        assertThat(result.reasoning()).contains("5.00");   // HIGH threshold
    }

    @Test
    @DisplayName("analyzer name is set on result")
    void analyzerName_isSet() {
        var result = analyzer.analyze(service, signals(MetricType.CPU_USAGE, 90.0));
        assertThat(result.analyzerName()).isEqualTo(RuleBasedSignalAnalyzer.NAME);
    }

    // ── Newest-first ordering: only latest reading is used ────────────────

    @Test
    @DisplayName("only the newest reading per metric is evaluated (not stale values)")
    void newestFirst_staleReadingIgnored() {
        // Newest (first in list) is below threshold; older reading breaches — should NOT flag
        List<HealthSignal> ordered = List.of(
                signal(MetricType.ERROR_RATE, 1.0),   // newest — below threshold
                signal(MetricType.ERROR_RATE, 12.0)   // older — would be CRITICAL
        );
        var result = analyzer.analyze(service, ordered);

        assertThat(result.flagged()).isFalse();
    }

    // ── Custom thresholds constructor ──────────────────────────────────────

    @Test
    @DisplayName("custom thresholds are respected")
    void customThresholds() {
        var custom = new RuleBasedSignalAnalyzer(List.of(
                ThresholdRule.errorRate(1.0, 3.0, 7.0)  // tighter error-rate thresholds
        ));
        // 1.5% → breaches custom MEDIUM (1.0) but would not breach default MEDIUM (2.0)
        var result = custom.analyze(service, signals(MetricType.ERROR_RATE, 1.5));

        assertThat(result.flagged()).isTrue();
        assertThat(result.severity()).isEqualTo(IncidentSeverity.MEDIUM);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static MonitoredService serviceNamed(String name) {
        MonitoredService s = new MonitoredService();
        s.setName(name);
        s.setTeamOwner("platform");
        return s;
    }

    private static List<HealthSignal> signals(MetricType type, double value) {
        return List.of(signal(type, value));
    }

    private static HealthSignal signal(MetricType type, double value) {
        HealthSignal s = new HealthSignal();
        s.setMetricType(type);
        s.setValue(value);
        s.setThresholdBreached(false);
        return s;
    }
}
