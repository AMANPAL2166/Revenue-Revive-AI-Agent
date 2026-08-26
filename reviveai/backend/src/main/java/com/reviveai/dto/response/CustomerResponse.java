package com.reviveai.dto.response;

import com.reviveai.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private BigDecimal lifetimeValue;
    private Integer successfulPayments;
    private Integer failedPayments;
    private BigDecimal paymentSuccessRate;
    private Instant createdAt;
    private Instant updatedAt;

    public static CustomerResponse from(Customer c) {
        int total = c.getSuccessfulPayments() + c.getFailedPayments();
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(c.getSuccessfulPayments())
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .lifetimeValue(c.getLifetimeValue())
                .successfulPayments(c.getSuccessfulPayments())
                .failedPayments(c.getFailedPayments())
                .paymentSuccessRate(rate)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
