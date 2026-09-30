
package com.cloudops.service.advisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration properties for the incident advisor.
 *
 * <p>Bound from the {@code cloudops.advisor} prefix in {@code application.yml}.
 * Switching between rule-based and LLM requires only a config change — no code
 * change and no redeploy of a new artifact:
 *
 * <pre>
 * cloudops:
 *   advisor:
 *     type: llm           # or rule-based
 *     llm:
 *       endpoint: https://api.openai.com/v1/chat/completions
 *       api-key:  ${OPENAI_API_KEY}
 *       model:    gpt-4o
 *       timeout:  30s
 *       max-tokens: 512
 * </pre>
 *
 * @param type  which advisor strategy to activate: {@code llm} or {@code rule-based}
 * @param llm   LLM-specific settings; only consulted when {@code type=llm}
 */
@ConfigurationProperties(prefix = "cloudops.advisor")
public record AdvisorProperties(
        @DefaultValue("rule-based") String type,
        LlmProperties llm
) {

    public static final String TYPE_LLM        = "llm";
    public static final String TYPE_RULE_BASED = "rule-based";

    /**
     * LLM connection and model settings.
     *
     * @param endpoint   full URL of the chat completions endpoint
     * @param apiKey     bearer token; read from an environment variable in production
     * @param model      model identifier (e.g. {@code gpt-4o}, {@code ibm/granite-13b-chat-v2})
     * @param timeout    per-request timeout; defaults to 30s
     * @param maxTokens  maximum tokens in the completion response; defaults to 512
     * @param temperature sampling temperature 0.0–1.0; lower = more deterministic
     */
    public record LlmProperties(
            @DefaultValue("https://api.openai.com/v1/chat/completions") String endpoint,
            String apiKey,
            @DefaultValue("gpt-4o") String model,
            @DefaultValue("30s") Duration timeout,
            @DefaultValue("512") int maxTokens,
            @DefaultValue("0.2") double temperature
    ) {}
}
