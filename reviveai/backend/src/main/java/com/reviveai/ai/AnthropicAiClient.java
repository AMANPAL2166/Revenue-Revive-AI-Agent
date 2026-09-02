package com.reviveai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.ReviveAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the Anthropic Messages API (https://api.anthropic.com/v1/messages).
 * One of two AiClient implementations — see GroqAiClient for the other.
 * Selected at startup by AiClientConfig based on reviveai.llm.provider
 * (env var LLM_PROVIDER). Everything else in the codebase depends only on
 * the AiClient interface, never on this class directly.
 *
 * Not a @Component: instantiated explicitly by AiClientConfig, so there is
 * never any ambiguity about which AiClient bean is active — same pattern
 * as RazorpayGatewayClientImpl / SimulatedRazorpayGatewayClient.
 *
 * Uses WebClient purely as a blocking HTTP client here (see pom.xml note:
 * WebFlux is pulled in only for WebClient — the application is not
 * reactive end-to-end).
 */
public class AnthropicAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiClient.class);
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 500;

    private final ReviveAiProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public AnthropicAiClient(ReviveAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is not configured");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getLlm().getModel(),
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        try {
            String rawApiResponse = webClient.post()
                    .uri(ANTHROPIC_API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            return extractText(rawApiResponse);
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new IllegalStateException("Anthropic API call failed", e);
        }
    }

    private String extractText(String rawApiResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawApiResponse);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                JsonNode first = content.get(0);
                if (first.hasNonNull("text")) {
                    return first.get("text").asText();
                }
            }
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("Anthropic API error: " + root.path("error").path("message").asText());
            }
            throw new IllegalStateException("Unexpected Anthropic API response shape: no content[0].text found");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Anthropic API response envelope", e);
        }
    }
}
