package com.reviveai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardSummaryResponse {
    /** Sum of revenueAtRisk across all still-open RecoveryCases. */
    private BigDecimal revenueAtRisk;
    /** Sum of expectedRecoveryValue across all still-open RecoveryCases — the probabilistic estimate. */
    private BigDecimal recoverableRevenue;
    /** Sum of revenueAtRisk across RECOVERED RecoveryCases. */
    private BigDecimal recoveredRevenue;
    /** recoveredCount / (recoveredCount + failedCount) * 100, safely 0 when there's no resolved history yet. */
    private double recoveryRatePercent;
}
