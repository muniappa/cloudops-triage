package com.cloudops.service.advisor;

/**
 * Pluggable interface for AI-powered incident analysis.
 *
 * <p>Every implementation receives a fully assembled {@link AnalysisContext}
 * containing the multi-signal timeline, breach summaries, trend directions,
 * and trigger metadata. The contract is intentionally narrow:
 * one method in, one structured record out.
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@code LlmIncidentAdvisor}     — active when {@code cloudops.advisor.type=llm}</li>
 *   <li>{@code RuleBasedIncidentAdvisor} — active when {@code cloudops.advisor.type=rule-based}
 *       or as automatic fallback when the LLM is unavailable</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations must never return {@code null}.</li>
 *   <li>Implementations must never propagate exceptions to the caller —
 *       they should handle failures internally and return a degraded-but-valid
 *       {@link AdvisorRecommendation} rather than throwing.</li>
 *   <li>The returned recommendation must always carry a non-null
 *       {@link com.cloudops.domain.enums.IncidentSeverity}.</li>
 * </ul>
 */
public interface IncidentAdvisor {

    /**
     * Analyse an incident context and return a structured recommendation.
     *
     * @param context fully assembled multi-signal snapshot; never null
     * @return        a populated, non-null {@link AdvisorRecommendation}
     */
    AdvisorRecommendation analyse(AnalysisContext context);

    /**
     * Short identifier used in logs and the recommendation's {@code advisorType} field.
     */
    String advisorType();
}
