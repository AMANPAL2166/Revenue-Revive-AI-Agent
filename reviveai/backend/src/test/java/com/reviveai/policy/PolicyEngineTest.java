package com.reviveai.policy;

import com.reviveai.config.ReviveAiProperties;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.PolicyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        // Defaults mirror the spec's example merchant policy exactly:
        // MAX_DISCOUNT_PERCENT=10, MAX_AUTOMATIC_REFUND_AMOUNT=2000,
        // MAX_PAYMENT_RETRIES=3, HIGH_VALUE_PAYMENT_THRESHOLD=50000,
        // both approval flags = true.
        MerchantPolicy merchantPolicy = new MerchantPolicy(new ReviveAiProperties());
        policyEngine = new PolicyEngine(merchantPolicy);
    }

    // ---- Retry limit ----

    @Test
    void retryPayment_underLimit_isAllowed() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.RETRY_PAYMENT)
                .paymentAmount(BigDecimal.valueOf(4999))
                .retryCount(1)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.ALLOWED);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void retryPayment_atMaxRetries_isBlocked() {
        // Spec: "If retryCount >= MAX_PAYMENT_RETRIES: BLOCK". Default max is 3.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.RETRY_PAYMENT)
                .paymentAmount(BigDecimal.valueOf(4999))
                .retryCount(3)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.BLOCKED);
        assertThat(result.getReason()).isEqualTo("Maximum retry attempts reached.");
    }

    @Test
    void retryPayment_exceedsMaxRetries_isBlocked() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.RETRY_PAYMENT)
                .paymentAmount(BigDecimal.valueOf(4999))
                .retryCount(5)
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.BLOCKED);
    }

    // ---- Discount limit ----

    @Test
    void offerDiscount_withinLimit_isAllowed() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.OFFER_DISCOUNT)
                .paymentAmount(BigDecimal.valueOf(9999))
                .discountPercent(10)
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.ALLOWED);
    }

    @Test
    void offerDiscount_exceedsLimit_isBlocked() {
        // Matches spec's second demo scenario exactly: ₹9,999 payment,
        // AI requests 20% discount, merchant limit is 10% -> BLOCKED.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.OFFER_DISCOUNT)
                .paymentAmount(BigDecimal.valueOf(9999))
                .discountPercent(20)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.BLOCKED);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).isEqualTo("Requested discount exceeds merchant policy.");
    }

    // ---- High-value payment handling ----

    @Test
    void highValuePayment_sensitiveAction_requiresApproval() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.RETRY_PAYMENT)
                .paymentAmount(BigDecimal.valueOf(75000))
                .retryCount(0)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.REQUIRES_APPROVAL);
        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    void highValuePayment_nonSensitiveAction_isAllowed() {
        // SEND_REMINDER never moves money, so it's exempt from the
        // high-value human-approval requirement even for a huge payment.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.SEND_REMINDER)
                .paymentAmount(BigDecimal.valueOf(200000))
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.ALLOWED);
    }

    @Test
    void lowValuePayment_sensitiveAction_isAllowedWithoutApproval() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.CREATE_PAYMENT_LINK)
                .paymentAmount(BigDecimal.valueOf(2499))
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.ALLOWED);
    }

    @Test
    void discountBlock_takesPrecedenceOverHighValueApproval() {
        // A discount that exceeds the merchant's limit is invalid on its
        // own terms — it should come back BLOCKED, not REQUIRES_APPROVAL,
        // even when the payment also happens to be high-value.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.OFFER_DISCOUNT)
                .paymentAmount(BigDecimal.valueOf(75000))
                .discountPercent(20)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.BLOCKED);
        assertThat(result.getReason()).isEqualTo("Requested discount exceeds merchant policy.");
    }

    // ---- Refunds ----

    @Test
    void refund_alwaysRequiresHumanApproval() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.NO_ACTION)
                .paymentAmount(BigDecimal.valueOf(500))
                .isRefund(true)
                .build();

        PolicyResult result = policyEngine.evaluate(ctx);

        assertThat(result.getStatus()).isEqualTo(PolicyStatus.REQUIRES_APPROVAL);
        assertThat(result.isAllowed()).isFalse();
    }

    @Test
    void refund_belowAutomaticThreshold_stillRequiresApprovalInMvp() {
        // Spec section 10 is explicit: refunds are NEVER auto-executed by
        // the AI in the MVP, regardless of amount — MAX_AUTOMATIC_REFUND_AMOUNT
        // exists for a future automatic-refund path, not this one.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.NO_ACTION)
                .paymentAmount(BigDecimal.valueOf(100))
                .isRefund(true)
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.REQUIRES_APPROVAL);
    }

    // ---- No-op / passthrough actions ----

    @Test
    void escalateToHuman_isAlwaysAllowedThroughPolicy() {
        // Escalating IS the safe path — the policy engine allows the
        // escalation itself; it doesn't need its own approval step.
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .actionType(ActionType.ESCALATE_TO_HUMAN)
                .paymentAmount(BigDecimal.valueOf(999999))
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.ALLOWED);
    }

    @Test
    void missingActionType_isBlockedDefensively() {
        PolicyEvaluationContext ctx = PolicyEvaluationContext.builder()
                .paymentAmount(BigDecimal.valueOf(999))
                .build();

        assertThat(policyEngine.evaluate(ctx).getStatus()).isEqualTo(PolicyStatus.BLOCKED);
    }
}
