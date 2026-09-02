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
 * Calls Groq's chat completions API (https://api.groq.com/openai/v1/chat/completions),
 * which follows the OpenAI request/response shape — a different envelope
 * from Anthropic's, so this is a genuinely separate implementation, not a
 * config toggle on AnthropicAiClient.
 *
 * Selected at startup by AiClientConfig based on reviveai.llm.provider
 * (env var LLM_PROVIDER=groq). Works with any Groq-hosted model id, e.g.
 * "openai/gpt-oss-20b", "llama-3.3-70b-versatile".
 *
 * Not a @Component — same reasoning as AnthropicAiClient.
 */
public class GroqAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqAiClient.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_TOKENS = 500;

    private final ReviveAiProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public GroqAiClient(ReviveAiProperties properties, ObjectMapper objectMapper) {
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
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            String rawApiResponse = webClient.post()
                    .uri(GROQ_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            return extractText(rawApiResponse);
        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new IllegalStateException("Groq API call failed", e);
        }
    }

    private String extractText(String rawApiResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawApiResponse);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                if (message.hasNonNull("content")) {
                    return message.get("content").asText();
                }
            }
            // Groq sometimes returns a 200 with a top-level "error" object rather than a
            // non-2xx status for certain request problems (e.g. an unsupported model id) —
            // surface that message explicitly rather than falling through to the generic case.
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("Groq API error: " + root.path("error").path("message").asText());
            }
            throw new IllegalStateException("Unexpected Groq API response shape: no choices[0].message.content found");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Groq API response envelope", e);
        }
    }
}
