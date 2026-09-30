package com.cloudops.service.correlation;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.enums.MetricType;

/**
 * The output of a {@link SignalAnalyzer} evaluation.
 *
 * <p>A {@code SignalAnalysis} is <em>flagged</em> when at least one analyzer
 * considers the current signal set severe enough to warrant a DEGRADED
 * transition. Unflagged results are discarded by the
 * {@link CompositeSignalAnalyzer} without side effects.
 *
 * @param flagged          true  → this service should be marked DEGRADED
 * @param severity         assessed severity when flagged; null when not flagged
 * @param triggerMetric    the metric type that crossed a threshold first,
 *                         driving the decision; null when not flagged
 * @param triggerValue     the observed value of the trigger metric
 * @param thresholdValue   the configured threshold that was breached
 * @param remediationHint  short, deterministic first-action string;
 *                         suitable for seeding the incident suggestion
 * @param reasoning        human-readable explanation of why the analyzer flagged
 * @param analyzerName     simple name of the analyzer that produced this result
 */
public record SignalAnalysis(
        boolean flagged,
        IncidentSeverity severity,
        MetricType triggerMetric,
        double triggerValue,
        double thresholdValue,
        String remediationHint,
        String reasoning,
        String analyzerName
) {

    /** Convenience factory — unflagged (pass-through) result. */
    public static SignalAnalysis noOp(String analyzerName) {
        return new SignalAnalysis(false, null, null, 0, 0, null, "No threshold breached.", analyzerName);
    }
}
