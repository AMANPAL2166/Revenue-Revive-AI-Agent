package com.reviveai.dto.response;

import com.reviveai.entity.AgentAction;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RecoveryCaseDetailResponse {
    private UUID id;
    private CustomerResponse customer;
    private PaymentResponse payment;

    private BigDecimal revenueAtRisk;
    private BigDecimal customerLifetimeValue;
    private BigDecimal paymentSuccessRate;
    private BigDecimal recoveryProbability;
    private BigDecimal expectedRecoveryValue;
    private Priority priority;

    private AiRecommendationResponse aiRecommendation;
    private PolicyDecisionResponse policyDecision;
    private ExecutionResultResponse executionResult;

    private ActionType recommendedAction;
    private ActionType finalAction;
    private RecoveryCaseStatus status;

    private List<TimelineEntryResponse> timeline;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * @param actions all AgentActions for this case, oldest first (as
     *                returned by AgentActionRepository); the last element
     *                (if any) is treated as the "current" recommendation.
     */
    public static RecoveryCaseDetailResponse from(RecoveryCase c, List<AgentAction> actions) {
        AgentAction latest = actions.isEmpty() ? null : actions.get(actions.size() - 1);

        return RecoveryCaseDetailResponse.builder()
                .id(c.getId())
                .customer(c.getCustomer() != null ? CustomerResponse.from(c.getCustomer()) : null)
                .payment(c.getPayment() != null ? PaymentResponse.from(c.getPayment()) : null)
                .revenueAtRisk(c.getRevenueAtRisk())
                .customerLifetimeValue(c.getCustomerLifetimeValue())
                .paymentSuccessRate(c.getPaymentSuccessRate())
                .recoveryProbability(c.getRecoveryProbability())
                .expectedRecoveryValue(c.getExpectedRecoveryValue())
                .priority(c.getPriority())
                .aiRecommendation(latest == null ? null : AiRecommendationResponse.builder()
                        .action(latest.getActionType())
                        .confidence(latest.getConfidence())
                        .reasoning(latest.getReasoning())
                        .build())
                .policyDecision(latest == null ? null : PolicyDecisionResponse.builder()
                        .status(latest.getPolicyStatus())
                        .reason(latest.getPolicyReason())
                        .build())
                .executionResult(latest == null || latest.getExecutedAt() == null ? null : ExecutionResultResponse.builder()
                        .success(Boolean.TRUE.equals(latest.getExecutionSuccess()))
                        .message(latest.getExecutionResultMessage())
                        .simulated(Boolean.TRUE.equals(latest.getExecutionSimulated()))
                        .executedAt(latest.getExecutedAt())
                        .build())
                .recommendedAction(c.getRecommendedAction())
                .finalAction(c.getFinalAction())
                .status(c.getStatus())
                .timeline(buildTimeline(c, latest))
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    /**
     * MVP limitation, documented here rather than hidden: because the
     * metrics -> AI -> policy sequence runs synchronously within one
     * request (see RecoveryService.analyze), there's no separately
     * persisted timestamp for "decision metrics calculated" vs. "revenue
     * risk detected", or for "policy validation" vs. "AI recommendation" —
     * they share a timestamp here. Only stages with real, available
     * timestamps are included.
     */
    private static List<TimelineEntryResponse> buildTimeline(RecoveryCase c, AgentAction latest) {
        List<TimelineEntryResponse> entries = new ArrayList<>();

        if (c.getPayment() != null && c.getPayment().getUpdatedAt() != null) {
            entries.add(TimelineEntryResponse.builder()
                    .label("Payment Failed")
                    .timestamp(c.getPayment().getUpdatedAt())
                    .description("Payment " + c.getPayment().getExternalPaymentId()
                            + " failed: " + c.getPayment().getFailureReason())
                    .build());
        }

        entries.add(TimelineEntryResponse.builder()
                .label("Revenue Risk Detected")
                .timestamp(c.getCreatedAt())
                .description("Case opened. Revenue at risk: " + c.getRevenueAtRisk())
                .build());

        entries.add(TimelineEntryResponse.builder()
                .label("Decision Metrics Calculated")
                .timestamp(c.getCreatedAt())
                .description("Priority: " + c.getPriority() + ", recovery probability: " + c.getRecoveryProbability())
                .build());

        if (latest != null) {
            entries.add(TimelineEntryResponse.builder()
                    .label("AI Recommendation")
                    .timestamp(latest.getProposedAt())
                    .description(latest.getActionType() + " (confidence " + latest.getConfidence() + ")")
                    .build());

            entries.add(TimelineEntryResponse.builder()
                    .label("Policy Validation")
                    .timestamp(latest.getProposedAt())
                    .description(latest.getPolicyStatus() + (latest.getPolicyReason() != null ? ": " + latest.getPolicyReason() : ""))
                    .build());

            if (latest.getExecutedAt() != null) {
                entries.add(TimelineEntryResponse.builder()
                        .label("Action Executed")
                        .timestamp(latest.getExecutedAt())
                        .description(latest.getExecutionResultMessage())
                        .build());
            }
        }

        if (c.getStatus() == RecoveryCaseStatus.RECOVERED) {
            entries.add(TimelineEntryResponse.builder()
                    .label("Payment Recovered")
                    .timestamp(c.getUpdatedAt())
                    .description("Revenue recovered.")
                    .build());
        }

        return entries;
    }
}
