package com.cloudops.service.correlation;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.MetricType;

/**
 * Immutable value object that encodes a single threshold rule for one metric.
 *
 * <p>A {@code ThresholdRule} binds together:
 * <ul>
 *   <li>the metric being watched ({@link MetricType})</li>
 *   <li>three escalating threshold values (MEDIUM, HIGH, CRITICAL)</li>
 *   <li>deterministic remediation hints for each severity band</li>
 * </ul>
 *
 * <p>Rules are intentionally immutable records so they can be shared across
 * threads without synchronisation and composed freely into analyzer instances.
 *
 * <h3>Example — error rate rule</h3>
 * <pre>{@code
 * ThresholdRule rule = ThresholdRule.errorRate(2.0, 5.0, 10.0);
 * ThresholdRule.Evaluation eval = rule.evaluate(7.3);
 * // eval.breached()  → true
 * // eval.severity()  → HIGH
 * // eval.threshold() → 5.0
 * }</pre>
 */
public record ThresholdRule(
        MetricType metricType,
        double mediumThreshold,
        double highThreshold,
        double criticalThreshold,
        String remediationMedium,
        String remediationHigh,
        String remediationCritical
) {

    // ── Factory methods for the three supported metric types ───────────────

    /**
     * Standard error-rate rule.
     *
     * @param medium   percentage threshold for MEDIUM (e.g. 2.0 → 2%)
     * @param high     percentage threshold for HIGH   (e.g. 5.0 → 5%)
     * @param critical percentage threshold for CRITICAL (e.g. 10.0 → 10%)
     */
    public static ThresholdRule errorRate(double medium, double high, double critical) {
        return new ThresholdRule(
                MetricType.ERROR_RATE,
                medium, high, critical,
                "Investigate error logs and check upstream dependencies for partial failures.",
                "Review recent deployments; consider rollback. Check circuit-breaker open states.",
                "Immediately roll back the last deployment or reroute traffic away from this service."
        );
    }

    /**
     * Standard p99 latency rule.
     *
     * @param medium   ms threshold for MEDIUM   (e.g. 500.0)
     * @param high     ms threshold for HIGH     (e.g. 1000.0)
     * @param critical ms threshold for CRITICAL (e.g. 2000.0)
     */
    public static ThresholdRule latencyP99(double medium, double high, double critical) {
        return new ThresholdRule(
                MetricType.LATENCY_P99,
                medium, high, critical,
                "Check DB connection pool utilisation and slow query logs.",
                "Scale replicas horizontally; inspect upstream service p99 latency.",
                "Scale replicas immediately and open a bridge call with the DB and upstream teams."
        );
    }

    /**
     * Standard CPU utilisation rule.
     *
     * @param medium   percentage threshold for MEDIUM   (e.g. 60.0)
     * @param high     percentage threshold for HIGH     (e.g. 75.0)
     * @param critical percentage threshold for CRITICAL (e.g. 90.0)
     */
    public static ThresholdRule cpuUsage(double medium, double high, double critical) {
        return new ThresholdRule(
                MetricType.CPU_USAGE,
                medium, high, critical,
                "Profile the service for CPU-bound hot paths introduced by recent changes.",
                "Scale replicas horizontally; check for runaway goroutines or thread pool exhaustion.",
                "Scale replicas immediately; isolate and kill any runaway pods consuming excess CPU."
        );
    }

    public static ThresholdRule memoryUsage(double medium, double high, double critical) {
        return new ThresholdRule(
                MetricType.MEMORY_USAGE,
                medium, high, critical,
                "Check for memory leaks in recent deployments; review heap allocation patterns.",
                "Restart pods with highest memory usage; trigger GC and check for OOM risks.",
                "Roll back last deployment immediately; memory pressure will cause OOM kills."
        );
    }

    // ── Evaluation ─────────────────────────────────────────────────────────

    /**
     * Evaluates an observed metric value against this rule's thresholds.
     *
     * @param observedValue the current metric reading
     * @return an {@link Evaluation}; never null
     */
    public Evaluation evaluate(double observedValue) {
        if (observedValue >= criticalThreshold) {
            return new Evaluation(true, IncidentSeverity.CRITICAL, criticalThreshold,
                                  observedValue, remediationCritical);
        }
        if (observedValue >= highThreshold) {
            return new Evaluation(true, IncidentSeverity.HIGH, highThreshold,
                                  observedValue, remediationHigh);
        }
        if (observedValue >= mediumThreshold) {
            return new Evaluation(true, IncidentSeverity.MEDIUM, mediumThreshold,
                                  observedValue, remediationMedium);
        }
        return Evaluation.noBreached(observedValue);
    }

    // ── Evaluation result ──────────────────────────────────────────────────

    /**
     * The result of evaluating one {@link ThresholdRule} against a single value.
     *
     * @param breached      true if the observed value crosses any threshold
     * @param severity      severity band crossed; null when not breached
     * @param threshold     the specific threshold value that was crossed
     * @param observedValue the raw metric value that was evaluated
     * @param remediation   deterministic remediation hint for this severity band
     */
    public record Evaluation(
            boolean breached,
            IncidentSeverity severity,
            double threshold,
            double observedValue,
            String remediation
    ) {
        static Evaluation noBreached(double observed) {
            return new Evaluation(false, null, 0, observed, null);
        }
    }
}
