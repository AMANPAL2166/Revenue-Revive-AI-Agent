package com.reviveai.dto.response;

import com.reviveai.entity.Payment;
import com.reviveai.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private String externalPaymentId;
    private UUID customerId;
    private String customerName;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String failureReason;
    private Integer retryCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .externalPaymentId(p.getExternalPaymentId())
                .customerId(p.getCustomer().getId())
                .customerName(p.getCustomer().getName())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .failureReason(p.getFailureReason())
                .retryCount(p.getRetryCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
