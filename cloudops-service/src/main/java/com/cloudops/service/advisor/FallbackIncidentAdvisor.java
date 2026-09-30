package com.cloudops.service.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that wraps the primary {@link IncidentAdvisor} with a
 * rule-based fallback.
 *
 * <p>This is the bean that the rest of the system depends on. It delegates
 * to the primary advisor (LLM or rule-based as configured) and, if that
 * throws an {@link AdvisorException}, immediately retries with the
 * {@link RuleBasedIncidentAdvisor}.
 *
 * <h3>Why this matters</h3>
 * The ironic worst-case: the LLM is down <em>during</em> a production outage.
 * This class guarantees that triage never silently fails — the async path
 * always produces a recommendation, even if it's rule-based.
 *
 * <h3>Never silent-drop</h3>
 * If both the primary <em>and</em> the fallback throw, this class logs
 * {@code ERROR} and returns a MEDIUM-severity safe-default recommendation
 * rather than propagating — ensuring the incident draft is always created.
 */
public class FallbackIncidentAdvisor implements IncidentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(FallbackIncidentAdvisor.class);

    private final IncidentAdvisor primary;
    private final RuleBasedIncidentAdvisor fallback;

    public FallbackIncidentAdvisor(IncidentAdvisor primary, RuleBasedIncidentAdvisor fallback) {
        this.primary  = primary;
        this.fallback = fallback;
    }

    @Override
    public String advisorType() {
        return "fallback[" + primary.advisorType() + "]";
    }

    @Override
    public AdvisorRecommendation analyse(AnalysisContext ctx) {
        // ── Attempt primary advisor ────────────────────────────────────────
        try {
            AdvisorRecommendation rec = primary.analyse(ctx);
            if (rec == null || !rec.isValid()) {
                log.warn("[FallbackAdvisor] Primary '{}' returned invalid recommendation "
                         + "for '{}' — switching to rule-based",
                         primary.advisorType(), ctx.serviceName());
                return fallback.analyse(ctx);
            }
            return rec;
        } catch (AdvisorException ex) {
            log.warn("[FallbackAdvisor] Primary '{}' failed for '{}': {} — switching to rule-based",
                     primary.advisorType(), ctx.serviceName(), ex.getMessage());
        } catch (Exception ex) {
            log.error("[FallbackAdvisor] Primary '{}' threw unexpected exception for '{}': {}",
                      primary.advisorType(), ctx.serviceName(), ex.getMessage(), ex);
        }

        // ── Attempt rule-based fallback ────────────────────────────────────
        try {
            AdvisorRecommendation fallbackRec = fallback.analyse(ctx);
            log.info("[FallbackAdvisor] Rule-based fallback succeeded for '{}'", ctx.serviceName());
            return fallbackRec;
        } catch (Exception ex) {
            log.error("[FallbackAdvisor] Rule-based fallback ALSO failed for '{}': {} — "
                      + "returning safe-default recommendation",
                      ctx.serviceName(), ex.getMessage(), ex);
        }

        // ── Last resort: safe-default (never null, never silent-drop) ────
        return safeDefault(ctx);
    }

    private AdvisorRecommendation safeDefault(AnalysisContext ctx) {
        String msg = "Both primary and fallback advisors failed. Manual triage required.";
        return new AdvisorRecommendation(
                com.cloudops.domain.enums.IncidentSeverity.MEDIUM,
                "Advisor unavailable — root cause unknown.",
                "Immediately investigate '%s' manually; all automated analysis failed."
                        .formatted(ctx.serviceName()),
                0.0,
                msg,
                "[MEDIUM] %s — manual triage required".formatted(ctx.serviceName()),
                msg,
                "safe-default"
        );
    }
}
