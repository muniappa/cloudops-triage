package com.cloudops.service.correlation;

import com.cloudops.domain.enums.IncidentSeverity;
import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates all registered {@link SignalAnalyzer} beans and merges their
 * individual {@link SignalAnalysis} results into a single authoritative verdict.
 *
 * <h3>Merge strategy</h3>
 * <ol>
 *   <li>Run every analyzer in registration order (Spring injection order).</li>
 *   <li>Collect only flagged results.</li>
 *   <li>If none flagged → return a no-op unflagged result.</li>
 *   <li>If one or more flagged → select the result with the <em>highest</em>
 *       severity as the primary; append all reasoning strings so on-call
 *       sees the full picture in one place.</li>
 * </ol>
 *
 * <p>Adding a new analyzer strategy is a single step: implement
 * {@link SignalAnalyzer} and annotate it with {@code @Component}. No other
 * code changes needed.
 */
@Component
public class CompositeSignalAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CompositeSignalAnalyzer.class);

    private final List<SignalAnalyzer> analyzers;

    public CompositeSignalAnalyzer(List<SignalAnalyzer> analyzers) {
        this.analyzers = List.copyOf(analyzers);
        log.info("[CorrelationEngine] Registered {} analyzer(s): {}",
                 analyzers.size(),
                 analyzers.stream().map(SignalAnalyzer::name).toList());
    }

    /**
     * Run all analyzers and merge results.
     *
     * @param service the service whose signals are being evaluated
     * @param signals rolling signal buffer, newest-first; may be empty
     * @return a merged {@link SignalAnalysis}; never null
     */
    public SignalAnalysis evaluate(MonitoredService service, List<HealthSignal> signals) {
        List<SignalAnalysis> flagged = analyzers.stream()
                .map(a -> {
                    try {
                        return a.analyze(service, signals);
                    } catch (Exception ex) {
                        log.warn("[CorrelationEngine] Analyzer '{}' threw an exception for service '{}': {}",
                                 a.name(), service.getName(), ex.getMessage(), ex);
                        return SignalAnalysis.noOp(a.name());
                    }
                })
                .filter(SignalAnalysis::flagged)
                .toList();

        if (flagged.isEmpty()) {
            return SignalAnalysis.noOp("CompositeSignalAnalyzer");
        }

        // Primary = highest severity; tie-break by ordinal (CRITICAL > HIGH > MEDIUM > LOW)
        SignalAnalysis primary = flagged.stream()
                .max(Comparator.comparingInt(a -> a.severity().ordinal()))
                .orElseThrow();

        // Merge reasoning from all flagged analyzers into one string
        String mergedReasoning = flagged.size() == 1
                ? primary.reasoning()
                : mergeReasoning(flagged);

        return new SignalAnalysis(
                true,
                primary.severity(),
                primary.triggerMetric(),
                primary.triggerValue(),
                primary.thresholdValue(),
                primary.remediationHint(),
                mergedReasoning,
                "CompositeSignalAnalyzer[" + primary.analyzerName() + "]"
        );
    }

    /**
     * Returns the count of registered analyzers.
     * Useful for health-check and diagnostic endpoints.
     */
    public int analyzerCount() {
        return analyzers.size();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String mergeReasoning(List<SignalAnalysis> flagged) {
        StringBuilder sb = new StringBuilder();
        for (SignalAnalysis a : flagged) {
            sb.append("[").append(a.analyzerName()).append("] ")
              .append(a.reasoning())
              .append(" ");
        }
        return sb.toString().strip();
    }
}
