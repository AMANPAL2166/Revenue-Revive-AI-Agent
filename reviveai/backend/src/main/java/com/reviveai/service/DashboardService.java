package com.reviveai.service;

import com.reviveai.dto.response.DashboardSummaryResponse;
import com.reviveai.dto.response.RevenueBreakdownResponse;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * All figures here are computed from RecoveryCase rows at request time —
 * nothing is hardcoded or cached-and-stale, per the spec's "do not
 * hardcode dashboard metrics" rule.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** A case still counts toward "at risk" until it resolves one way or the other. */
    private static final Set<RecoveryCaseStatus> STILL_OPEN_STATUSES = EnumSet.of(
            RecoveryCaseStatus.OPEN, RecoveryCaseStatus.ANALYZING, RecoveryCaseStatus.ACTION_PROPOSED,
            RecoveryCaseStatus.HUMAN_REVIEW, RecoveryCaseStatus.APPROVED, RecoveryCaseStatus.EXECUTED
    );

    private final RecoveryCaseRepository recoveryCaseRepository;

    public DashboardSummaryResponse getSummary() {
        List<RecoveryCase> open = recoveryCaseRepository.findByStatusIn(STILL_OPEN_STATUSES);
        List<RecoveryCase> recovered = recoveryCaseRepository.findByStatusIn(EnumSet.of(RecoveryCaseStatus.RECOVERED));
        long recoveredCount = recovered.size();
        long failedCount = recoveryCaseRepository.countByStatus(RecoveryCaseStatus.FAILED);

        BigDecimal revenueAtRisk = sum(open, RecoveryCase::getRevenueAtRisk);
        BigDecimal recoverableRevenue = sum(open, RecoveryCase::getExpectedRecoveryValue);
        BigDecimal recoveredRevenue = sum(recovered, RecoveryCase::getRevenueAtRisk);

        long resolvedTotal = recoveredCount + failedCount;
        double recoveryRatePercent = resolvedTotal == 0
                ? 0.0
                : BigDecimal.valueOf(recoveredCount)
                    .divide(BigDecimal.valueOf(resolvedTotal), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();

        return DashboardSummaryResponse.builder()
                .revenueAtRisk(revenueAtRisk)
                .recoverableRevenue(recoverableRevenue)
                .recoveredRevenue(recoveredRevenue)
                .recoveryRatePercent(recoveryRatePercent)
                .build();
    }

    public RevenueBreakdownResponse getRevenueBreakdown() {
        List<RecoveryCase> open = recoveryCaseRepository.findByStatusIn(STILL_OPEN_STATUSES);

        BigDecimal failedPayments = sum(
                open.stream().filter(c -> c.getPayment() != null).toList(), RecoveryCase::getRevenueAtRisk);
        BigDecimal subscriptionFailures = sum(
                open.stream().filter(c -> c.getSubscription() != null).toList(), RecoveryCase::getRevenueAtRisk);

        return RevenueBreakdownResponse.builder()
                .failedPayments(failedPayments)
                .checkoutAbandonment(BigDecimal.ZERO) // not yet implemented — see field javadoc
                .subscriptionFailures(subscriptionFailures)
                .build();
    }

    private BigDecimal sum(List<RecoveryCase> cases, Function<RecoveryCase, BigDecimal> extractor) {
        return cases.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
