package com.reviveai.ai;

import com.reviveai.enums.Priority;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The complete context sent to the AI Agent. Deliberately a plain POJO
 * with no JPA entity references and no secrets — assembled by
 * RecoveryService (Day 6) from Customer + Payment/Subscription +
 * DecisionMetrics, per the spec's "AI Decision Context" rules: never
 * Razorpay keys, database credentials, or unnecessary personal data.
 */
@Getter
@Builder
public class AgentContext {
    private UUID customerId;
    private BigDecimal customerLifetimeValue;
    private int successfulPayments;
    private int failedPayments;
    private BigDecimal paymentSuccessRate;

    private BigDecimal amount;
    private String failureReason;
    private int retryCount;

    private BigDecimal revenueAtRisk;
    private BigDecimal recoveryProbability;
    private BigDecimal expectedRecoveryValue;
    private Priority priority;

    private boolean previousSuccessfulRecovery;
}
