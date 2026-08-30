package com.reviveai.service;

import com.reviveai.dto.response.DashboardSummaryResponse;
import com.reviveai.dto.response.RevenueBreakdownResponse;
import com.reviveai.entity.Payment;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    // Mirrors DashboardService.STILL_OPEN_STATUSES exactly, since that field is private.
    private static final Set<RecoveryCaseStatus> STILL_OPEN_STATUSES = EnumSet.of(
            RecoveryCaseStatus.OPEN, RecoveryCaseStatus.ANALYZING, RecoveryCaseStatus.ACTION_PROPOSED,
            RecoveryCaseStatus.HUMAN_REVIEW, RecoveryCaseStatus.APPROVED, RecoveryCaseStatus.EXECUTED
    );
    private static final Set<RecoveryCaseStatus> RECOVERED_ONLY = EnumSet.of(RecoveryCaseStatus.RECOVERED);

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardService(recoveryCaseRepository);
    }

    private RecoveryCase caseWith(RecoveryCaseStatus status, BigDecimal revenueAtRisk, BigDecimal expectedRecoveryValue) {
        return RecoveryCase.builder()
                .status(status)
                .revenueAtRisk(revenueAtRisk)
                .expectedRecoveryValue(expectedRecoveryValue)
                .build();
    }

    @Test
    void summary_sumsOpenCasesForAtRiskAndRecoverable() {
        List<RecoveryCase> open = List.of(
                caseWith(RecoveryCaseStatus.OPEN, BigDecimal.valueOf(4999), BigDecimal.valueOf(4249.15)),
                caseWith(RecoveryCaseStatus.HUMAN_REVIEW, BigDecimal.valueOf(9999), BigDecimal.valueOf(1999.80))
        );
        List<RecoveryCase> recovered = List.of(
                caseWith(RecoveryCaseStatus.RECOVERED, BigDecimal.valueOf(2999), null)
        );

        when(recoveryCaseRepository.findByStatusIn(eq(STILL_OPEN_STATUSES))).thenReturn(open);
        when(recoveryCaseRepository.findByStatusIn(eq(RECOVERED_ONLY))).thenReturn(recovered);
        when(recoveryCaseRepository.countByStatus(RecoveryCaseStatus.FAILED)).thenReturn(1L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.getRevenueAtRisk()).isEqualByComparingTo("14998");
        assertThat(summary.getRecoverableRevenue()).isEqualByComparingTo("6248.95");
        assertThat(summary.getRecoveredRevenue()).isEqualByComparingTo("2999");
        // 1 recovered / (1 recovered + 1 failed) = 50.0%
        assertThat(summary.getRecoveryRatePercent()).isEqualTo(50.0);
    }

    @Test
    void summary_noResolvedCasesYet_recoveryRateIsZeroNotDivideByZeroError() {
        when(recoveryCaseRepository.findByStatusIn(any())).thenReturn(List.of());
        when(recoveryCaseRepository.countByStatus(RecoveryCaseStatus.FAILED)).thenReturn(0L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.getRecoveryRatePercent()).isEqualTo(0.0);
        assertThat(summary.getRevenueAtRisk()).isEqualByComparingTo("0");
    }

    @Test
    void summary_nullExpectedRecoveryValue_doesNotBreakSum() {
        List<RecoveryCase> open = List.of(
                caseWith(RecoveryCaseStatus.OPEN, BigDecimal.valueOf(1000), null)
        );
        when(recoveryCaseRepository.findByStatusIn(eq(STILL_OPEN_STATUSES))).thenReturn(open);
        when(recoveryCaseRepository.findByStatusIn(eq(RECOVERED_ONLY))).thenReturn(List.of());
        when(recoveryCaseRepository.countByStatus(RecoveryCaseStatus.FAILED)).thenReturn(0L);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.getRecoverableRevenue()).isEqualByComparingTo("0");
        assertThat(summary.getRevenueAtRisk()).isEqualByComparingTo("1000");
    }

    @Test
    void revenueBreakdown_bucketsByPaymentVsSubscriptionVsAbandonment() {
        RecoveryCase paymentCase = RecoveryCase.builder()
                .status(RecoveryCaseStatus.OPEN)
                .revenueAtRisk(BigDecimal.valueOf(1420))
                .payment(Payment.builder().build())
                .build();
        RecoveryCase subscriptionCase = RecoveryCase.builder()
                .status(RecoveryCaseStatus.OPEN)
                .revenueAtRisk(BigDecimal.valueOf(980))
                .subscription(Subscription.builder().build())
                .build();

        when(recoveryCaseRepository.findByStatusIn(any())).thenReturn(List.of(paymentCase, subscriptionCase));

        RevenueBreakdownResponse breakdown = dashboardService.getRevenueBreakdown();

        assertThat(breakdown.getFailedPayments()).isEqualByComparingTo("1420");
        assertThat(breakdown.getSubscriptionFailures()).isEqualByComparingTo("980");
        assertThat(breakdown.getCheckoutAbandonment()).isEqualByComparingTo("0");
    }
}
