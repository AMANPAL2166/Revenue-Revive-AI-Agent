package com.reviveai.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.enums.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AiClient is mocked throughout — these tests exercise AgentService's
 * validation and fallback logic in isolation, without ever making a real
 * network call to an LLM provider.
 */
class AgentServiceTest {

    @Mock
    private AiClient aiClient;

    private AgentService agentService;

    private static final AgentContext SAMPLE_CONTEXT = AgentContext.builder()
            .amount(java.math.BigDecimal.valueOf(4999))
            .failureReason("Insufficient funds")
            .retryCount(0)
            .build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper();
        agentService = new AgentService(aiClient, new AgentPromptBuilder(objectMapper), objectMapper);
    }

    @Test
    void validResponse_parsesIntoMatchingAgentDecision() {
        String response = """
                {
                  "action": "RETRY_PAYMENT",
                  "confidence": 0.91,
                  "reason": "The customer has a strong historical payment record and the failure appears temporary.",
                  "suggestedDelayHours": 24,
                  "discountPercent": 0
                }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isTrue();
        assertThat(decision.getAction()).isEqualTo(ActionType.RETRY_PAYMENT);
        assertThat(decision.getConfidence()).isEqualTo(0.91);
        assertThat(decision.getSuggestedDelayHours()).isEqualTo(24);
        assertThat(decision.getDiscountPercent()).isEqualTo(0);
    }

    @Test
    void markdownFencedJson_isStrippedAndParsedSuccessfully() {
        String response = """
                ```json
                {
                  "action": "SEND_REMINDER",
                  "confidence": 0.7,
                  "reason": "A gentle nudge is likely to work."
                }
                ```
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isTrue();
        assertThat(decision.getAction()).isEqualTo(ActionType.SEND_REMINDER);
    }

    @Test
    void discountPercent_isNormalizedToZeroForNonDiscountActions() {
        // Even if the model mistakenly includes a discountPercent for an
        // unrelated action, it must never leak through.
        String response = """
                {
                  "action": "SEND_REMINDER",
                  "confidence": 0.6,
                  "reason": "Remind them.",
                  "discountPercent": 50
                }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isTrue();
        assertThat(decision.getDiscountPercent()).isEqualTo(0);
    }

    @Test
    void malformedJson_fallsBackToEscalateToHuman() {
        when(aiClient.complete(any(), any())).thenReturn("this is not json");

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
        assertThat(decision.getAction()).isEqualTo(ActionType.ESCALATE_TO_HUMAN);
    }

    @Test
    void unrecognizedAction_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "ISSUE_REFUND", "confidence": 0.5, "reason": "test" }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
        assertThat(decision.getAction()).isEqualTo(ActionType.ESCALATE_TO_HUMAN);
    }

    @Test
    void confidenceOutOfRange_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "NO_ACTION", "confidence": 1.5, "reason": "test" }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
    }

    @Test
    void missingReason_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "NO_ACTION", "confidence": 0.5 }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
    }

    @Test
    void offerDiscountMissingDiscountPercent_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "OFFER_DISCOUNT", "confidence": 0.7, "reason": "discount time" }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
    }

    @Test
    void offerDiscountOutOfRange_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "OFFER_DISCOUNT", "confidence": 0.7, "reason": "big discount", "discountPercent": 150 }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
    }

    @Test
    void validOfferDiscount_parsesDiscountPercentCorrectly() {
        String response = """
                { "action": "OFFER_DISCOUNT", "confidence": 0.8, "reason": "within policy", "discountPercent": 10 }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isTrue();
        assertThat(decision.getDiscountPercent()).isEqualTo(10);
    }

    @Test
    void aiClientThrows_fallsBackToEscalateToHumanWithoutPropagating() {
        when(aiClient.complete(any(), any())).thenThrow(new IllegalStateException("network down"));

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
        assertThat(decision.getAction()).isEqualTo(ActionType.ESCALATE_TO_HUMAN);
    }

    @Test
    void negativeSuggestedDelayHours_fallsBackToEscalateToHuman() {
        String response = """
                { "action": "RETRY_PAYMENT", "confidence": 0.6, "reason": "retry", "suggestedDelayHours": -5 }
                """;
        when(aiClient.complete(any(), any())).thenReturn(response);

        AgentDecision decision = agentService.recommend(SAMPLE_CONTEXT);

        assertThat(decision.isValidAiResponse()).isFalse();
    }
}
