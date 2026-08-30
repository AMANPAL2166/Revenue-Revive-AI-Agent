package com.reviveai.service;

import com.reviveai.ai.AgentContext;
import com.reviveai.ai.AgentDecision;
import com.reviveai.ai.AgentService;
import com.reviveai.entity.AgentAction;
import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.PolicyStatus;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.exception.ResourceNotFoundException;
import com.reviveai.policy.PolicyEngine;
import com.reviveai.policy.PolicyEvaluationContext;
import com.reviveai.policy.PolicyResult;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryExecutionContext;
import com.reviveai.recovery.RecoveryResult;
import com.reviveai.repository.AgentActionRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The orchestrator of the Revenue Recovery Engine — ties RevenueRiskService
 * (metrics) -> AgentService (AI) -> PolicyEngine (safety) ->
 * RecoveryActionExecutor (execution) into the single pipeline described in
 * the spec's Core Product Flow. This is the only class in the codebase
 * that depends on all four.
 */
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    /** A payment already has a case "in flight" if it's anywhere in these statuses — don't open a second one. */
    private static final Set<RecoveryCaseStatus> IN_FLIGHT_STATUSES = EnumSet.of(
            RecoveryCaseStatus.OPEN, RecoveryCaseStatus.ANALYZING, RecoveryCaseStatus.ACTION_PROPOSED,
            RecoveryCaseStatus.HUMAN_REVIEW, RecoveryCaseStatus.APPROVED, RecoveryCaseStatus.EXECUTED
    );

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AgentActionRepository agentActionRepository;
    private final RevenueRiskService revenueRiskService;
    private final AgentService agentService;
    private final PolicyEngine policyEngine;
    private final RecoveryActionExecutor recoveryActionExecutor;

    // ---- Entry point from the webhook pipeline: payment.failed ----

    @Transactional
    public RecoveryCase createCaseForFailedPayment(Payment payment) {
        var existing = recoveryCaseRepository.findFirstByPaymentIdAndStatusIn(payment.getId(), IN_FLIGHT_STATUSES);
        if (existing.isPresent()) {
            log.info("An in-flight RecoveryCase already exists for payment {}, skipping creation.",
                    payment.getExternalPaymentId());
            return existing.get();
        }

        RecoveryCase recoveryCase = RecoveryCase.builder()
                .customer(payment.getCustomer())
                .payment(payment)
                .revenueAtRisk(payment.getAmount())
                .status(RecoveryCaseStatus.OPEN)
                .build();
        recoveryCase = recoveryCaseRepository.save(recoveryCase);

        return analyze(recoveryCase);
    }

    // ---- POST /recovery-cases/{id}/analyze (also called internally on case creation) ----

    @Transactional
    public RecoveryCase analyze(RecoveryCase recoveryCase) {
        Payment payment = recoveryCase.getPayment();
        Customer customer = recoveryCase.getCustomer();

        recoveryCase.setStatus(RecoveryCaseStatus.ANALYZING);
        recoveryCaseRepository.save(recoveryCase);

        var metrics = revenueRiskService.calculateForPayment(payment);

        recoveryCase.setRevenueAtRisk(metrics.getRevenueAtRisk());
        recoveryCase.setCustomerLifetimeValue(metrics.getCustomerLifetimeValue());
        recoveryCase.setPaymentSuccessRate(metrics.getPaymentSuccessRate());
        recoveryCase.setRecoveryProbability(metrics.getRecoveryProbability());
        recoveryCase.setExpectedRecoveryValue(metrics.getExpectedRecoveryValue());
        recoveryCase.setPriority(metrics.getPriority());

        boolean previousSuccessfulRecovery = recoveryCaseRepository
                .existsByCustomerIdAndStatus(customer.getId(), RecoveryCaseStatus.RECOVERED);

        AgentContext context = AgentContext.builder()
                .customerId(customer.getId())
                .customerLifetimeValue(metrics.getCustomerLifetimeValue())
                .successfulPayments(customer.getSuccessfulPayments())
                .failedPayments(customer.getFailedPayments())
                .paymentSuccessRate(metrics.getPaymentSuccessRate())
                .amount(payment.getAmount())
                .failureReason(payment.getFailureReason())
                .retryCount(payment.getRetryCount())
                .revenueAtRisk(metrics.getRevenueAtRisk())
                .recoveryProbability(metrics.getRecoveryProbability())
                .expectedRecoveryValue(metrics.getExpectedRecoveryValue())
                .priority(metrics.getPriority())
                .previousSuccessfulRecovery(previousSuccessfulRecovery)
                .build();

        AgentDecision decision = agentService.recommend(context);

        recoveryCase.setRecommendedAction(decision.getAction());
        recoveryCase.setStatus(RecoveryCaseStatus.ACTION_PROPOSED);
        recoveryCaseRepository.save(recoveryCase);

        AgentAction agentAction = AgentAction.builder()
                .recoveryCase(recoveryCase)
                .actionType(decision.getAction())
                .reasoning(decision.getReason())
                .confidence(decision.getConfidence())
                .discountPercent(decision.getDiscountPercent())
                .suggestedDelayHours(decision.getSuggestedDelayHours())
                .policyStatus(PolicyStatus.PENDING_REVIEW)
                .build();
        agentAction = agentActionRepository.save(agentAction);

        PolicyEvaluationContext policyContext = PolicyEvaluationContext.builder()
                .actionType(decision.getAction())
                .paymentAmount(metrics.getRevenueAtRisk())
                .retryCount(payment.getRetryCount() != null ? payment.getRetryCount() : 0)
                .discountPercent(decision.getDiscountPercent())
                .build();
        PolicyResult policyResult = policyEngine.evaluate(policyContext);

        agentAction.setPolicyStatus(policyResult.getStatus());
        agentAction.setPolicyReason(policyResult.getReason());
        agentActionRepository.save(agentAction);

        if (policyResult.isAllowed()) {
            recoveryCase.setFinalAction(decision.getAction());
            recoveryCase.setStatus(RecoveryCaseStatus.APPROVED);
            recoveryCaseRepository.save(recoveryCase);
            return executeInternal(recoveryCase, agentAction);
        }

        // BLOCKED and REQUIRES_APPROVAL both land the case on the same
        // human review queue — the distinction is preserved on the
        // AgentAction's policyStatus so the UI can explain *why* review is
        // needed (this is the "major demo moment" from the spec).
        recoveryCase.setStatus(RecoveryCaseStatus.HUMAN_REVIEW);
        return recoveryCaseRepository.save(recoveryCase);
    }

    // ---- POST /recovery-cases/{id}/approve ----

    @Transactional
    public RecoveryCase approve(UUID recoveryCaseId) {
        RecoveryCase recoveryCase = getById(recoveryCaseId);
        if (recoveryCase.getStatus() != RecoveryCaseStatus.HUMAN_REVIEW) {
            throw new IllegalStateException("Only cases in HUMAN_REVIEW can be approved.");
        }

        AgentAction latest = latestAgentAction(recoveryCaseId);
        recoveryCase.setFinalAction(latest.getActionType());
        recoveryCase.setStatus(RecoveryCaseStatus.APPROVED);
        recoveryCaseRepository.save(recoveryCase);

        return executeInternal(recoveryCase, latest);
    }

    // ---- POST /recovery-cases/{id}/reject ----

    @Transactional
    public RecoveryCase reject(UUID recoveryCaseId) {
        RecoveryCase recoveryCase = getById(recoveryCaseId);
        if (recoveryCase.getStatus() != RecoveryCaseStatus.HUMAN_REVIEW) {
            throw new IllegalStateException("Only cases in HUMAN_REVIEW can be rejected.");
        }
        recoveryCase.setFinalAction(ActionType.NO_ACTION);
        recoveryCase.setStatus(RecoveryCaseStatus.FAILED);
        return recoveryCaseRepository.save(recoveryCase);
    }

    // ---- POST /recovery-cases/{id}/execute (manual re-trigger, e.g. after approval) ----

    @Transactional
    public RecoveryCase execute(UUID recoveryCaseId) {
        RecoveryCase recoveryCase = getById(recoveryCaseId);
        AgentAction latest = latestAgentAction(recoveryCaseId);
        return executeInternal(recoveryCase, latest);
    }

    private RecoveryCase executeInternal(RecoveryCase recoveryCase, AgentAction agentAction) {
        Payment payment = recoveryCase.getPayment();

        RecoveryExecutionContext ctx = RecoveryExecutionContext.builder()
                .recoveryCaseId(recoveryCase.getId())
                .paymentId(payment.getId())
                .externalPaymentId(payment.getExternalPaymentId())
                .customerEmail(recoveryCase.getCustomer().getEmail())
                .amount(recoveryCase.getRevenueAtRisk())
                .currency(payment.getCurrency())
                .actionType(agentAction.getActionType())
                .discountPercent(agentAction.getDiscountPercent())
                .suggestedDelayHours(agentAction.getSuggestedDelayHours())
                .recoveryProbability(recoveryCase.getRecoveryProbability())
                .build();

        RecoveryResult result = recoveryActionExecutor.execute(ctx);

        agentAction.setExecutedAt(Instant.now());
        agentAction.setExecutionResultMessage(result.getMessage());
        agentAction.setExecutionSuccess(result.isSuccess());
        agentAction.setExecutionSimulated(result.isSimulated());
        agentActionRepository.save(agentAction);

        // ESCALATE_TO_HUMAN's "execution" IS routing to a human — it never
        // reaches EXECUTED the way a real external action does.
        recoveryCase.setStatus(agentAction.getActionType() == ActionType.ESCALATE_TO_HUMAN
                ? RecoveryCaseStatus.HUMAN_REVIEW
                : RecoveryCaseStatus.EXECUTED);
        recoveryCase.setFinalAction(agentAction.getActionType());

        log.info("Executed action {} for RecoveryCase {}: {}",
                agentAction.getActionType(), recoveryCase.getId(), result.getMessage());

        return recoveryCaseRepository.save(recoveryCase);
    }

    private AgentAction latestAgentAction(UUID recoveryCaseId) {
        List<AgentAction> actions = agentActionRepository.findByRecoveryCaseIdOrderByProposedAtAsc(recoveryCaseId);
        if (actions.isEmpty()) {
            throw new ResourceNotFoundException("No AgentAction found for RecoveryCase: " + recoveryCaseId);
        }
        return actions.get(actions.size() - 1);
    }

    // ---- POST /recovery-cases/{id}/analyze (manual re-trigger by id) ----

    @Transactional
    public RecoveryCase analyzeById(UUID recoveryCaseId) {
        return analyze(getById(recoveryCaseId));
    }

    // ---- GET /recovery-cases (list, optionally filtered) ----

    public Page<RecoveryCase> list(RecoveryCaseStatus status, Priority priority, Pageable pageable) {
        if (status != null && priority != null) {
            return recoveryCaseRepository.findByStatusAndPriority(status, priority, pageable);
        }
        if (status != null) {
            return recoveryCaseRepository.findByStatus(status, pageable);
        }
        if (priority != null) {
            return recoveryCaseRepository.findByPriority(priority, pageable);
        }
        return recoveryCaseRepository.findAll(pageable);
    }

    public RecoveryCase getById(UUID id) {
        return recoveryCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecoveryCase not found: " + id));
    }
}
