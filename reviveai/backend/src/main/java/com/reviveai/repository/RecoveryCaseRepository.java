package com.reviveai.repository;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.RecoveryCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {

    Page<RecoveryCase> findByStatus(RecoveryCaseStatus status, Pageable pageable);

    List<RecoveryCase> findByCustomerId(UUID customerId);

    /**
     * Used by RecoveryService before creating a new case for a payment, to
     * avoid opening a second concurrent case for the same failed payment
     * when a webhook is (safely, non-duplicate) re-delivered for a related
     * event, or when a retry itself fails again.
     */
    Optional<RecoveryCase> findFirstByPaymentIdAndStatusIn(UUID paymentId, Collection<RecoveryCaseStatus> statuses);

    Optional<RecoveryCase> findFirstBySubscriptionIdAndStatusIn(UUID subscriptionId, Collection<RecoveryCaseStatus> statuses);
}
