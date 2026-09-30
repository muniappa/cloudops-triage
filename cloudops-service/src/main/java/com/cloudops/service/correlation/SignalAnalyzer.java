package com.cloudops.service.correlation;

import com.cloudops.domain.model.HealthSignal;
import com.cloudops.domain.model.MonitoredService;

import java.util.List;

/**
 * Pluggable contract for a signal correlation analyzer.
 *
 * <p>Each implementation represents one analysis strategy. The
 * {@link CompositeSignalAnalyzer} collects all registered
 * {@code SignalAnalyzer} beans and merges their results, so new
 * strategies (ML-based, deployment-aware, dependency-graph-aware, etc.)
 * can be added without modifying existing code.
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li>Implementations must be stateless and thread-safe.</li>
 *   <li>Never throw — return {@link SignalAnalysis#noOp(String)} for any
 *       case that does not warrant flagging.</li>
 *   <li>The {@code signals} list is ordered newest-first and may be empty.</li>
 * </ul>
 */
public interface SignalAnalyzer {

    /**
     * Evaluate the current signal buffer for the given service.
     *
     * @param service the monitored service whose status may change
     * @param signals rolling buffer of recent signals, ordered newest-first;
     *                may be empty if no signals have been ingested yet
     * @return a {@link SignalAnalysis}; never null
     */
    SignalAnalysis analyze(MonitoredService service, List<HealthSignal> signals);

    /**
     * Short identifier used in logs and the {@link SignalAnalysis#analyzerName()} field.
     */
    String name();
}
