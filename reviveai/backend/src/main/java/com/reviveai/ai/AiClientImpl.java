//package com.reviveai.ai;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.reviveai.config.ReviveAiProperties;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClientResponseException;
//
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//
///**
// * Groq implementation of the AiClient abstraction.
// *
// * AgentService does not know which LLM provider is being used.
// * This class owns the provider-specific HTTP contract and API key.
// */
//@Component
//public class AiClientImpl implements AiClient {
//
//    private static final Logger log = LoggerFactory.getLogger(AiClientImpl.class);
//
//    private static final String GROQ_API_URL =
//            "https://api.groq.com/openai/v1/chat/completions";
//
//    private static final int MAX_COMPLETION_TOKENS = 500;
//
//    private final ReviveAiProperties properties;
//    private final ObjectMapper objectMapper;
//    private final WebClient webClient;
//
//    public AiClientImpl(
//            ReviveAiProperties properties,
//            ObjectMapper objectMapper
//    ) {
//        this.properties = properties;
//        this.objectMapper = objectMapper;
//        this.webClient = WebClient.builder().build();
//    }
//
//    @Override
//    public String complete(String systemPrompt, String userPrompt) {
//
//        String apiKey = properties.getLlm().getApiKey();
//
//        if (apiKey == null || apiKey.isBlank()) {
//            throw new IllegalStateException("LLM_API_KEY is not configured");
//        }
//
//        String model = properties.getLlm().getModel();
//
//        if (model == null || model.isBlank()) {
//            throw new IllegalStateException("LLM_MODEL is not configured");
//        }
//
//        Map<String, Object> requestBody = Map.of(
//                "model", model,
//                "messages", List.of(
//                        Map.of(
//                                "role", "system",
//                                "content", systemPrompt
//                        ),
//                        Map.of(
//                                "role", "user",
//                                "content", userPrompt
//                        )
//                ),
//                "max_completion_tokens", MAX_COMPLETION_TOKENS,
//                "temperature", 0.1,
//                "response_format", Map.of(
//                        "type", "json_object"
//                )
//        );
//
//        try {
//
//            log.info("Calling LLM provider: Groq, model={}", model);
//
//            String rawApiResponse = webClient.post()
//                    .uri(GROQ_API_URL)
//                    .header(
//                            "Authorization",
//                            "Bearer " + apiKey
//                    )
//                    .header(
//                            "Content-Type",
//                            "application/json"
//                    )
//                    .bodyValue(requestBody)
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .timeout(Duration.ofSeconds(20))
//                    .block();
//
//            return extractText(rawApiResponse);
//
//        } catch (WebClientResponseException e) {
//
//            log.error(
//                    "Groq API error: status={}, body={}",
//                    e.getStatusCode(),
//                    e.getResponseBodyAsString()
//            );
//
//            throw new IllegalStateException(
//                    "LLM API call failed",
//                    e
//            );
//
//        } catch (Exception e) {
//
//            log.error(
//                    "Groq LLM API call failed. Exception type={}, message={}",
//                    e.getClass().getName(),
//                    e.getMessage(),
//                    e,
//                    e
//            );
//
//            throw new IllegalStateException(
//                    "LLM API call failed",
//                    e
//            );
//        }
//    }
//
//    private String extractText(String rawApiResponse) {
//
//        if (rawApiResponse == null || rawApiResponse.isBlank()) {
//            throw new IllegalStateException(
//                    "Groq returned an empty response"
//            );
//        }
//
//        try {
//
//            JsonNode root = objectMapper.readTree(rawApiResponse);
//
//            JsonNode choices = root.path("choices");
//
//            if (!choices.isArray() || choices.isEmpty()) {
//                throw new IllegalStateException(
//                        "Unexpected Groq response: missing choices"
//                );
//            }
//
//            JsonNode message = choices
//                    .get(0)
//                    .path("message");
//
//            JsonNode content = message.path("content");
//
//            if (content.isTextual()) {
//                return content.asText();
//            }
//
//            throw new IllegalStateException(
//                    "Unexpected Groq response: missing choices[0].message.content"
//            );
//
//        } catch (IllegalStateException e) {
//
//            throw e;
//
//        } catch (Exception e) {
//
//            throw new IllegalStateException(
//                    "Failed to parse Groq API response",
//                    e
//            );
//        }
//    }
//}