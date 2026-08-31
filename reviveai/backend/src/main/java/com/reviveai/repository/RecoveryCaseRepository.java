package com.reviveai.repository;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {
    @EntityGraph(attributePaths = {
            "customer",
            "customer.successfulPayments",
            "payment"
    })
    @Override
    Optional<RecoveryCase> findById(UUID id);

    @EntityGraph(attributePaths = {"customer"})
    Page<RecoveryCase> findByStatus(
            RecoveryCaseStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"customer"})
    Page<RecoveryCase> findByPriority(
            Priority priority,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"customer"})
    Page<RecoveryCase> findByStatusAndPriority(
            RecoveryCaseStatus status,
            Priority priority,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"customer"})
    Page<RecoveryCase> findAll(Pageable pageable);

    List<RecoveryCase> findByCustomerId(UUID customerId);

    List<RecoveryCase> findByStatusIn(
            Collection<RecoveryCaseStatus> statuses
    );

    long countByStatus(RecoveryCaseStatus status);

    Optional<RecoveryCase> findFirstByPaymentIdAndStatusIn(
            UUID paymentId,
            Collection<RecoveryCaseStatus> statuses
    );

    Optional<RecoveryCase> findFirstBySubscriptionIdAndStatusIn(
            UUID subscriptionId,
            Collection<RecoveryCaseStatus> statuses
    );

    boolean existsByCustomerIdAndStatus(
            UUID customerId,
            RecoveryCaseStatus status
    );
}