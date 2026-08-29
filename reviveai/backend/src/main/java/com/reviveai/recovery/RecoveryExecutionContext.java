package com.reviveai.recovery;

import com.reviveai.enums.ActionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class RecoveryExecutionContext {
    private UUID recoveryCaseId;
    private UUID paymentId;
    private String externalPaymentId;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    private ActionType actionType;
    /** Only meaningful when actionType == OFFER_DISCOUNT. */
    private Integer discountPercent;
    /** Only meaningful when actionType == RETRY_PAYMENT. */
    private Integer suggestedDelayHours;
    /** Biases the simulated retry outcome — higher probability, more likely to "succeed" in demo mode. */
    private BigDecimal recoveryProbability;
}
