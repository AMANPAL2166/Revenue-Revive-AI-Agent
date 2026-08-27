package com.reviveai.policy;

import com.reviveai.enums.ActionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Everything PolicyEngine needs to validate a single proposed action.
 * Deliberately decoupled from AgentDecision (Day 5) and RecoveryCase, so
 * PolicyEngine has zero compile-time dependency on the AI layer — it only
 * ever sees plain values, which keeps it fully deterministic and trivial
 * to unit test in isolation.
 */
@Getter
@Builder
public class PolicyEvaluationContext {

    private ActionType actionType;

    /** The payment/subscription amount this action concerns — drives the high-value check. */
    private BigDecimal paymentAmount;

    /** Current retry count on the payment — drives the retry-limit check. */
    @Builder.Default
    private int retryCount = 0;

    /** Only meaningful when actionType == OFFER_DISCOUNT. */
    private Integer discountPercent;

    /**
     * Forward-compatible flag for refund handling. No current ActionType
     * represents a refund (the spec's fixed action set has none), but the
     * Policy Engine still enforces "refunds always require human review"
     * so a future refund path — a new ActionType, or a manual
     * merchant-initiated refund — can reuse this exact check without any
     * change to PolicyEngine.
     */
    @Builder.Default
    private boolean isRefund = false;
}
