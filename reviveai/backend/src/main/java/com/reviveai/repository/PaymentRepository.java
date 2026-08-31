package com.reviveai.repository;

import com.reviveai.entity.Payment;
import com.reviveai.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"customer"})
    Optional<Payment> findByExternalPaymentId(String externalPaymentId);

    boolean existsByExternalPaymentId(String externalPaymentId);

    @EntityGraph(attributePaths = {"customer"})
    Page<Payment> findByCustomerId(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    List<Payment> findByStatus(PaymentStatus status);

    @EntityGraph(attributePaths = {"customer"})
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Payment> findAll(Pageable pageable);
}