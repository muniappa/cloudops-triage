package com.cloudops.service.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the active {@link IncidentAdvisor} bean based on
 * {@code cloudops.advisor.type} in {@code application.yml}.
 *
 * <p>The advisor registered as the primary Spring bean is always wrapped
 * by {@link FallbackIncidentAdvisor}, which catches failures and falls
 * back to the rule-based implementation automatically.
 *
 * <h3>Switching at runtime</h3>
 * Change {@code cloudops.advisor.type} and restart. No code change needed.
 * <pre>
 *   cloudops.advisor.type: llm        → LlmIncidentAdvisor (with rule-based fallback)
 *   cloudops.advisor.type: rule-based → RuleBasedIncidentAdvisor (no fallback needed)
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(AdvisorProperties.class)
public class AdvisorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdvisorConfiguration.class);

    private final AdvisorProperties props;

    public AdvisorConfiguration(AdvisorProperties props) {
        this.props = props;
    }

    /**
     * The single {@link IncidentAdvisor} bean used by the rest of the system.
     * Always a {@link FallbackIncidentAdvisor} wrapper — the primary advisor
     * varies by config.
     */
    @Bean
    public IncidentAdvisor incidentAdvisor(ObjectMapper objectMapper) {
        RuleBasedIncidentAdvisor ruleBased = new RuleBasedIncidentAdvisor();

        IncidentAdvisor primary;
        if (AdvisorProperties.TYPE_LLM.equalsIgnoreCase(props.type())) {
            log.info("[AdvisorConfig] Activating LLM advisor (model={}, endpoint={})",
                     props.llm().model(), props.llm().endpoint());
            WebClient webClient = WebClient.builder()
                    .baseUrl(props.llm().endpoint())
                    .defaultHeader("Authorization", "Bearer " + props.llm().apiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            primary = new LlmIncidentAdvisor(webClient, props.llm(), objectMapper);
        } else {
            log.info("[AdvisorConfig] Activating rule-based advisor");
            primary = ruleBased;
        }

        return new FallbackIncidentAdvisor(primary, ruleBased);
    }
}
