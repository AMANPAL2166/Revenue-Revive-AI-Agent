package com.reviveai.recovery;

import com.reviveai.config.ReviveAiProperties;
import com.reviveai.entity.Payment;
import com.reviveai.service.NotificationService;
import com.reviveai.service.PaymentOutcomeService;
import com.reviveai.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes a single, already-policy-approved recovery action. Never
 * decides *whether* an action is allowed (that's PolicyEngine's job) —
 * only *how* to carry it out.
 *
 * RETRY_PAYMENT and SEND_REMINDER are delayed via Spring's TaskScheduler
 * (see SchedulingConfig), with the delay controlled by
 * reviveai.demo.retry-delay-seconds / reminder-delay-seconds so the
 * buildathon demo doesn't require waiting real hours.
 */
@Service
@RequiredArgsConstructor
public class RecoveryActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(RecoveryActionExecutor.class);
    private static final long DEFAULT_RETRY_DELAY_HOURS = 24;
    private static final long DEFAULT_REMINDER_DELAY_HOURS = 12;

    private final RazorpayGatewayClient gatewayClient;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final PaymentOutcomeService paymentOutcomeService;
    private final TaskScheduler taskScheduler;
    private final ReviveAiProperties properties;

    public RecoveryResult execute(RecoveryExecutionContext ctx) {
        return switch (ctx.getActionType()) {
            case RETRY_PAYMENT -> executeRetryPayment(ctx);
            case SEND_REMINDER -> executeSendReminder(ctx);
            case CREATE_PAYMENT_LINK -> executeCreatePaymentLink(ctx);
            case OFFER_DISCOUNT -> executeOfferDiscount(ctx);
            case ESCALATE_TO_HUMAN -> executeEscalateToHuman();
            case NO_ACTION -> executeNoAction();
        };
    }

    private RecoveryResult executeRetryPayment(RecoveryExecutionContext ctx) {
        paymentService.incrementRetryCount(ctx.getPaymentId());

        long delaySeconds = resolveRetryDelaySeconds(ctx.getSuggestedDelayHours());
        taskScheduler.schedule(() -> simulateRetryOutcome(ctx), Instant.now().plusSeconds(delaySeconds));

        log.info("Retry scheduled for payment {} in {}s", ctx.getExternalPaymentId(), delaySeconds);
        return RecoveryResult.builder()
                .success(true)
                .message("Retry scheduled in " + delaySeconds + " seconds.")
                .executedAt(Instant.now())
                .simulated(true)
                .build();
    }

    /**
     * Simulates the outcome of a retried payment attempt. There is no real
     * Razorpay "retry this payment" API to call, so this — like
     * SimulatedRazorpayGatewayClient — is a clearly-separated demo
     * adapter, never presented as a real gateway callback. The outcome is
     * weighted by the case's own recoveryProbability rather than a plain
     * coin flip, so higher-probability cases really do recover more often
     * in the demo.
     */
    private void simulateRetryOutcome(RecoveryExecutionContext ctx) {
        try {
            double probability = ctx.getRecoveryProbability() != null
                    ? ctx.getRecoveryProbability().doubleValue()
                    : 0.5;
            boolean succeeds = ThreadLocalRandom.current().nextDouble() < probability;

            Payment updated = paymentService.applySimulatedOutcome(
                    ctx.getPaymentId(), succeeds, "Insufficient funds (simulated retry failure)");

            if (succeeds) {
                paymentOutcomeService.handlePaymentSuccess(updated);
            } else {
                paymentOutcomeService.handlePaymentFailure(updated);
            }

            log.info("[SIMULATED RETRY] Payment {} -> {}", ctx.getExternalPaymentId(), succeeds ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            log.error("Simulated retry outcome failed for payment {}: {}", ctx.getExternalPaymentId(), e.getMessage(), e);
        }
    }

    private long resolveRetryDelaySeconds(Integer suggestedDelayHours) {
        if (properties.getDemo().isEnabled()) {
            return properties.getDemo().getRetryDelaySeconds();
        }
        long hours = suggestedDelayHours != null ? suggestedDelayHours : DEFAULT_RETRY_DELAY_HOURS;
        return hours * 3600L;
    }

    private RecoveryResult executeSendReminder(RecoveryExecutionContext ctx) {
        long delaySeconds = properties.getDemo().isEnabled()
                ? properties.getDemo().getReminderDelaySeconds()
                : DEFAULT_REMINDER_DELAY_HOURS * 3600L;

        taskScheduler.schedule(() -> {
            String message = "Your payment of " + ctx.getAmount() + " " + ctx.getCurrency() + " needs attention.";
            notificationService.sendReminder(ctx.getCustomerEmail(), message);
        }, Instant.now().plusSeconds(delaySeconds));

        return RecoveryResult.builder()
                .success(true)
                .message("Reminder scheduled in " + delaySeconds + " seconds.")
                .executedAt(Instant.now())
                .simulated(true)
                .build();
    }

    private RecoveryResult executeCreatePaymentLink(RecoveryExecutionContext ctx) {
        PaymentLinkResult link = gatewayClient.createPaymentLink(
                ctx.getAmount(), ctx.getCurrency(), ctx.getCustomerEmail(), "ReviveAI recovery payment link");

        return RecoveryResult.builder()
                .success(true)
                .message("Payment link created: " + link.getShortUrl())
                .executedAt(Instant.now())
                .simulated(link.isSimulated())
                .externalReference(link.getId())
                .build();
    }

    private RecoveryResult executeOfferDiscount(RecoveryExecutionContext ctx) {
        int discountPercent = ctx.getDiscountPercent() != null ? ctx.getDiscountPercent() : 0;
        BigDecimal discountedAmount = ctx.getAmount()
                .multiply(BigDecimal.valueOf(100 - discountPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        PaymentLinkResult link = gatewayClient.createPaymentLink(
                discountedAmount, ctx.getCurrency(), ctx.getCustomerEmail(),
                "ReviveAI recovery offer: " + discountPercent + "% off");

        return RecoveryResult.builder()
                .success(true)
                .message(discountPercent + "% discount offer created: " + link.getShortUrl())
                .executedAt(Instant.now())
                .simulated(link.isSimulated())
                .externalReference(link.getId())
                .build();
    }

    private RecoveryResult executeEscalateToHuman() {
        return RecoveryResult.builder()
                .success(true)
                .message("Case escalated for human review.")
                .executedAt(Instant.now())
                .simulated(false)
                .build();
    }

    private RecoveryResult executeNoAction() {
        return RecoveryResult.builder()
                .success(true)
                .message("No action taken for this case.")
                .executedAt(Instant.now())
                .simulated(false)
                .build();
    }
}
