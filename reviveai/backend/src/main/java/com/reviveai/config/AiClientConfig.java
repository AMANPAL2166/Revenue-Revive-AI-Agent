package com.reviveai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.ai.AiClient;
import com.reviveai.ai.AnthropicAiClient;
import com.reviveai.ai.GroqAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses which AiClient implementation is active at startup, based on
 * reviveai.llm.provider (env var LLM_PROVIDER, default "anthropic"). This
 * is the ONLY place that decision is made — AgentService depends solely
 * on the AiClient interface and never knows, or needs to know, which
 * provider is behind it. Mirrors GatewayConfig's real/simulated Razorpay
 * adapter selection exactly.
 */
@Configuration
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    @Bean
    public AiClient aiClient(ReviveAiProperties properties, ObjectMapper objectMapper) {
        String provider = properties.getLlm().getProvider();

        if ("groq".equalsIgnoreCase(provider)) {
            log.info("AiClient: using Groq (model={})", properties.getLlm().getModel());
            return new GroqAiClient(properties, objectMapper);
        }

        log.info("AiClient: using Anthropic (model={})", properties.getLlm().getModel());
        return new AnthropicAiClient(properties, objectMapper);
    }
}
