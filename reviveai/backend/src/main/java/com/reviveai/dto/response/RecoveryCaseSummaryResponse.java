package com.reviveai.dto.response;

import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RecoveryCaseSummaryResponse {
    private UUID id;
    private String customerName;
    private BigDecimal amount;
    private Priority priority;
    /** 0-100 integer percent, for direct display (spec example: "86%"). */
    private Integer recoveryProbabilityPercent;
    private ActionType recommendedAction;
    private ActionType finalAction;
    private RecoveryCaseStatus status;
    private Instant createdAt;

    public static RecoveryCaseSummaryResponse from(RecoveryCase c) {
        return RecoveryCaseSummaryResponse.builder()
                .id(c.getId())
                .customerName(c.getCustomer() != null ? c.getCustomer().getName() : null)
                .amount(c.getRevenueAtRisk())
                .priority(c.getPriority())
                .recoveryProbabilityPercent(toPercent(c.getRecoveryProbability()))
                .recommendedAction(c.getRecommendedAction())
                .finalAction(c.getFinalAction())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private static Integer toPercent(BigDecimal fraction) {
        if (fraction == null) {
            return null;
        }
        return fraction.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
