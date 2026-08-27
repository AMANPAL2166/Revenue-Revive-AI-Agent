package com.reviveai.policy;

import com.reviveai.enums.ActionType;
import com.reviveai.enums.PolicyStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * The deterministic safety layer between the AI Agent's recommendation and
 * RecoveryActionExecutor. Central principle: "AI decides; Policy Engine
 * controls." Every AI-proposed action must pass through here before it can
 * run.
 *
 * PolicyEngine never talks to the LLM and never reads AI confidence scores
 * or reasoning text — it only evaluates merchant-configured limits against
 * plain values (amount, retry count, discount percent), which keeps it
 * fully deterministic, side-effect-free, and exhaustively unit-testable.
 */
@Service
@RequiredArgsConstructor
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final MerchantPolicy merchantPolicy;

    /**
     * Actions that move money or grant a concession, and therefore fall
     * under the high-value-payment human-approval rule. SEND_REMINDER,
     * ESCALATE_TO_HUMAN, and NO_ACTION never spend anything on the
     * merchant's behalf, so they're exempt from this check regardless of
     * how large the underlying payment is.
     */
    private static final Set<ActionType> SENSITIVE_ACTIONS = EnumSet.of(
            ActionType.RETRY_PAYMENT, ActionType.CREATE_PAYMENT_LINK, ActionType.OFFER_DISCOUNT
    );

    public PolicyResult evaluate(PolicyEvaluationContext context) {
        // Refunds: never auto-executed, regardless of action type or amount.
        // This check runs first and short-circuits everything else because
        // no downstream rule can make a refund more or less approvable.
        if (context.isRefund() && merchantPolicy.isRefundRequiresHumanApproval()) {
            return logAndReturn(PolicyResult.requiresApproval(
                    "Refunds always require human approval under current merchant policy."));
        }

        PolicyResult actionSpecific = evaluateActionSpecificRules(context);
        if (actionSpecific.getStatus() == PolicyStatus.BLOCKED) {
            // A hard block is final. It overrides the high-value check too:
            // there's nothing left to seek approval for if the requested
            // action itself violates a merchant limit (e.g. a 20% discount
            // request when the cap is 10% is invalid at any payment size).
            return logAndReturn(actionSpecific);
        }

        PolicyResult highValueResult = evaluateHighValueRule(context);
        if (highValueResult.getStatus() == PolicyStatus.REQUIRES_APPROVAL) {
            return logAndReturn(highValueResult);
        }

        return logAndReturn(actionSpecific);
    }

    private PolicyResult evaluateActionSpecificRules(PolicyEvaluationContext context) {
        if (context.getActionType() == null) {
            return PolicyResult.blocked("No action type provided.");
        }

        return switch (context.getActionType()) {
            case RETRY_PAYMENT -> {
                if (context.getRetryCount() >= merchantPolicy.getMaxPaymentRetries()) {
                    yield PolicyResult.blocked("Maximum retry attempts reached.");
                }
                yield PolicyResult.allowed("Retry count within merchant policy limit.");
            }
            case OFFER_DISCOUNT -> {
                int requested = context.getDiscountPercent() != null ? context.getDiscountPercent() : 0;
                if (requested > merchantPolicy.getMaxDiscountPercent()) {
                    yield PolicyResult.blocked("Requested discount exceeds merchant policy.");
                }
                yield PolicyResult.allowed("Requested discount within merchant policy limit.");
            }
            case SEND_REMINDER, CREATE_PAYMENT_LINK, ESCALATE_TO_HUMAN, NO_ACTION ->
                    PolicyResult.allowed("No action-specific restriction applies.");
        };
    }

    private PolicyResult evaluateHighValueRule(PolicyEvaluationContext context) {
        BigDecimal amount = context.getPaymentAmount();
        if (amount == null || !merchantPolicy.isHighValueActionsRequireHumanApproval()) {
            return PolicyResult.allowed("High-value check not applicable.");
        }
        if (!SENSITIVE_ACTIONS.contains(context.getActionType())) {
            return PolicyResult.allowed("Action does not move money; high-value check skipped.");
        }
        if (amount.compareTo(merchantPolicy.getHighValuePaymentThreshold()) > 0) {
            return PolicyResult.requiresApproval(
                    "Payment amount exceeds high-value threshold ("
                            + merchantPolicy.getHighValuePaymentThreshold()
                            + "); sensitive actions require human approval.");
        }
        return PolicyResult.allowed("Payment amount within high-value threshold.");
    }

    private PolicyResult logAndReturn(PolicyResult result) {
        log.info("Policy decision: {} — {}", result.getStatus(), result.getReason());
        return result;
    }
}
