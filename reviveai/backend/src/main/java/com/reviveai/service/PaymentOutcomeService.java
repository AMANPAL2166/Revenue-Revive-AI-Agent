package com.reviveai.service;

import com.reviveai.entity.Payment;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Closes the loop between a payment reaching a terminal state and any
 * RecoveryCase that was tracking it: a payment that finally succeeds marks
 * its EXECUTED case RECOVERED; one that fails again marks it FAILED.
 *
 * Deliberately a small, standalone component rather than a method on
 * RecoveryService. Both WebhookProcessingService (real Razorpay webhooks)
 * and RecoveryActionExecutor's scheduled retry simulation need this
 * behavior, but RecoveryService already depends on RecoveryActionExecutor
 * — if RecoveryActionExecutor depended back on RecoveryService (or on
 * WebhookProcessingService, which depends on RecoveryService), that would
 * be a circular bean dependency. Routing both callers through this
 * dependency-light service instead keeps the object graph acyclic.
 */
@Service
@RequiredArgsConstructor
public class PaymentOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeService.class);

    private final RecoveryCaseRepository recoveryCaseRepository;

    @Transactional
    public void handlePaymentSuccess(Payment payment) {
        recoveryCaseRepository
                .findFirstByPaymentIdAndStatusIn(payment.getId(), EnumSet.of(RecoveryCaseStatus.EXECUTED))
                .ifPresent(recoveryCase -> {
                    recoveryCase.setStatus(RecoveryCaseStatus.RECOVERED);
                    recoveryCaseRepository.save(recoveryCase);
                    log.info("RecoveryCase {} marked RECOVERED (payment {} succeeded).",
                            recoveryCase.getId(), payment.getExternalPaymentId());
                });
    }

    @Transactional
    public void handlePaymentFailure(Payment payment) {
        recoveryCaseRepository
                .findFirstByPaymentIdAndStatusIn(payment.getId(), EnumSet.of(RecoveryCaseStatus.EXECUTED))
                .ifPresent(recoveryCase -> {
                    recoveryCase.setStatus(RecoveryCaseStatus.FAILED);
                    recoveryCaseRepository.save(recoveryCase);
                    log.info("RecoveryCase {} marked FAILED (payment {} failed again).",
                            recoveryCase.getId(), payment.getExternalPaymentId());
                });
    }
}
