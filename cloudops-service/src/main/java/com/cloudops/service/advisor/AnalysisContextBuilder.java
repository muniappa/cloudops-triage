package com.cloudops.service.advisor;

import com.cloudops.domain.enums.MetricType;
import com.cloudops.domain.enums.TriggerReason;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles an {@link AnalysisContext} from raw domain objects.
 *
 * <p>Extracts the signal timeline, computes per-metric breach runs and
 * trends, and bundles everything into the immutable snapshot the advisor
 * needs. Kept as a separate component so it can be tested independently
 * and reused from both the agentic loop and the on-demand analyze endpoint.
 */
@Component
public class AnalysisContextBuilder {

    /** Default thresholds used for breach summaries (mirror RuleBasedSignalAnalyzer defaults). */
    private static final Map<MetricType, Double> DEFAULT_THRESHOLDS = Map.of(
            MetricType.ERROR_RATE,   2.0,
            MetricType.LATENCY_P99,  500.0,
            MetricType.CPU_USAGE,    80.0,
            MetricType.MEMORY_USAGE, 85.0
    );

    /**
     * Build a full {@link AnalysisContext}.
     *
     * @param service       the monitored service
     * @param signals       rolling signal buffer, ordered newest-first
     * @param triggerReason why this analysis was requested
     * @return an immutable context snapshot
     */
    public AnalysisContext build(MonitoredService service,
                                 List<HealthSignal> signals,
                                 TriggerReason triggerReason) {

        List<AnalysisContext.SignalSnapshot> timeline = buildTimeline(signals);
        List<AnalysisContext.MetricBreachSummary> summaries = buildBreachSummaries(signals);

        return new AnalysisContext(
                service.getName(),
                service.getTeamOwner(),
                service.getHealthStatus(),
                service.getLastStatusChangedAt(),   // degradedSince
                triggerReason,
                timeline,
                summaries,
                Instant.now()
        );
    }

    // ── Private builders ───────────────────────────────────────────────────

    private List<AnalysisContext.SignalSnapshot> buildTimeline(List<HealthSignal> signals) {
        return signals.stream()
                .map(s -> new AnalysisContext.SignalSnapshot(
                        s.getMetricType(),
                        s.getValue(),
                        s.isThresholdBreached(),
                        s.getRecordedAt()
                ))
                .toList();
    }

    private List<AnalysisContext.MetricBreachSummary> buildBreachSummaries(
            List<HealthSignal> signals) {

        // Group signals by metric type preserving newest-first order
        Map<MetricType, List<HealthSignal>> byType = new LinkedHashMap<>();
        for (HealthSignal s : signals) {
            byType.computeIfAbsent(s.getMetricType(), k -> new ArrayList<>()).add(s);
        }

        return byType.entrySet().stream()
                .map(e -> buildSummary(e.getKey(), e.getValue()))
                .toList();
    }

    private AnalysisContext.MetricBreachSummary buildSummary(MetricType type,
                                                              List<HealthSignal> readings) {
        // readings are newest-first
        int consecutive = 0;
        for (HealthSignal s : readings) {
            if (s.isThresholdBreached()) consecutive++;
            else break;
        }

        double peak   = readings.stream().mapToDouble(HealthSignal::getValue).max().orElse(0);
        double latest = readings.isEmpty() ? 0 : readings.get(0).getValue();
        double threshold = DEFAULT_THRESHOLDS.getOrDefault(type, 0.0);

        AnalysisContext.Trend trend = computeTrend(readings);

        return new AnalysisContext.MetricBreachSummary(
                type, consecutive, peak, latest, threshold, trend);
    }

    /**
     * Computes trend by comparing the mean of the newest half vs the oldest half
     * of the readings. Returns STABLE when the window is too small to be meaningful.
     */
    private AnalysisContext.Trend computeTrend(List<HealthSignal> readings) {
        if (readings.size() < 3) return AnalysisContext.Trend.STABLE;

        int mid = readings.size() / 2;
        // readings newest-first → newest half is [0..mid), oldest half is [mid..end)
        double newerMean = readings.subList(0, mid).stream()
                .mapToDouble(HealthSignal::getValue).average().orElse(0);
        double olderMean = readings.subList(mid, readings.size()).stream()
                .mapToDouble(HealthSignal::getValue).average().orElse(0);

        double delta = newerMean - olderMean;
        double relativeChange = olderMean == 0 ? 0 : Math.abs(delta) / olderMean;

        if (relativeChange < 0.05) return AnalysisContext.Trend.STABLE;
        return delta > 0 ? AnalysisContext.Trend.RISING : AnalysisContext.Trend.FALLING;
    }
}
