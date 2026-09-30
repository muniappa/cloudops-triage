package com.cloudops.service.advisor;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.MetricType;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rule-based {@link IncidentAdvisor} — updated to consume {@link AnalysisContext}.
 *
 * <p>Active when {@code cloudops.advisor.type=rule-based} (the default), or
 * automatically used as the fallback when the LLM advisor fails.
 * Produces deterministic, auditable recommendations with no external calls.
 */
public class RuleBasedIncidentAdvisor implements IncidentAdvisor {

    static final String ADVISOR_TYPE = "rule-based";

    private static final double ERROR_RATE_CRITICAL = 10.0;
    private static final double ERROR_RATE_HIGH     = 5.0;
    private static final double ERROR_RATE_MEDIUM   = 2.0;

    private static final double LATENCY_CRITICAL_MS = 2000.0;
    private static final double LATENCY_HIGH_MS     = 1000.0;
    private static final double LATENCY_MEDIUM_MS   = 500.0;

    private static final double CPU_CRITICAL = 90.0;
    private static final double CPU_HIGH     = 75.0;
    private static final double CPU_MEDIUM   = 60.0;

    @Override
    public String advisorType() { return ADVISOR_TYPE; }

    @Override
    public AdvisorRecommendation analyse(AnalysisContext ctx) {
        // Collapse latest value per metric from the timeline
        Map<MetricType, Double> latest = ctx.signalTimeline().stream()
                .collect(Collectors.toMap(
                        AnalysisContext.SignalSnapshot::metricType,
                        AnalysisContext.SignalSnapshot::value,
                        (newer, older) -> newer   // timeline is newest-first
                ));

        IncidentSeverity severity = deriveSeverity(latest);
        String rootCause = deriveRootCause(ctx, latest);
        String action    = deriveAction(ctx, latest);
        String reasoning = buildReasoning(ctx, latest);

        String title   = "[%s] %s — %s degradation detected"
                .formatted(severity.name(), ctx.serviceName(), ctx.triggerReason().name());
        String summary = "Rule-based analysis (%s). %s".formatted(ctx.triggerReason().name(), reasoning);

        return new AdvisorRecommendation(
                severity, rootCause, action, 0.72, reasoning, title, summary, ADVISOR_TYPE);
    }

    // ── Severity ───────────────────────────────────────────────────────────

    private IncidentSeverity deriveSeverity(Map<MetricType, Double> m) {
        double er  = m.getOrDefault(MetricType.ERROR_RATE,   0.0);
        double lat = m.getOrDefault(MetricType.LATENCY_P99,  0.0);
        double cpu = m.getOrDefault(MetricType.CPU_USAGE,    0.0);
        double mem = m.getOrDefault(MetricType.MEMORY_USAGE, 0.0);

        if (er >= ERROR_RATE_CRITICAL || lat >= LATENCY_CRITICAL_MS || cpu >= CPU_CRITICAL || mem >= CPU_CRITICAL)
            return IncidentSeverity.CRITICAL;
        if (er >= ERROR_RATE_HIGH || lat >= LATENCY_HIGH_MS || cpu >= CPU_HIGH || mem >= CPU_HIGH)
            return IncidentSeverity.HIGH;
        if (er >= ERROR_RATE_MEDIUM || lat >= LATENCY_MEDIUM_MS || cpu >= CPU_MEDIUM || mem >= CPU_MEDIUM)
            return IncidentSeverity.MEDIUM;
        return IncidentSeverity.LOW;
    }

    // ── Root cause (uses trend context from AnalysisContext) ───────────────

    private String deriveRootCause(AnalysisContext ctx, Map<MetricType, Double> m) {
        double er  = m.getOrDefault(MetricType.ERROR_RATE,   0.0);
        double lat = m.getOrDefault(MetricType.LATENCY_P99,  0.0);
        double cpu = m.getOrDefault(MetricType.CPU_USAGE,    0.0);
        double mem = m.getOrDefault(MetricType.MEMORY_USAGE, 0.0);

        // Enrich with trend when available
        AnalysisContext.Trend latTrend = trendFor(ctx, MetricType.LATENCY_P99);
        AnalysisContext.Trend erTrend  = trendFor(ctx, MetricType.ERROR_RATE);

        if (cpu >= CPU_HIGH && lat >= LATENCY_HIGH_MS) {
            return "CPU saturation in '%s' is causing request queuing and elevated p99 latency%s."
                    .formatted(ctx.serviceName(),
                               latTrend == AnalysisContext.Trend.RISING ? " (trend: RISING)" : "");
        }
        if (mem >= CPU_HIGH) {
            return "Memory pressure in '%s' risks OOM; heap usage is above safe threshold.".formatted(ctx.serviceName());
        }
        if (er >= ERROR_RATE_HIGH) {
            return "Elevated error rate in '%s'%s suggests dependency failure or a bad deployment."
                    .formatted(ctx.serviceName(),
                               erTrend == AnalysisContext.Trend.RISING ? " (trend: RISING)" : "");
        }
        if (lat >= LATENCY_HIGH_MS) {
            return "High p99 latency in '%s' may indicate upstream dependency slowness or "
                    .formatted(ctx.serviceName()) + "DB connection pool exhaustion.";
        }
        return "Anomalous readings in '%s'. Manual investigation required.".formatted(ctx.serviceName());
    }

    // ── Remediation action ─────────────────────────────────────────────────

    private String deriveAction(AnalysisContext ctx, Map<MetricType, Double> m) {
        double er  = m.getOrDefault(MetricType.ERROR_RATE,   0.0);
        double cpu = m.getOrDefault(MetricType.CPU_USAGE,    0.0);
        double mem = m.getOrDefault(MetricType.MEMORY_USAGE, 0.0);

        if (cpu >= CPU_CRITICAL)
            return "Scale out '%s' immediately or restart highest-CPU pods; "
                    .formatted(ctx.serviceName())
                    + "check for CPU-bound loops introduced by recent deployments.";
        if (mem >= CPU_HIGH)
            return "Restart the highest-memory pods in '%s'; check for heap leaks "
                    .formatted(ctx.serviceName())
                    + "and review allocations introduced in recent deployments.";
        if (er >= ERROR_RATE_HIGH)
            return "Review recent deployments to '%s' and upstream dependencies; "
                    .formatted(ctx.serviceName())
                    + "inspect error logs and consider rollback.";
        return "Inspect '%s' pod logs and dependency health; ".formatted(ctx.serviceName())
                + "check DB connection pool metrics and recent config changes.";
    }

    // ── Reasoning ─────────────────────────────────────────────────────────

    private String buildReasoning(AnalysisContext ctx, Map<MetricType, Double> m) {
        StringBuilder sb = new StringBuilder("Signal snapshot: ");
        m.forEach((type, v) -> sb.append(type).append("=")
                                  .append(String.format("%.2f", v)).append(" "));

        // Annotate with breach run lengths and trends
        ctx.breachSummaries().stream()
                .filter(AnalysisContext.MetricBreachSummary::isBreached)
                .forEach(s -> sb.append(String.format("| %s consecutive_breaches=%d trend=%s ",
                        s.metricType(), s.consecutiveBreaches(), s.trend())));

        sb.append("| trigger=").append(ctx.triggerReason());
        return sb.toString().trim();
    }

    private AnalysisContext.Trend trendFor(AnalysisContext ctx, MetricType type) {
        return ctx.breachSummaries().stream()
                .filter(s -> s.metricType() == type)
                .map(AnalysisContext.MetricBreachSummary::trend)
                .findFirst()
                .orElse(AnalysisContext.Trend.STABLE);
    }
}
