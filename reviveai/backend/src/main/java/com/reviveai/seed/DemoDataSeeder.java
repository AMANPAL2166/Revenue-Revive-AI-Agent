package com.reviveai.seed;

import com.reviveai.config.ReviveAiProperties;
import com.reviveai.entity.AgentAction;
import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.PaymentStatus;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.enums.SubscriptionStatus;
import com.reviveai.policy.PolicyEngine;
import com.reviveai.policy.PolicyEvaluationContext;
import com.reviveai.policy.PolicyResult;
import com.reviveai.recovery.RecoveryActionExecutor;
import com.reviveai.recovery.RecoveryExecutionContext;
import com.reviveai.recovery.RecoveryResult;
import com.reviveai.repository.AgentActionRepository;
import com.reviveai.repository.CustomerRepository;
import com.reviveai.repository.PaymentRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import com.reviveai.service.DecisionMetrics;
import com.reviveai.service.RevenueRiskService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Seeds realistic demo data at startup: customers, payments, subscriptions,
 * and — for the named demo scenarios — fully-processed RecoveryCases.
 *
 * DESIGN NOTE: this deliberately does NOT call AgentService/AiClient. Doing
 * so would require a real LLM_API_KEY and network access just to boot the
 * app with demo data, and would make the seeded scenarios non-deterministic
 * (an LLM might not recommend OFFER_DISCOUNT at 20% every time, breaking
 * the "policy blocks it" demo moment). Instead, each scenario's AI decision
 * is hand-authored to match the spec's own worked examples, while every
 * OTHER part of the pipeline — RevenueRiskService, PolicyEngine, and
 * RecoveryActionExecutor — is the real, unmodified production code. The
 * metrics and policy verdicts shown in the demo are therefore genuine
 * output of the actual algorithms, not fabricated numbers.
 *
 * Only runs when reviveai.demo.enabled=true and the database is empty, so
 * it never touches a real production dataset and never double-seeds across
 * restarts.
 */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AgentActionRepository agentActionRepository;
    private final RevenueRiskService revenueRiskService;
    private final PolicyEngine policyEngine;
    private final RecoveryActionExecutor recoveryActionExecutor;
    private final ReviveAiProperties properties;

    @Override
    public void run(String... args) {
        if (!properties.getDemo().isEnabled()) {
            log.info("Demo mode is off — skipping seed data.");
            return;
        }
        if (customerRepository.count() > 0) {
            log.info("Seed data already present — skipping.");
            return;
        }

        log.info("Seeding ReviveAI demo data...");

        seedScenario1_HighPrioritySuccessfulRecovery();
        seedScenario2_PolicyBlockedDiscount();
        seedScenario3_HighValueRequiresApproval();
        seedScenario4_SubscriptionFailure();
        seedScenario5_CheckoutAbandonment();
        seedBackgroundCustomers();

        log.info("Seed data complete: {} customers, {} payments, {} subscriptions, {} recovery cases.",
                customerRepository.count(), paymentRepository.count(),
                subscriptionRepository.count(), recoveryCaseRepository.count());
    }

    // ---- Scenario 1: spec section 23 — high-priority, ends up RECOVERED via simulated retry ----
    private void seedScenario1_HighPrioritySuccessfulRecovery() {
        Customer rahul = customer("Rahul Sharma", "rahul.sharma@example.com",
                BigDecimal.valueOf(54990), 11, 1);
        payment(rahul, "seed_pay_rahul_hist", BigDecimal.valueOf(4999), PaymentStatus.SUCCESS, null, 0);
        Payment failedPayment = payment(rahul, "seed_pay_rahul_001", BigDecimal.valueOf(4999),
                PaymentStatus.FAILED, "Insufficient funds", 0);

        processPaymentCase(rahul, failedPayment, ActionType.RETRY_PAYMENT, 0.91,
                "The customer has a strong historical payment record and only one previous failure. "
                        + "The current failure appears temporary — retrying, alongside a reminder, is likely to succeed.",
                null, 24);
    }

    // ---- Scenario 2: spec section 24 — the central "policy blocks the AI" demo moment ----
    private void seedScenario2_PolicyBlockedDiscount() {
        Customer ananya = customer("Ananya Verma", "ananya.verma@example.com",
                BigDecimal.valueOf(28500), 6, 2);
        payment(ananya, "seed_pay_ananya_hist", BigDecimal.valueOf(4750), PaymentStatus.SUCCESS, null, 0);
        Payment failedPayment = payment(ananya, "seed_pay_ananya_001", BigDecimal.valueOf(9999),
                PaymentStatus.FAILED, "Card declined", 1);

        processPaymentCase(ananya, failedPayment, ActionType.OFFER_DISCOUNT, 0.72,
                "A limited-time discount may re-engage this price-sensitive customer, who has shown repeated "
                        + "purchase intent despite two recent declines.",
                20, null);
    }

    // ---- Scenario 3: high-value payment — REQUIRES_APPROVAL rather than BLOCKED ----
    private void seedScenario3_HighValueRequiresApproval() {
        Customer karan = customer("Karan Mehta", "karan.mehta@example.com",
                BigDecimal.valueOf(112000), 3, 1);
        payment(karan, "seed_pay_karan_hist", BigDecimal.valueOf(38000), PaymentStatus.SUCCESS, null, 0);
        Payment failedPayment = payment(karan, "seed_pay_karan_001", BigDecimal.valueOf(65000),
                PaymentStatus.FAILED, "Bank server down", 0);

        processPaymentCase(karan, failedPayment, ActionType.RETRY_PAYMENT, 0.68,
                "High historical value and a transient gateway-side failure suggest a retry is worth "
                        + "attempting, though the amount warrants human sign-off before proceeding.",
                null, 24);
    }

    // ---- Scenario 4: spec section 26 — subscription renewal failure ----
    private void seedScenario4_SubscriptionFailure() {
        Customer priya = customer("Priya Nair", "priya.nair@example.com",
                BigDecimal.valueOf(15992), 8, 0);
        payment(priya, "seed_pay_priya_hist", BigDecimal.valueOf(1999), PaymentStatus.SUCCESS, null, 0);

        Subscription subscription = Subscription.builder()
                .externalSubscriptionId("seed_sub_priya_001")
                .customer(priya)
                .amount(BigDecimal.valueOf(1999))
                .status(SubscriptionStatus.RENEWAL_FAILED)
                .renewalDate(LocalDate.now().minusDays(3))
                .failureCount(1)
                .build();
        subscription = subscriptionRepository.save(subscription);

        processSubscriptionCase(priya, subscription, ActionType.SEND_REMINDER, 0.80,
                "Reliable renewal history with only one recent miss — a reminder is likely sufficient "
                        + "without escalating to a discount offer.");
    }

    // ---- Scenario 5: spec section 25 — checkout abandonment (secondary flow) ----
    private void seedScenario5_CheckoutAbandonment() {
        Customer deepak = customer("Deepak Joshi", "deepak.joshi@example.com",
                BigDecimal.valueOf(9998), 2, 0);
        payment(deepak, "seed_pay_deepak_1", BigDecimal.valueOf(4999), PaymentStatus.SUCCESS, null, 0);
        payment(deepak, "seed_pay_deepak_2", BigDecimal.valueOf(4999), PaymentStatus.SUCCESS, null, 0);

        // No Payment/Subscription entity backs a checkout-abandonment event in
        // the current schema, so RevenueRiskService can't be called here (it
        // requires one of those). Metrics below are therefore hand-computed
        // rather than the deterministic algorithm's output — documented
        // clearly since every other seeded case uses the real calculation.
        BigDecimal revenueAtRisk = BigDecimal.valueOf(2499);
        BigDecimal recoveryProbability = BigDecimal.valueOf(0.55);
        BigDecimal expectedRecoveryValue = revenueAtRisk.multiply(recoveryProbability).setScale(2, RoundingMode.HALF_UP);

        RecoveryCase recoveryCase = RecoveryCase.builder()
                .customer(deepak)
                .revenueAtRisk(revenueAtRisk)
                .customerLifetimeValue(deepak.getLifetimeValue())
                .paymentSuccessRate(BigDecimal.ONE)
                .recoveryProbability(recoveryProbability)
                .expectedRecoveryValue(expectedRecoveryValue)
                .priority(Priority.MEDIUM)
                .recommendedAction(ActionType.CREATE_PAYMENT_LINK)
                .status(RecoveryCaseStatus.ACTION_PROPOSED)
                .build();
        recoveryCase = recoveryCaseRepository.save(recoveryCase);

        AgentAction agentAction = AgentAction.builder()
                .recoveryCase(recoveryCase)
                .actionType(ActionType.CREATE_PAYMENT_LINK)
                .reasoning("Checkout was not completed. Sending a fresh payment link is a low-friction way "
                        + "to recover this attempt without requiring a full re-purchase flow.")
                .confidence(0.60)
                .build();
        agentAction = agentActionRepository.save(agentAction);

        PolicyResult policyResult = policyEngine.evaluate(PolicyEvaluationContext.builder()
                .actionType(ActionType.CREATE_PAYMENT_LINK)
                .paymentAmount(revenueAtRisk)
                .retryCount(0)
                .build());
        agentAction.setPolicyStatus(policyResult.getStatus());
        agentAction.setPolicyReason(policyResult.getReason());
        agentActionRepository.save(agentAction);

        if (policyResult.isAllowed()) {
            recoveryCase.setFinalAction(ActionType.CREATE_PAYMENT_LINK);
            recoveryCase.setStatus(RecoveryCaseStatus.APPROVED);
            recoveryCaseRepository.save(recoveryCase);
            executeSeededCase(recoveryCase, agentAction, revenueAtRisk, "INR", null, recoveryProbability);
        } else {
            recoveryCase.setStatus(RecoveryCaseStatus.HUMAN_REVIEW);
            recoveryCaseRepository.save(recoveryCase);
        }
    }

    // ---- Background customers: fills out the dashboard with realistic variety ----
    private void seedBackgroundCustomers() {
        bgProcessedCase("Sneha Iyer", "sneha.iyer@example.com", 4, 0, BigDecimal.valueOf(15996), null, null, null, null);
        bgProcessedCase("Arjun Kapoor", "arjun.kapoor@example.com", 5, 1, BigDecimal.valueOf(24975),
                BigDecimal.valueOf(1499), "Insufficient funds", ActionType.RETRY_PAYMENT, 0.83);
        bgProcessedCase("Meera Pillai", "meera.pillai@example.com", 2, 3, BigDecimal.valueOf(3998),
                BigDecimal.valueOf(799), "Card declined", ActionType.RETRY_PAYMENT, 0.5, 3); // retryCount=3 -> BLOCKED
        bgProcessedCase("Vikram Rao", "vikram.rao@example.com", 7, 0, BigDecimal.valueOf(34993), null, null, null, null);
        bgProcessedCase("Ishita Desai", "ishita.desai@example.com", 1, 1, BigDecimal.valueOf(999),
                BigDecimal.valueOf(1999), "Card reported lost or stolen", ActionType.ESCALATE_TO_HUMAN, 0.55);
        bgProcessedCase("Rohan Malhotra", "rohan.malhotra@example.com", 3, 1, BigDecimal.valueOf(8997),
                BigDecimal.valueOf(2999), "Expired card", ActionType.RETRY_PAYMENT, 0.77);
        bgProcessedCase("Kavya Reddy", "kavya.reddy@example.com", 6, 2, BigDecimal.valueOf(29994),
                BigDecimal.valueOf(3499), "Insufficient funds", ActionType.SEND_REMINDER, 0.65);
        bgProcessedCase("Aditya Bose", "aditya.bose@example.com", 0, 2, BigDecimal.ZERO,
                BigDecimal.valueOf(599), "Card declined", ActionType.NO_ACTION, 0.35);
    }

    /** Overload for customers with no current failed payment (pure history, no case). */
    private void bgProcessedCase(String name, String email, int success, int failed, BigDecimal ltv,
                                  BigDecimal failedAmount, String failureReason, ActionType action, Double confidence) {
        bgProcessedCase(name, email, success, failed, ltv, failedAmount, failureReason, action, confidence, 0);
    }

    private void bgProcessedCase(String name, String email, int success, int failed, BigDecimal ltv,
                                  BigDecimal failedAmount, String failureReason, ActionType action,
                                  Double confidence, int retryCount) {
        String slug = email.substring(0, email.indexOf('@'));
        Customer customer = customer(name, email, ltv, success, failed);
        if (success > 0) {
            BigDecimal historicalAmount = ltv.compareTo(BigDecimal.ZERO) > 0
                    ? ltv.divide(BigDecimal.valueOf(Math.max(success, 1)), 2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(999);
            payment(customer, "seed_pay_" + slug + "_hist", historicalAmount, PaymentStatus.SUCCESS, null, 0);
        }
        if (failedAmount != null) {
            Payment failedPayment = payment(customer, "seed_pay_" + slug + "_001", failedAmount,
                    PaymentStatus.FAILED, failureReason, retryCount);
            processPaymentCase(customer, failedPayment, action, confidence, generateReasoning(action), null, null);
        }
    }

    private String generateReasoning(ActionType action) {
        return switch (action) {
            case RETRY_PAYMENT -> "Failure reason looks transient; retrying is likely to succeed.";
            case SEND_REMINDER -> "A gentle nudge should be enough to bring this customer back.";
            case ESCALATE_TO_HUMAN -> "This failure reason warrants manual review before any automated action.";
            case NO_ACTION -> "Limited payment history and repeated failures make automated recovery unlikely to help.";
            case CREATE_PAYMENT_LINK -> "A fresh payment link removes friction from completing this purchase.";
            case OFFER_DISCOUNT -> "A modest discount may be enough to recover this sale.";
        };
    }

    // ---- Shared pipeline: metrics (real) -> hand-authored AI decision -> policy (real) -> execution (real) ----

    private void processPaymentCase(Customer customer, Payment payment, ActionType action, double confidence,
                                     String reasoning, Integer discountPercent, Integer suggestedDelayHours) {
        DecisionMetrics metrics = revenueRiskService.calculateForPayment(payment);

        RecoveryCase recoveryCase = RecoveryCase.builder()
                .customer(customer)
                .payment(payment)
                .revenueAtRisk(metrics.getRevenueAtRisk())
                .customerLifetimeValue(metrics.getCustomerLifetimeValue())
                .paymentSuccessRate(metrics.getPaymentSuccessRate())
                .recoveryProbability(metrics.getRecoveryProbability())
                .expectedRecoveryValue(metrics.getExpectedRecoveryValue())
                .priority(metrics.getPriority())
                .recommendedAction(action)
                .status(RecoveryCaseStatus.ACTION_PROPOSED)
                .build();
        recoveryCase = recoveryCaseRepository.save(recoveryCase);

        AgentAction agentAction = AgentAction.builder()
                .recoveryCase(recoveryCase)
                .actionType(action)
                .reasoning(reasoning)
                .confidence(confidence)
                .discountPercent(discountPercent)
                .suggestedDelayHours(suggestedDelayHours)
                .build();
        agentAction = agentActionRepository.save(agentAction);

        PolicyResult policyResult = policyEngine.evaluate(PolicyEvaluationContext.builder()
                .actionType(action)
                .paymentAmount(metrics.getRevenueAtRisk())
                .retryCount(payment.getRetryCount() != null ? payment.getRetryCount() : 0)
                .discountPercent(discountPercent)
                .build());
        agentAction.setPolicyStatus(policyResult.getStatus());
        agentAction.setPolicyReason(policyResult.getReason());
        agentActionRepository.save(agentAction);

        if (policyResult.isAllowed()) {
            recoveryCase.setFinalAction(action);
            recoveryCase.setStatus(RecoveryCaseStatus.APPROVED);
            recoveryCaseRepository.save(recoveryCase);
            executeSeededCase(recoveryCase, agentAction, metrics.getRevenueAtRisk(), payment.getCurrency(),
                    payment.getId(), metrics.getRecoveryProbability());
        } else {
            recoveryCase.setStatus(RecoveryCaseStatus.HUMAN_REVIEW);
            recoveryCaseRepository.save(recoveryCase);
        }
    }

    private void processSubscriptionCase(Customer customer, Subscription subscription, ActionType action,
                                          double confidence, String reasoning) {
        DecisionMetrics metrics = revenueRiskService.calculateForSubscription(subscription);

        RecoveryCase recoveryCase = RecoveryCase.builder()
                .customer(customer)
                .subscription(subscription)
                .revenueAtRisk(metrics.getRevenueAtRisk())
                .customerLifetimeValue(metrics.getCustomerLifetimeValue())
                .paymentSuccessRate(metrics.getPaymentSuccessRate())
                .recoveryProbability(metrics.getRecoveryProbability())
                .expectedRecoveryValue(metrics.getExpectedRecoveryValue())
                .priority(metrics.getPriority())
                .recommendedAction(action)
                .status(RecoveryCaseStatus.ACTION_PROPOSED)
                .build();
        recoveryCase = recoveryCaseRepository.save(recoveryCase);

        AgentAction agentAction = AgentAction.builder()
                .recoveryCase(recoveryCase)
                .actionType(action)
                .reasoning(reasoning)
                .confidence(confidence)
                .build();
        agentAction = agentActionRepository.save(agentAction);

        PolicyResult policyResult = policyEngine.evaluate(PolicyEvaluationContext.builder()
                .actionType(action)
                .paymentAmount(metrics.getRevenueAtRisk())
                .retryCount(0)
                .build());
        agentAction.setPolicyStatus(policyResult.getStatus());
        agentAction.setPolicyReason(policyResult.getReason());
        agentActionRepository.save(agentAction);

        if (policyResult.isAllowed()) {
            recoveryCase.setFinalAction(action);
            recoveryCase.setStatus(RecoveryCaseStatus.APPROVED);
            recoveryCaseRepository.save(recoveryCase);
            executeSeededCase(recoveryCase, agentAction, metrics.getRevenueAtRisk(), "INR", null, metrics.getRecoveryProbability());
        } else {
            recoveryCase.setStatus(RecoveryCaseStatus.HUMAN_REVIEW);
            recoveryCaseRepository.save(recoveryCase);
        }
    }

    /**
     * Re-implements RecoveryService.executeInternal's logic (that method is
     * private to RecoveryService). Duplicated deliberately rather than
     * exposed: this is seed-script wiring, not part of the production
     * orchestration surface.
     */
    private void executeSeededCase(RecoveryCase recoveryCase, AgentAction agentAction, BigDecimal amount,
                                    String currency, UUID paymentId, BigDecimal recoveryProbability) {
        RecoveryExecutionContext ctx = RecoveryExecutionContext.builder()
                .recoveryCaseId(recoveryCase.getId())
                .paymentId(paymentId)
                .externalPaymentId(recoveryCase.getPayment() != null ? recoveryCase.getPayment().getExternalPaymentId() : null)
                .customerEmail(recoveryCase.getCustomer().getEmail())
                .amount(amount)
                .currency(currency)
                .actionType(agentAction.getActionType())
                .discountPercent(agentAction.getDiscountPercent())
                .suggestedDelayHours(agentAction.getSuggestedDelayHours())
                .recoveryProbability(recoveryProbability)
                .build();

        RecoveryResult result = recoveryActionExecutor.execute(ctx);

        agentAction.setExecutedAt(Instant.now());
        agentAction.setExecutionResultMessage(result.getMessage());
        agentAction.setExecutionSuccess(result.isSuccess());
        agentAction.setExecutionSimulated(result.isSimulated());
        agentActionRepository.save(agentAction);

        recoveryCase.setStatus(agentAction.getActionType() == ActionType.ESCALATE_TO_HUMAN
                ? RecoveryCaseStatus.HUMAN_REVIEW
                : RecoveryCaseStatus.EXECUTED);
        recoveryCase.setFinalAction(agentAction.getActionType());
        recoveryCaseRepository.save(recoveryCase);
    }

    // ---- Entity construction helpers ----

    private Customer customer(String name, String email, BigDecimal lifetimeValue, int successful, int failed) {
        return customerRepository.save(Customer.builder()
                .name(name)
                .email(email)
                .lifetimeValue(lifetimeValue)
                .successfulPayments(successful)
                .failedPayments(failed)
                .build());
    }

    private Payment payment(Customer customer, String externalId, BigDecimal amount, PaymentStatus status,
                             String failureReason, int retryCount) {
        return paymentRepository.save(Payment.builder()
                .externalPaymentId(externalId)
                .customer(customer)
                .amount(amount)
                .currency("INR")
                .status(status)
                .failureReason(failureReason)
                .retryCount(retryCount)
                .build());
    }
}
