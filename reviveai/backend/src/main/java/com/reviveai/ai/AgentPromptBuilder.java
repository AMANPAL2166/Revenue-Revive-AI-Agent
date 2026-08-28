package com.reviveai.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentPromptBuilder {

    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are the ReviveAI Recovery Agent, an assistant that recommends a single
            revenue-recovery action for a merchant based on data provided to you.

            Respond with ONLY a single JSON object — no prose, no markdown code fences,
            no explanation outside the JSON. The JSON must have exactly this shape:

            {
              "action": "RETRY_PAYMENT" | "SEND_REMINDER" | "CREATE_PAYMENT_LINK" | "OFFER_DISCOUNT" | "ESCALATE_TO_HUMAN" | "NO_ACTION",
              "confidence": <number between 0 and 1>,
              "reason": "<one or two concise sentences explaining the recommendation, written for a merchant to read>",
              "suggestedDelayHours": <integer, optional, only relevant for RETRY_PAYMENT>,
              "discountPercent": <integer 0-100, required only when action is OFFER_DISCOUNT>
            }

            Rules:
            - Choose exactly one action from the list above. Do not invent new actions.
            - Base your recommendation only on the data provided in the user message.
            - Do not calculate or restate financial metrics — they are provided to you
              as authoritative, already-computed values. Never recompute or override them.
            - "reason" must be a short, final explanation only — the conclusion itself,
              not your reasoning process, not alternatives you considered.
            - If you are not confident an automated action is appropriate, recommend
              ESCALATE_TO_HUMAN rather than guessing.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(AgentContext context) {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode customer = root.putObject("customer");
        customer.put("id", context.getCustomerId() != null ? context.getCustomerId().toString() : null);
        customer.put("lifetimeValue", context.getCustomerLifetimeValue());
        customer.put("successfulPayments", context.getSuccessfulPayments());
        customer.put("failedPayments", context.getFailedPayments());
        customer.put("paymentSuccessRate", context.getPaymentSuccessRate());

        ObjectNode payment = root.putObject("payment");
        payment.put("amount", context.getAmount());
        payment.put("failureReason", context.getFailureReason());
        payment.put("retryCount", context.getRetryCount());

        ObjectNode metrics = root.putObject("calculatedMetrics");
        metrics.put("revenueAtRisk", context.getRevenueAtRisk());
        metrics.put("recoveryProbability", context.getRecoveryProbability());
        metrics.put("expectedRecoveryValue", context.getExpectedRecoveryValue());
        metrics.put("priority", context.getPriority() != null ? context.getPriority().name() : null);

        ObjectNode history = root.putObject("history");
        history.put("previousSuccessfulRecovery", context.isPreviousSuccessfulRecovery());

        return "Here is the revenue-risk case data. Recommend one action as instructed.\n\n"
                + root.toPrettyString();
    }
}
