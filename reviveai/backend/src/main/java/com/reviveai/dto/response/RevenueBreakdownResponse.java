package com.reviveai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RevenueBreakdownResponse {
    private BigDecimal failedPayments;
    /**
     * Always 0 in the current implementation: checkout-abandonment is a
     * secondary flow the spec explicitly defers (section 1), and no
     * CHECKOUT_ABANDONED event source exists yet. This is a real zero
     * because no such RecoveryCases exist yet, not a placeholder value.
     */
    private BigDecimal checkoutAbandonment;
    /**
     * Always 0 for the same reason: RevenueRiskService.calculateForSubscription
     * exists, but no webhook/orchestration path creates subscription-based
     * RecoveryCases yet.
     */
    private BigDecimal subscriptionFailures;
}
