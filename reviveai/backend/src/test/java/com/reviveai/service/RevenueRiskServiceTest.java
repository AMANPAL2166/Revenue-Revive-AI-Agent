package com.reviveai.service;

import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.enums.PaymentStatus;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.util.FailureReasonClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RevenueRiskServiceTest {

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    private RevenueRiskService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RevenueRiskService(recoveryCaseRepository, new FailureReasonClassifier());
        // Default: no prior successful recovery, unless a test overrides it.
        when(recoveryCaseRepository.existsByCustomerIdAndStatus(any(), any())).thenReturn(false);
    }

    private Customer customer(int successful, int failed, BigDecimal ltv) {
        return Customer.builder()
                .id(UUID.randomUUID())
                .name("Rahul Sharma")
                .email("rahul@example.com")
                .lifetimeValue(ltv)
                .successfulPayments(successful)
                .failedPayments(failed)
                .build();
    }

    private Payment payment(Customer customer, BigDecimal amount, String failureReason, int retryCount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .externalPaymentId("pay_test")
                .customer(customer)
                .amount(amount)
                .currency("INR")
                .status(PaymentStatus.FAILED)
                .failureReason(failureReason)
                .retryCount(retryCount)
                .updatedAt(Instant.now())
                .build();
    }

    // ---- Payment success rate ----

    @Test
    void successRate_noHistory_defaultsToZero() {
        Customer c = customer(0, 0, BigDecimal.ZERO);
        Payment p = payment(c, BigDecimal.valueOf(4999), "Insufficient funds", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getPaymentSuccessRate()).isEqualByComparingTo("0");
    }

    @Test
    void successRate_computesCorrectly() {
        // 11 successful / 12 total = 0.9167
        Customer c = customer(11, 1, BigDecimal.valueOf(54990));
        Payment p = payment(c, BigDecimal.valueOf(4999), "Insufficient funds", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getPaymentSuccessRate()).isEqualByComparingTo("0.9167");
    }

    @Test
    void successRate_divisionByZero_neverThrows() {
        Customer c = customer(0, 0, BigDecimal.ZERO);
        Payment p = payment(c, BigDecimal.TEN, null, 0);

        assertThat(service.calculateForPayment(p).getPaymentSuccessRate()).isNotNull();
    }

    // ---- Recovery probability ----

    @Test
    void recoveryProbability_strongHistoryRecoverableReason_scoresHigh() {
        // Base 50 + 15 (success rate >= 0.8) + 15 (recoverable reason) + 5 (low recent failures) = 85
        Customer c = customer(11, 1, BigDecimal.valueOf(54990));
        Payment p = payment(c, BigDecimal.valueOf(4999), "Insufficient funds", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getRecoveryProbability()).isEqualByComparingTo("0.85");
    }

    @Test
    void recoveryProbability_severeFailureReason_scoresLow() {
        // Base 50 + 15 (success rate) - 25 (severe reason) + 5 (low recent failures) = 45
        Customer c = customer(11, 1, BigDecimal.valueOf(54990));
        Payment p = payment(c, BigDecimal.valueOf(4999), "Card reported lost or stolen", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getRecoveryProbability()).isEqualByComparingTo("0.45");
    }

    @Test
    void recoveryProbability_previousSuccessfulRecovery_addsBonus() {
        when(recoveryCaseRepository.existsByCustomerIdAndStatus(any(), any())).thenReturn(true);
        Customer c = customer(2, 2, BigDecimal.valueOf(10000));
        Payment p = payment(c, BigDecimal.valueOf(999), null, 0);

        // Base 50 + 7 (success rate 2/4=0.5 >= 0.5) + 10 (previous recovery) = 67
        // (failedPayments=2 triggers neither the low-failure bonus [<=1]
        // nor the many-failures penalty [>=3] — it sits in the neutral zone.)
        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getRecoveryProbability()).isEqualByComparingTo("0.67");
    }

    @Test
    void recoveryProbability_manyFailuresAndHighRetries_isPenalizedAndClamped() {
        Customer c = customer(0, 6, BigDecimal.ZERO);
        Payment p = payment(c, BigDecimal.valueOf(999), "Card reported lost or stolen", 5);

        // Base 50 - 25 (severe) - 15 (many recent failures) - 15 (very high retry count) = -5 -> clamped to 0
        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getRecoveryProbability()).isEqualByComparingTo("0");
    }

    @Test
    void recoveryProbability_isNeverBelowZeroOrAboveOne() {
        Customer c = customer(50, 0, BigDecimal.valueOf(1000000));
        Payment p = payment(c, BigDecimal.valueOf(999), "Insufficient funds", 0);

        BigDecimal probability = service.calculateForPayment(p).getRecoveryProbability();

        assertThat(probability).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(probability).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    // ---- Expected recovery value ----

    @Test
    void expectedRecoveryValue_multipliesRevenueByProbability() {
        Customer c = customer(11, 1, BigDecimal.valueOf(54990));
        Payment p = payment(c, BigDecimal.valueOf(4999), "Insufficient funds", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        // revenueAtRisk (4999) * recoveryProbability (0.85) = 4249.15
        assertThat(metrics.getExpectedRecoveryValue()).isEqualByComparingTo("4249.15");
    }

    // ---- Priority ----

    @Test
    void priority_highRevenueHighLtvHighProbability_isHigh() {
        Customer c = customer(20, 1, BigDecimal.valueOf(60000));
        Payment p = payment(c, BigDecimal.valueOf(25000), "Insufficient funds", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void priority_lowRevenueLowLtvSevereReason_isLow() {
        Customer c = customer(0, 3, BigDecimal.ZERO);
        Payment p = payment(c, BigDecimal.valueOf(499), "Card reported lost or stolen", 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getPriority()).isEqualTo(Priority.LOW);
    }

    @Test
    void priority_moderateSignalsAcrossTheBoard_isMedium() {
        Customer c = customer(3, 1, BigDecimal.valueOf(18000));
        Payment p = payment(c, BigDecimal.valueOf(6000), null, 0);

        DecisionMetrics metrics = service.calculateForPayment(p);

        assertThat(metrics.getPriority()).isEqualTo(Priority.MEDIUM);
    }
}
