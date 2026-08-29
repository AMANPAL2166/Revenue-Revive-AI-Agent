package com.reviveai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.enums.PaymentStatus;
import com.reviveai.exception.ResourceNotFoundException;
import com.reviveai.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;

    /**
     * Creates or updates a Payment from a Razorpay `payload.payment.entity`
     * JSON node, and keeps the owning Customer's aggregate stats
     * (successfulPayments, failedPayments, lifetimeValue) in sync.
     *
     * Called at most once per unique webhook event — idempotency itself is
     * enforced upstream by WebhookProcessingService before this method is
     * ever reached, so no additional dedup logic is needed here.
     */
    @Transactional
    public Payment upsertFromRazorpayPayload(JsonNode paymentEntity) {
        String externalPaymentId = textOrNull(paymentEntity, "id");
        if (externalPaymentId == null) {
            throw new IllegalArgumentException("Razorpay payment payload missing 'id'");
        }

        String email = textOrNull(paymentEntity, "email");
        String contact = textOrNull(paymentEntity, "contact");
        String razorpayStatus = textOrNull(paymentEntity, "status");
        String errorDescription = textOrNull(paymentEntity, "error_description");
        long amountPaise = paymentEntity.hasNonNull("amount") ? paymentEntity.get("amount").asLong() : 0L;
        String currency = paymentEntity.hasNonNull("currency") ? paymentEntity.get("currency").asText() : "INR";

        BigDecimal amount = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        PaymentStatus newStatus = mapRazorpayStatus(razorpayStatus);

        Customer customer = customerService.findOrCreateByEmail(
                email != null ? email : externalPaymentId + "@unknown.reviveai",
                null,
                contact
        );

        Payment payment = paymentRepository.findByExternalPaymentId(externalPaymentId)
                .orElseGet(() -> Payment.builder()
                        .externalPaymentId(externalPaymentId)
                        .customer(customer)
                        .amount(amount)
                        .currency(currency)
                        .status(PaymentStatus.CREATED)
                        .retryCount(0)
                        .build());

        PaymentStatus previousStatus = payment.getStatus();
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(newStatus);
        payment.setFailureReason(errorDescription);

        Payment saved = paymentRepository.save(payment);
        applyCustomerStatsTransition(customer, previousStatus, newStatus, amount);

        log.info("Payment {} upserted: {} -> {}", externalPaymentId, previousStatus, newStatus);
        return saved;
    }

    /**
     * Used by RecoveryActionExecutor's RETRY_PAYMENT simulation adapter to
     * apply a retry outcome without going through the Razorpay webhook
     * payload shape (there's no real webhook payload for a simulated
     * event). Deliberately separate from upsertFromRazorpayPayload so this
     * class never has to fabricate a fake Razorpay JSON structure just to
     * reuse the same code path.
     */
    @Transactional
    public Payment applySimulatedOutcome(UUID paymentId, boolean succeeded, String failureReason) {
        Payment payment = getById(paymentId);
        PaymentStatus previousStatus = payment.getStatus();
        PaymentStatus newStatus = succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        payment.setStatus(newStatus);
        payment.setFailureReason(succeeded ? null : failureReason);
        Payment saved = paymentRepository.save(payment);

        applyCustomerStatsTransition(payment.getCustomer(), previousStatus, newStatus, payment.getAmount());

        log.info("Simulated retry outcome applied to payment {}: {} -> {}",
                payment.getExternalPaymentId(), previousStatus, newStatus);
        return saved;
    }

    /**
     * Only moves customer aggregate stats the first time a payment reaches
     * a given terminal state, to avoid double-counting if the same
     * (non-duplicate) payment goes through multiple distinct status
     * transitions on its way there.
     */
    private void applyCustomerStatsTransition(Customer customer, PaymentStatus previousStatus, PaymentStatus newStatus, BigDecimal amount) {
        if (previousStatus != PaymentStatus.SUCCESS && newStatus == PaymentStatus.SUCCESS) {
            customerService.recordSuccessfulPayment(customer, amount);
        } else if (previousStatus != PaymentStatus.FAILED && newStatus == PaymentStatus.FAILED) {
            customerService.recordFailedPayment(customer);
        }
    }

    @Transactional
    public Payment incrementRetryCount(UUID paymentId) {
        Payment payment = getById(paymentId);
        payment.setRetryCount(payment.getRetryCount() + 1);
        return paymentRepository.save(payment);
    }

    public Payment getById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    public Page<Payment> list(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    private PaymentStatus mapRazorpayStatus(String razorpayStatus) {
        if (razorpayStatus == null) {
            return PaymentStatus.CREATED;
        }
        return switch (razorpayStatus.toLowerCase()) {
            case "captured", "success" -> PaymentStatus.SUCCESS;
            case "failed" -> PaymentStatus.FAILED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "authorized", "created" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PENDING;
        };
    }

    private String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
