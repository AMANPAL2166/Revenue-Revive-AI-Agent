package com.reviveai.service;

import com.reviveai.ai.AgentContext;
import com.reviveai.ai.AgentDecision;
import com.reviveai.ai.AgentService;
import com.reviveai.entity.AgentAction;
import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.PaymentStatus;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.policy.PolicyEngine;
import com.reviveai.policy.PolicyResult;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryResult;
import com.reviveai.repository.AgentActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryServiceTest {

    @Mock private RecoveryCaseRepository recoveryCaseRepository;
    @Mock private AgentActionRepository agentActionRepository;
    @Mock private RevenueRiskService revenueRiskService;
    @Mock private AgentService agentService;
    @Mock private PolicyEngine policyEngine;
    @Mock private RecoveryActionExecutor recoveryActionExecutor;

    private RecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        recoveryService = new RecoveryService(
                recoveryCaseRepository, agentActionRepository, revenueRiskService,
                agentService, policyEngine, recoveryActionExecutor);

        // Defaults for the plumbing every test needs.
        when(recoveryCaseRepository.save(any(RecoveryCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agentActionRepository.save(any(AgentAction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recoveryCaseRepository.existsByCustomerIdAndStatus(any(), any())).thenReturn(false);
        when(recoveryCaseRepository.findFirstByPaymentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());
    }

    private Payment samplePayment() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Rahul Sharma")
                .email("rahul@example.com")
                .lifetimeValue(BigDecimal.valueOf(54990))
                .successfulPayments(11)
                .failedPayments(1)
                .build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .externalPaymentId("pay_test")
                .customer(customer)
                .amount(BigDecimal.valueOf(4999))
                .currency("INR")
                .status(PaymentStatus.FAILED)
                .failureReason("Insufficient funds")
                .retryCount(0)
                .updatedAt(Instant.now())
                .build();
    }

    private DecisionMetrics sampleMetrics() {
        return DecisionMetrics.builder()
                .revenueAtRisk(BigDecimal.valueOf(4999))
                .customerLifetimeValue(BigDecimal.valueOf(54990))
                .paymentSuccessRate(BigDecimal.valueOf(0.9167))
                .recoveryProbability(BigDecimal.valueOf(0.85))
                .expectedRecoveryValue(BigDecimal.valueOf(4249.15))
                .priority(Priority.HIGH)
                .reasoningTrace(List.of())
                .build();
    }

    // ---- Allowed path: auto-executes ----

    @Test
    void createCaseForFailedPayment_policyAllows_executesImmediately() {
        Payment payment = samplePayment();
        when(revenueRiskService.calculateForPayment(payment)).thenReturn(sampleMetrics());
        when(agentService.recommend(any(AgentContext.class))).thenReturn(
                AgentDecision.builder()
                        .action(ActionType.RETRY_PAYMENT)
                        .confidence(0.91)
                        .reason("Strong payment history, temporary failure.")
                        .suggestedDelayHours(24)
                        .discountPercent(0)
                        .validAiResponse(true)
                        .build());
        when(policyEngine.evaluate(any())).thenReturn(PolicyResult.allowed("Within limits."));
        when(recoveryActionExecutor.execute(any())).thenReturn(
                RecoveryResult.builder().success(true).message("Retry scheduled.").executedAt(Instant.now()).simulated(true).build());

        RecoveryCase result = recoveryService.createCaseForFailedPayment(payment);

        assertThat(result.getStatus()).isEqualTo(RecoveryCaseStatus.EXECUTED);
        assertThat(result.getFinalAction()).isEqualTo(ActionType.RETRY_PAYMENT);
        assertThat(result.getRecommendedAction()).isEqualTo(ActionType.RETRY_PAYMENT);
        verify(recoveryActionExecutor, times(1)).execute(any());
    }

    // ---- Blocked path: never executes ----

    @Test
    void createCaseForFailedPayment_policyBlocks_landsOnHumanReviewWithoutExecuting() {
        Payment payment = samplePayment();
        when(revenueRiskService.calculateForPayment(payment)).thenReturn(sampleMetrics());
        when(agentService.recommend(any(AgentContext.class))).thenReturn(
                AgentDecision.builder()
                        .action(ActionType.OFFER_DISCOUNT)
                        .confidence(0.7)
                        .reason("Offer a discount to close the deal.")
                        .discountPercent(20)
                        .validAiResponse(true)
                        .build());
        when(policyEngine.evaluate(any())).thenReturn(PolicyResult.blocked("Requested discount exceeds merchant policy."));

        RecoveryCase result = recoveryService.createCaseForFailedPayment(payment);

        assertThat(result.getStatus()).isEqualTo(RecoveryCaseStatus.HUMAN_REVIEW);
        assertThat(result.getFinalAction()).isNull();
        verify(recoveryActionExecutor, never()).execute(any());
    }

    // ---- Requires-approval path: also never executes ----

    @Test
    void createCaseForFailedPayment_policyRequiresApproval_landsOnHumanReviewWithoutExecuting() {
        Payment payment = samplePayment();
        when(revenueRiskService.calculateForPayment(payment)).thenReturn(sampleMetrics());
        when(agentService.recommend(any(AgentContext.class))).thenReturn(
                AgentDecision.builder()
                        .action(ActionType.RETRY_PAYMENT)
                        .confidence(0.8)
                        .reason("High value payment.")
                        .validAiResponse(true)
                        .build());
        when(policyEngine.evaluate(any())).thenReturn(PolicyResult.requiresApproval("High-value payment."));

        RecoveryCase result = recoveryService.createCaseForFailedPayment(payment);

        assertThat(result.getStatus()).isEqualTo(RecoveryCaseStatus.HUMAN_REVIEW);
        verify(recoveryActionExecutor, never()).execute(any());
    }

    // ---- Duplicate-case prevention ----

    @Test
    void createCaseForFailedPayment_inFlightCaseAlreadyExists_skipsCreationEntirely() {
        Payment payment = samplePayment();
        RecoveryCase existing = RecoveryCase.builder()
                .id(UUID.randomUUID())
                .customer(payment.getCustomer())
                .payment(payment)
                .status(RecoveryCaseStatus.HUMAN_REVIEW)
                .build();
        when(recoveryCaseRepository.findFirstByPaymentIdAndStatusIn(eq(payment.getId()), any()))
                .thenReturn(Optional.of(existing));

        RecoveryCase result = recoveryService.createCaseForFailedPayment(payment);

        assertThat(result).isSameAs(existing);
        verify(agentService, never()).recommend(any());
        verify(policyEngine, never()).evaluate(any());
    }

    // ---- Approve / reject ----

    @Test
    void approve_onHumanReviewCase_executesTheRecommendedAction() {
        Payment payment = samplePayment();
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .id(UUID.randomUUID())
                .customer(payment.getCustomer())
                .payment(payment)
                .revenueAtRisk(BigDecimal.valueOf(4999))
                .recoveryProbability(BigDecimal.valueOf(0.85))
                .status(RecoveryCaseStatus.HUMAN_REVIEW)
                .build();
        AgentAction latest = AgentAction.builder()
                .id(UUID.randomUUID())
                .recoveryCase(recoveryCase)
                .actionType(ActionType.RETRY_PAYMENT)
                .reasoning("test")
                .confidence(0.8)
                .build();

        when(recoveryCaseRepository.findById(recoveryCase.getId())).thenReturn(Optional.of(recoveryCase));
        when(agentActionRepository.findByRecoveryCaseIdOrderByProposedAtAsc(recoveryCase.getId()))
                .thenReturn(List.of(latest));
        when(recoveryActionExecutor.execute(any())).thenReturn(
                RecoveryResult.builder().success(true).message("done").executedAt(Instant.now()).build());

        RecoveryCase result = recoveryService.approve(recoveryCase.getId());

        assertThat(result.getStatus()).isEqualTo(RecoveryCaseStatus.EXECUTED);
        verify(recoveryActionExecutor, times(1)).execute(any());
    }

    @Test
    void approve_onNonHumanReviewCase_throws() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .id(UUID.randomUUID())
                .status(RecoveryCaseStatus.EXECUTED)
                .build();
        when(recoveryCaseRepository.findById(recoveryCase.getId())).thenReturn(Optional.of(recoveryCase));

        assertThatThrownBy(() -> recoveryService.approve(recoveryCase.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reject_onHumanReviewCase_setsFailedStatusAndNoAction() {
        RecoveryCase recoveryCase = RecoveryCase.builder()
                .id(UUID.randomUUID())
                .status(RecoveryCaseStatus.HUMAN_REVIEW)
                .build();
        when(recoveryCaseRepository.findById(recoveryCase.getId())).thenReturn(Optional.of(recoveryCase));

        RecoveryCase result = recoveryService.reject(recoveryCase.getId());

        assertThat(result.getStatus()).isEqualTo(RecoveryCaseStatus.FAILED);
        assertThat(result.getFinalAction()).isEqualTo(ActionType.NO_ACTION);
        verify(recoveryActionExecutor, never()).execute(any());
    }
}
