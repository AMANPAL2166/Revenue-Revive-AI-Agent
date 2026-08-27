package com.reviveai.policy;

import com.reviveai.config.ReviveAiProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Domain-facing view of a merchant's safety policy limits. For the MVP
 * there is exactly one merchant, bound directly from application
 * configuration (ReviveAiProperties.Policy). PolicyEngine only depends on
 * this class rather than on ReviveAiProperties directly — swapping to a
 * per-merchant, database-backed policy later means changing how
 * MerchantPolicy is constructed, not touching PolicyEngine's logic at all.
 */
@Getter
@Component
public class MerchantPolicy {

    private final int maxDiscountPercent;
    private final BigDecimal maxAutomaticRefundAmount;
    private final int maxPaymentRetries;
    private final BigDecimal highValuePaymentThreshold;
    private final boolean highValueActionsRequireHumanApproval;
    private final boolean refundRequiresHumanApproval;

    public MerchantPolicy(ReviveAiProperties properties) {
        ReviveAiProperties.Policy policy = properties.getPolicy();
        this.maxDiscountPercent = policy.getMaxDiscountPercent();
        this.maxAutomaticRefundAmount = policy.getMaxAutomaticRefundAmount();
        this.maxPaymentRetries = policy.getMaxPaymentRetries();
        this.highValuePaymentThreshold = policy.getHighValuePaymentThreshold();
        this.highValueActionsRequireHumanApproval = policy.isHighValueActionsRequireHumanApproval();
        this.refundRequiresHumanApproval = policy.isRefundRequiresHumanApproval();
    }
}
