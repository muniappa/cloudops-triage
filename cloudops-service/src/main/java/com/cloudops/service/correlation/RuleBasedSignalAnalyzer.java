package com.cloudops.service.correlation;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.MetricType;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Rule-based {@link SignalAnalyzer} implementation.
 *
 * <p>Evaluates the most recent reading for each metric type against three
 * {@link ThresholdRule}s (ERROR_RATE, LATENCY_P99, CPU). When any rule is
 * breached the analyzer returns a flagged {@link SignalAnalysis} containing:
 * <ul>
 *   <li>the <em>highest</em> severity across all breached rules</li>
 *   <li>the metric that drove the highest severity as the trigger</li>
 *   <li>a deterministic, human-readable remediation hint appropriate to that metric and band</li>
 * </ul>
 *
 * <p>Thresholds are configurable via the constructor; the default instance
 * registered as a Spring bean uses production-appropriate defaults:
 * <pre>
 *   ERROR_RATE   :  2% (MEDIUM) | 5%  (HIGH) | 10%  (CRITICAL)
 *   LATENCY_P99  :  500ms       | 1000ms      | 2000ms
 *   CPU          :  60%         | 75%         | 90%
 * </pre>
 */
@Component
public class RuleBasedSignalAnalyzer implements SignalAnalyzer {

    static final String NAME = "RuleBasedSignalAnalyzer";

    private final List<ThresholdRule> rules;

    /** Default bean — production thresholds. */
    public RuleBasedSignalAnalyzer() {
        this(List.of(
                ThresholdRule.errorRate(2.0, 5.0, 10.0),
                ThresholdRule.latencyP99(500.0, 1000.0, 2000.0),
                ThresholdRule.cpuUsage(80.0, 85.0, 90.0),
                ThresholdRule.memoryUsage(85.0, 90.0, 95.0)
        ));
    }

    /** Overridable constructor for testing with custom thresholds. */
    public RuleBasedSignalAnalyzer(List<ThresholdRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SignalAnalysis analyze(MonitoredService service, List<HealthSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return SignalAnalysis.noOp(NAME);
        }

        // Reduce to the latest reading per metric type (signals arrive newest-first)
        Map<MetricType, Double> latestByType = signals.stream()
                .collect(Collectors.toMap(
                        HealthSignal::getMetricType,
                        HealthSignal::getValue,
                        (newer, older) -> newer   // keep first = newest
                ));

        // Evaluate every rule and collect breached evaluations
        List<RuleEval> breached = rules.stream()
                .filter(rule -> latestByType.containsKey(rule.metricType()))
                .map(rule -> new RuleEval(rule, rule.evaluate(latestByType.get(rule.metricType()))))
                .filter(re -> re.evaluation().breached())
                .toList();

        if (breached.isEmpty()) {
            return SignalAnalysis.noOp(NAME);
        }

        // Pick the highest-severity breach as the primary trigger
        RuleEval primary = breached.stream()
                .max(Comparator.comparingInt(re -> re.evaluation().severity().ordinal()))
                .orElseThrow();   // safe — breached is non-empty

        ThresholdRule.Evaluation eval = primary.evaluation();
        MetricType triggerMetric      = primary.rule().metricType();

        String reasoning = buildReasoning(service.getName(), breached, latestByType);

        return new SignalAnalysis(
                true,
                eval.severity(),
                triggerMetric,
                eval.observedValue(),
                eval.threshold(),
                eval.remediation(),
                reasoning,
                NAME
        );
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String buildReasoning(String serviceName,
                                   List<RuleEval> breached,
                                   Map<MetricType, Double> latestByType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Service '").append(serviceName).append("' breached thresholds: ");

        breached.forEach(re -> {
            ThresholdRule.Evaluation eval = re.evaluation();
            sb.append(re.rule().metricType().name())
              .append("=").append(String.format("%.2f", eval.observedValue()))
              .append(" (threshold ").append(String.format("%.2f", eval.threshold()))
              .append(", ").append(eval.severity()).append("); ");
        });

        // Note metrics that are elevated but below threshold — useful context
        latestByType.forEach((type, value) -> {
            boolean alreadyBreached = breached.stream()
                    .anyMatch(re -> re.rule().metricType() == type);
            if (!alreadyBreached) {
                Optional<ThresholdRule> rule = rules.stream()
                        .filter(r -> r.metricType() == type).findFirst();
                rule.ifPresent(r -> {
                    double pct = (value / r.mediumThreshold()) * 100.0;
                    if (pct >= 70) {
                        sb.append(type.name())
                          .append(" at ").append(String.format("%.0f%%", pct))
                          .append(" of MEDIUM threshold (watch); ");
                    }
                });
            }
        });

        return sb.toString().stripTrailing().replaceAll(";\\s*$", ".");
    }

    /** Internal tuple linking a rule to its evaluated result. */
    private record RuleEval(ThresholdRule rule, ThresholdRule.Evaluation evaluation) {}
}
