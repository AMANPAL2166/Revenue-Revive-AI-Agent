package com.reviveai.service;

import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.entity.Subscription;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.util.FailureReasonClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calculates every revenue-recovery decision metric deterministically.
 *
 * Nothing here is machine learning. recoveryProbability is an explicit,
 * documented rule-based heuristic (see calculateRecoveryProbability) —
 * suitable for an MVP and intended to be replaced by a trained model later
 * (see README "Future Improvements"). Every other metric is plain
 * arithmetic on data already in the database.
 *
 * The AI Agent (Day 5) receives the output of this class as read-only
 * context; it never recomputes or overrides these numbers, and the
 * PolicyEngine (Day 4) never sees these numbers change once a
 * RecoveryCase has moved past ANALYZING.
 */
@Service
@RequiredArgsConstructor
public class RevenueRiskService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final FailureReasonClassifier failureReasonClassifier;

    private static final int VERY_HIGH_RETRY_COUNT = 3;
    private static final int MANY_RECENT_FAILURES = 3;
    private static final long LONG_OVERDUE_DAYS = 7;

    // Priority thresholds — MVP constants, not merchant-configurable (unlike
    // PolicyEngine's limits, which live in ReviveAiProperties.Policy).
    private static final BigDecimal HIGH_REVENUE_THRESHOLD = BigDecimal.valueOf(20000);
    private static final BigDecimal MEDIUM_REVENUE_THRESHOLD = BigDecimal.valueOf(5000);
    private static final BigDecimal HIGH_LTV_THRESHOLD = BigDecimal.valueOf(50000);
    private static final BigDecimal MEDIUM_LTV_THRESHOLD = BigDecimal.valueOf(15000);

    public DecisionMetrics calculateForPayment(Payment payment) {
        Customer customer = payment.getCustomer();
        List<String> trace = new ArrayList<>();

        BigDecimal revenueAtRisk = payment.getAmount();
        BigDecimal clv = customerLifetimeValue(customer);
        BigDecimal successRate = paymentSuccessRate(customer, trace);

        long daysSinceFailure = payment.getUpdatedAt() != null
                ? ChronoUnit.DAYS.between(payment.getUpdatedAt(), Instant.now())
                : 0;

        // NOTE (documented MVP limitation): customer.getFailedPayments() is
        // a lifetime count, used here as a proxy for "recent failures"
        // since the Day 1 schema does not yet track failure timestamps
        // per-window. Good enough for the buildathon; flagged for the
        // "Future Improvements" section of the README.
        BigDecimal recoveryProbability = calculateRecoveryProbability(
                successRate,
                payment.getFailureReason(),
                hasPreviousSuccessfulRecovery(customer.getId()),
                customer.getFailedPayments() != null ? customer.getFailedPayments() : 0,
                payment.getRetryCount() != null ? payment.getRetryCount() : 0,
                daysSinceFailure,
                trace
        );

        BigDecimal expectedRecoveryValue = expectedRecoveryValue(revenueAtRisk, recoveryProbability);
        Priority priority = calculatePriority(revenueAtRisk, clv, recoveryProbability, payment.getFailureReason(), trace);

        return DecisionMetrics.builder()
                .revenueAtRisk(revenueAtRisk)
                .customerLifetimeValue(clv)
                .paymentSuccessRate(successRate)
                .recoveryProbability(recoveryProbability)
                .expectedRecoveryValue(expectedRecoveryValue)
                .priority(priority)
                .reasoningTrace(trace)
                .build();
    }

    public DecisionMetrics calculateForSubscription(Subscription subscription) {
        Customer customer = subscription.getCustomer();
        List<String> trace = new ArrayList<>();

        BigDecimal revenueAtRisk = subscription.getAmount();
        BigDecimal clv = customerLifetimeValue(customer);
        BigDecimal successRate = paymentSuccessRate(customer, trace);

        long daysOverdue = subscription.getRenewalDate() != null
                ? ChronoUnit.DAYS.between(subscription.getRenewalDate(), LocalDate.now())
                : 0;

        int failureCount = subscription.getFailureCount() != null ? subscription.getFailureCount() : 0;

        // Subscriptions don't carry a payment-gateway failure reason and
        // don't yet distinguish "recent failures" from "retry attempts" —
        // failureCount is used for both signals, documented here.
        BigDecimal recoveryProbability = calculateRecoveryProbability(
                successRate,
                null,
                hasPreviousSuccessfulRecovery(customer.getId()),
                failureCount,
                failureCount,
                Math.max(daysOverdue, 0),
                trace
        );

        BigDecimal expectedRecoveryValue = expectedRecoveryValue(revenueAtRisk, recoveryProbability);
        Priority priority = calculatePriority(revenueAtRisk, clv, recoveryProbability, null, trace);

        return DecisionMetrics.builder()
                .revenueAtRisk(revenueAtRisk)
                .customerLifetimeValue(clv)
                .paymentSuccessRate(successRate)
                .recoveryProbability(recoveryProbability)
                .expectedRecoveryValue(expectedRecoveryValue)
                .priority(priority)
                .reasoningTrace(trace)
                .build();
    }

    // ---- 6.2 Customer Lifetime Value ----
    private BigDecimal customerLifetimeValue(Customer customer) {
        return customer.getLifetimeValue() != null ? customer.getLifetimeValue() : BigDecimal.ZERO;
    }

    // ---- 6.3 Payment Success Rate ----
    /**
     * successfulPayments / (successfulPayments + failedPayments).
     *
     * A customer with zero payment history has no evidence of reliability,
     * so this deliberately returns 0 (risk-averse) rather than dividing by
     * zero or defaulting optimistically to 1. This directly affects
     * recoveryProbability downstream, so it's called out explicitly.
     */
    private BigDecimal paymentSuccessRate(Customer customer, List<String> trace) {
        int success = customer.getSuccessfulPayments() != null ? customer.getSuccessfulPayments() : 0;
        int failed = customer.getFailedPayments() != null ? customer.getFailedPayments() : 0;
        int total = success + failed;
        if (total == 0) {
            trace.add("No payment history — success rate defaulted to 0%");
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    // ---- 6.4 Recovery Probability (MVP heuristic, NOT machine learning) ----
    /**
     * Base 50%, adjusted by explicit, documented signals, then clamped to
     * [0, 100] and returned as a 0..1 fraction. Every adjustment is logged
     * to `trace` so the calculation stays inspectable and explainable —
     * this is the property that lets the dashboard show *why* a number is
     * what it is, without pretending it's a trained model.
     */
    private BigDecimal calculateRecoveryProbability(
            BigDecimal successRate,
            String failureReason,
            boolean hasPreviousSuccessfulRecovery,
            int recentFailureCount,
            int retryCount,
            long daysOverdueOrSinceFailure,
            List<String> trace
    ) {
        int score = 50;
        trace.add("Base probability: 50");

        // --- Positive signals ---
        if (successRate.compareTo(BigDecimal.valueOf(0.8)) >= 0) {
            score += 15;
            trace.add("+15: strong payment history (success rate >= 80%)");
        } else if (successRate.compareTo(BigDecimal.valueOf(0.5)) >= 0) {
            score += 7;
            trace.add("+7: moderate payment history (success rate >= 50%)");
        }

        FailureReasonClassifier.Severity severity = failureReasonClassifier.classify(failureReason);
        if (severity == FailureReasonClassifier.Severity.RECOVERABLE) {
            score += 15;
            trace.add("+15: recoverable failure reason (" + failureReason + ")");
        } else if (severity == FailureReasonClassifier.Severity.SEVERE) {
            score -= 25;
            trace.add("-25: severe/non-recoverable failure reason (" + failureReason + ")");
        }

        if (hasPreviousSuccessfulRecovery) {
            score += 10;
            trace.add("+10: customer has a previous successful recovery");
        }

        if (recentFailureCount <= 1) {
            score += 5;
            trace.add("+5: low recent failure frequency");
        }

        // --- Negative signals ---
        if (recentFailureCount >= MANY_RECENT_FAILURES) {
            score -= 15;
            trace.add("-15: multiple recent failures (" + recentFailureCount + ")");
        }
        if (retryCount >= VERY_HIGH_RETRY_COUNT) {
            score -= 15;
            trace.add("-15: very high retry count (" + retryCount + ")");
        }
        if (daysOverdueOrSinceFailure >= LONG_OVERDUE_DAYS) {
            score -= 10;
            trace.add("-10: long overdue duration (" + daysOverdueOrSinceFailure + " days)");
        }

        int clamped = Math.max(0, Math.min(100, score));
        if (clamped != score) {
            trace.add("Clamped " + score + " -> " + clamped);
        }

        return BigDecimal.valueOf(clamped).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    // ---- 6.5 Expected Recovery Value ----
    private BigDecimal expectedRecoveryValue(BigDecimal revenueAtRisk, BigDecimal recoveryProbabilityFraction) {
        return revenueAtRisk.multiply(recoveryProbabilityFraction).setScale(2, RoundingMode.HALF_UP);
    }

    // ---- 6.6 Priority ----
    /**
     * Deterministic point-scoring across revenue at risk, recovery
     * probability, and customer lifetime value, penalized for severe
     * (non-recoverable) failure reasons.
     *
     *   score >= 4  -> HIGH
     *   score >= 2  -> MEDIUM
     *   else        -> LOW
     */
    private Priority calculatePriority(
            BigDecimal revenueAtRisk,
            BigDecimal customerLifetimeValue,
            BigDecimal recoveryProbabilityFraction,
            String failureReason,
            List<String> trace
    ) {
        int score = 0;

        if (revenueAtRisk.compareTo(HIGH_REVENUE_THRESHOLD) >= 0) {
            score += 2;
            trace.add("Priority +2: revenue at risk >= " + HIGH_REVENUE_THRESHOLD);
        } else if (revenueAtRisk.compareTo(MEDIUM_REVENUE_THRESHOLD) >= 0) {
            score += 1;
            trace.add("Priority +1: revenue at risk >= " + MEDIUM_REVENUE_THRESHOLD);
        }

        if (customerLifetimeValue.compareTo(HIGH_LTV_THRESHOLD) >= 0) {
            score += 2;
            trace.add("Priority +2: customer LTV >= " + HIGH_LTV_THRESHOLD);
        } else if (customerLifetimeValue.compareTo(MEDIUM_LTV_THRESHOLD) >= 0) {
            score += 1;
            trace.add("Priority +1: customer LTV >= " + MEDIUM_LTV_THRESHOLD);
        }

        BigDecimal probabilityPercent = recoveryProbabilityFraction.multiply(BigDecimal.valueOf(100));
        if (probabilityPercent.compareTo(BigDecimal.valueOf(70)) >= 0) {
            score += 2;
            trace.add("Priority +2: recovery probability >= 70%");
        } else if (probabilityPercent.compareTo(BigDecimal.valueOf(40)) >= 0) {
            score += 1;
            trace.add("Priority +1: recovery probability >= 40%");
        }

        if (failureReasonClassifier.isSevere(failureReason)) {
            score -= 2;
            trace.add("Priority -2: severe failure reason");
        }

        Priority priority;
        if (score >= 4) {
            priority = Priority.HIGH;
        } else if (score >= 2) {
            priority = Priority.MEDIUM;
        } else {
            priority = Priority.LOW;
        }
        trace.add("Priority score = " + score + " -> " + priority);
        return priority;
    }

    private boolean hasPreviousSuccessfulRecovery(UUID customerId) {
        return recoveryCaseRepository.existsByCustomerIdAndStatus(customerId, RecoveryCaseStatus.RECOVERED);
    }
}