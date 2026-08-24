package com.reviveai.repository;

import com.reviveai.entity.Payment;
import com.reviveai.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByExternalPaymentId(String externalPaymentId);

    boolean existsByExternalPaymentId(String externalPaymentId);

    Page<Payment> findByCustomerId(UUID customerId, Pageable pageable);

    List<Payment> findByStatus(PaymentStatus status);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
