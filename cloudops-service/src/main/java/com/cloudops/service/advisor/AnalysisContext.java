package com.cloudops.service.advisor;

import com.cloudops.domain.enums.MetricType;
import com.cloudops.domain.enums.ServiceHealthStatus;
import com.cloudops.domain.enums.TriggerReason;

import java.time.Instant;
import java.util.List;

/**
 * Structured multi-signal snapshot handed to every {@link IncidentAdvisor}.
 *
 * <p>Built once per analysis request by {@link AnalysisContextBuilder} and
 * passed immutably to the advisor. The LLM advisor serialises this to a
 * structured prompt; the rule-based advisor reads the fields directly.
 *
 * <h3>What the LLM sees</h3>
 * <pre>
 *   service:        payment-api  (team: platform-payments)
 *   status:         DEGRADED  (since 14:22 UTC, 4m 37s ago)
 *   trigger:        AUTO_DEGRADED
 *   signal timeline (newest → oldest):
 *     ERROR_RATE   12.40%  [BREACHED]  14:26
 *     ERROR_RATE    8.10%  [BREACHED]  14:25
 *     LATENCY_P99  1840ms  [BREACHED]  14:25
 *     LATENCY_P99   320ms             14:24
 *     CPU           44.0%             14:24
 *   breach summary:
 *     ERROR_RATE  : breached for 2 consecutive readings, peak 12.40%, trend RISING
 *     LATENCY_P99 : breached for 1 reading,             peak 1840ms,  trend RISING
 * </pre>
 *
 * @param serviceName        service identifier
 * @param teamOwner          owning team (for escalation context)
 * @param currentStatus      health status at analysis time
 * @param degradedSince      when the service first entered DEGRADED; null if not DEGRADED
 * @param triggerReason      what caused the analysis to run
 * @param signalTimeline     ordered signal snapshots, newest first
 * @param breachSummaries    per-metric aggregated breach context
 * @param analysisRequestedAt wall-clock time this context was assembled
 */
public record AnalysisContext(
        String serviceName,
        String teamOwner,
        ServiceHealthStatus currentStatus,
        Instant degradedSince,
        TriggerReason triggerReason,
        List<SignalSnapshot> signalTimeline,
        List<MetricBreachSummary> breachSummaries,
        Instant analysisRequestedAt
) {

    // ── Nested value objects ───────────────────────────────────────────────

    /**
     * A single point-in-time metric reading in the timeline.
     *
     * @param metricType       what was measured
     * @param value            observed value
     * @param thresholdBreached true when this reading crossed its configured threshold
     * @param recordedAt        when the reading was taken
     */
    public record SignalSnapshot(
            MetricType metricType,
            double value,
            boolean thresholdBreached,
            Instant recordedAt
    ) {}

    /**
     * Per-metric breach aggregation across the rolling window.
     *
     * @param metricType            metric being summarised
     * @param consecutiveBreaches   number of consecutive breached readings (newest-first run)
     * @param peakValue             highest observed value in the window
     * @param latestValue           most recent reading
     * @param threshold             the configured threshold for this metric
     * @param trend                 direction of the metric over the window
     */
    public record MetricBreachSummary(
            MetricType metricType,
            int consecutiveBreaches,
            double peakValue,
            double latestValue,
            double threshold,
            Trend trend
    ) {
        public boolean isBreached() { return consecutiveBreaches > 0; }
    }

    /** Direction of a metric's value over the rolling signal window. */
    public enum Trend { RISING, FALLING, STABLE }
}
