package com.cloudops.service.advisor;

import com.cloudops.domain.enums.IncidentSeverity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM-backed {@link IncidentAdvisor} using the OpenAI chat-completions API.
 *
 * <h3>Prompt design</h3>
 * The system prompt instructs the model to act as a senior SRE and return
 * <em>only</em> a JSON object. The user message is a structured narrative built
 * from the full {@link AnalysisContext}: service name, health status, how long
 * it has been degraded, the trigger reason, the signal timeline with breach
 * annotations, and per-metric breach run lengths and trends.
 *
 * <h3>Response validation</h3>
 * The raw JSON is deserialised into {@link LlmResponse}, then mapped to
 * {@link AdvisorRecommendation}. Validation checks:
 * <ul>
 *   <li>severity is a valid {@link IncidentSeverity} enum value</li>
 *   <li>recommendedAction is non-blank and at least 20 characters</li>
 *   <li>confidence is between 0.0 and 1.0</li>
 * </ul>
 * Any failure — network error, timeout, malformed JSON, failed validation —
 * propagates as an {@link AdvisorException} so the caller (
 * {@link FallbackIncidentAdvisor}) can catch and switch to rule-based.
 *
 * <h3>Never silent-drop</h3>
 * This class never swallows failures. It throws {@link AdvisorException}
 * on every error path so the agentic loop's fallback wrapper always has a
 * signal to act on.
 */
public class LlmIncidentAdvisor implements IncidentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LlmIncidentAdvisor.class);

    static final String ADVISOR_TYPE = "llm";

    private static final String SYSTEM_PROMPT = """
            You are a senior SRE performing incident triage for a microservice platform.
            You will be given structured context about a degraded service including its recent
            signal timeline, breach summaries, and the reason analysis was triggered.

            Respond ONLY with a valid JSON object — no markdown, no prose, no code fences.
            The JSON must have exactly these fields:
            {
              "severity":            "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
              "rootCause":           "<concise hypothesis, 1-2 sentences>",
              "recommendedAction":   "<specific first remediation step, 1-2 sentences>",
              "confidence":          <float 0.0–1.0>,
              "reasoning":           "<chain-of-thought, up to 4 sentences>"
            }

            Rules:
            - severity must be exactly one of: LOW, MEDIUM, HIGH, CRITICAL
            - recommendedAction must be actionable and specific to the service and signals given
            - confidence reflects how certain you are given the available signals (0.9+ only for clear-cut cases)
            - If signals are ambiguous, say so in reasoning and lower confidence accordingly
            """;

    private final WebClient webClient;
    private final AdvisorProperties.LlmProperties config;
    private final ObjectMapper objectMapper;

    public LlmIncidentAdvisor(WebClient webClient,
                               AdvisorProperties.LlmProperties config,
                               ObjectMapper objectMapper) {
        this.webClient    = webClient;
        this.config       = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public String advisorType() { return ADVISOR_TYPE; }

    @Override
    public AdvisorRecommendation analyse(AnalysisContext ctx) {
        String userMessage = buildUserMessage(ctx);
        log.debug("[LlmAdvisor] Sending analysis request for '{}' (trigger={})",
                  ctx.serviceName(), ctx.triggerReason());

        String rawJson = callLlm(userMessage, ctx.serviceName());
        LlmResponse parsed = parseAndValidate(rawJson, ctx.serviceName());

        String title   = "[%s] %s — LLM triage (%s)"
                .formatted(parsed.severity, ctx.serviceName(), ctx.triggerReason().name());
        String summary = "LLM analysis (confidence %.0f%%). %s"
                .formatted(parsed.confidence * 100, parsed.reasoning);

        return new AdvisorRecommendation(
                parsed.severity,
                parsed.rootCause,
                parsed.recommendedAction,
                parsed.confidence,
                parsed.reasoning,
                title,
                summary,
                ADVISOR_TYPE
        );
    }

    // ── Prompt builder ─────────────────────────────────────────────────────

    private String buildUserMessage(AnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("SERVICE CONTEXT\n");
        sb.append("  name:        ").append(ctx.serviceName()).append("\n");
        sb.append("  team:        ").append(ctx.teamOwner()).append("\n");
        sb.append("  status:      ").append(ctx.currentStatus()).append("\n");
        sb.append("  trigger:     ").append(ctx.triggerReason()).append("\n");

        if (ctx.degradedSince() != null) {
            long seconds = Duration.between(ctx.degradedSince(), ctx.analysisRequestedAt()).toSeconds();
            sb.append("  degraded for: ").append(formatDuration(seconds)).append("\n");
        }

        sb.append("\nSIGNAL TIMELINE (newest → oldest)\n");
        ctx.signalTimeline().forEach(s ->
                sb.append(String.format("  %-12s  %8.2f  %s  %s%n",
                        s.metricType(),
                        s.value(),
                        s.thresholdBreached() ? "[BREACHED]" : "         ",
                        s.recordedAt()))
        );

        sb.append("\nBREACH SUMMARY\n");
        if (ctx.breachSummaries().isEmpty()) {
            sb.append("  No breaches detected in window.\n");
        } else {
            ctx.breachSummaries().forEach(b ->
                    sb.append(String.format("  %-12s  consecutive=%d  peak=%.2f  latest=%.2f  "
                                    + "threshold=%.2f  trend=%s%n",
                            b.metricType(), b.consecutiveBreaches(),
                            b.peakValue(), b.latestValue(),
                            b.threshold(), b.trend()))
            );
        }

        sb.append("\nAnalysis requested at: ").append(ctx.analysisRequestedAt());
        return sb.toString();
    }

    // ── HTTP call ──────────────────────────────────────────────────────────

    private String callLlm(String userMessage, String serviceName) {
        Map<String, Object> requestBody = Map.of(
                "model", config.model(),
                "max_tokens", config.maxTokens(),
                "temperature", config.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user",   "content", userMessage)
                )
        );

        try {
            Map<?, ?> response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(config.timeout())
                    .block();

            if (response == null) {
                throw new AdvisorException("LLM returned null response for service: " + serviceName);
            }

            // Extract content from choices[0].message.content
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> choices = (List<Map<?, ?>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AdvisorException("LLM response missing 'choices' for service: " + serviceName);
            }
            @SuppressWarnings("unchecked")
            Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
            if (message == null) {
                throw new AdvisorException("LLM choice missing 'message' for service: " + serviceName);
            }
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new AdvisorException("LLM message content is blank for service: " + serviceName);
            }
            return content.strip();

        } catch (AdvisorException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new AdvisorException(
                    "LLM HTTP error %d for service '%s': %s"
                    .formatted(e.getStatusCode().value(), serviceName, e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new AdvisorException(
                    "LLM call failed for service '%s': %s".formatted(serviceName, e.getMessage()), e);
        }
    }

    // ── Response parsing and validation ───────────────────────────────────

    private LlmResponse parseAndValidate(String rawJson, String serviceName) {
        LlmResponse parsed;
        try {
            parsed = objectMapper.readValue(rawJson, LlmResponse.class);
        } catch (Exception e) {
            throw new AdvisorException(
                    "LLM response is not valid JSON for service '%s'. Raw: %s"
                    .formatted(serviceName, truncate(rawJson, 200)), e);
        }

        // Severity enum validation
        if (parsed.severity == null) {
            throw new AdvisorException(
                    "LLM returned null/invalid severity for service '%s'. Raw: %s"
                    .formatted(serviceName, truncate(rawJson, 200)));
        }

        // Action plausibility: must be non-blank and have enough content to be actionable
        if (parsed.recommendedAction == null || parsed.recommendedAction.trim().length() < 20) {
            throw new AdvisorException(
                    "LLM recommended action is implausible (too short) for service '%s'. Got: '%s'"
                    .formatted(serviceName, parsed.recommendedAction));
        }

        // Confidence range
        if (parsed.confidence < 0.0 || parsed.confidence > 1.0) {
            throw new AdvisorException(
                    "LLM confidence out of range [0,1] for service '%s': %s"
                    .formatted(serviceName, parsed.confidence));
        }

        log.info("[LlmAdvisor] Valid response for '{}': severity={}, confidence={}",
                 serviceName, parsed.severity, String.format("%.2f", parsed.confidence));
        return parsed;
    }

    // ── Internal DTO for LLM JSON deserialization ──────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LlmResponse {
        public IncidentSeverity severity;
        public String rootCause;
        public String recommendedAction;
        public double confidence;
        public String reasoning;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String formatDuration(long totalSeconds) {
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        return mins > 0 ? "%dm %ds".formatted(mins, secs) : "%ds".formatted(secs);
    }

    private static String truncate(String s, int max) {
        return s == null ? "null" : s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
