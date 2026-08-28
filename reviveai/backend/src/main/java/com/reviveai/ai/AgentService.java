package com.reviveai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.enums.ActionType;
import com.reviveai.exception.InvalidAiResponseException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends decision context to the LLM and returns a validated AgentDecision.
 *
 * Central rule from the spec: "Never trust arbitrary LLM output." Every
 * field in the raw response is checked before use; if anything is missing,
 * malformed, or out of range, the AI decision is marked invalid, the error
 * is logged, and a safe ESCALATE_TO_HUMAN fallback is returned instead.
 * This method never throws to its caller.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AiClient aiClient;
    private final AgentPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    private static AgentDecision fallbackDecision() {
        return AgentDecision.builder()
                .action(ActionType.ESCALATE_TO_HUMAN)
                .confidence(0.0)
                .reason("The AI recommendation could not be validated, so this case has been escalated for manual review.")
                .discountPercent(0)
                .validAiResponse(false)
                .build();
    }

    public AgentDecision recommend(AgentContext context) {
        String rawResponse;
        try {
            rawResponse = aiClient.complete(promptBuilder.systemPrompt(), promptBuilder.buildUserPrompt(context));
        } catch (Exception e) {
            log.error("AI call failed, falling back to ESCALATE_TO_HUMAN: {}", e.getMessage());
            return fallbackDecision();
        }

        try {
            AgentDecision decision = parseAndValidate(rawResponse);
            log.info("AI recommendation: {} (confidence={})", decision.getAction(), decision.getConfidence());
            return decision;
        } catch (InvalidAiResponseException e) {
            log.error("AI response failed validation, falling back to ESCALATE_TO_HUMAN: {}", e.getMessage());
            return fallbackDecision();
        }
    }

    private AgentDecision parseAndValidate(String rawResponse) {
        JsonNode json;
        try {
            json = objectMapper.readTree(stripMarkdownFences(rawResponse));
        } catch (Exception e) {
            throw new InvalidAiResponseException("AI response was not valid JSON: " + e.getMessage());
        }

        if (!json.hasNonNull("action")) {
            throw new InvalidAiResponseException("AI response missing 'action' field");
        }
        ActionType action;
        try {
            action = ActionType.valueOf(json.get("action").asText().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAiResponseException("AI response had an unrecognized action: " + json.get("action").asText());
        }

        if (!json.hasNonNull("confidence")) {
            throw new InvalidAiResponseException("AI response missing 'confidence' field");
        }
        double confidence = json.get("confidence").asDouble();
        if (confidence < 0.0 || confidence > 1.0) {
            throw new InvalidAiResponseException("AI response confidence out of range [0,1]: " + confidence);
        }

        String reason = json.hasNonNull("reason") ? json.get("reason").asText().trim() : "";
        if (reason.isBlank()) {
            throw new InvalidAiResponseException("AI response missing a non-blank 'reason'");
        }
        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        Integer suggestedDelayHours = null;
        if (json.hasNonNull("suggestedDelayHours")) {
            int hours = json.get("suggestedDelayHours").asInt();
            if (hours < 0) {
                throw new InvalidAiResponseException("suggestedDelayHours cannot be negative: " + hours);
            }
            suggestedDelayHours = hours;
        }

        // discountPercent is only meaningful for OFFER_DISCOUNT — normalized
        // to 0 for every other action regardless of what the model sent, so
        // a stray value can never leak into an unrelated action downstream.
        int discountPercent = 0;
        if (action == ActionType.OFFER_DISCOUNT) {
            if (!json.hasNonNull("discountPercent")) {
                throw new InvalidAiResponseException("OFFER_DISCOUNT response missing required 'discountPercent'");
            }
            discountPercent = json.get("discountPercent").asInt();
            if (discountPercent < 0 || discountPercent > 100) {
                throw new InvalidAiResponseException("discountPercent out of range [0,100]: " + discountPercent);
            }
        }

        return AgentDecision.builder()
                .action(action)
                .confidence(confidence)
                .reason(reason)
                .suggestedDelayHours(suggestedDelayHours)
                .discountPercent(discountPercent)
                .validAiResponse(true)
                .build();
    }

    /** Some models wrap JSON in ```json fences despite instructions not to; strip defensively before parsing. */
    private String stripMarkdownFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
