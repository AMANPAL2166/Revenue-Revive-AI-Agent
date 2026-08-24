package com.reviveai.entity;

import com.reviveai.enums.ActionType;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The central object of the Revenue Recovery Engine. One RecoveryCase is
 * created per revenue-risk event (failed payment, subscription renewal
 * failure, or abandoned checkout) and carries it through:
 * decision metrics -> AI recommendation -> policy validation -> execution.
 *
 * Exactly one of paymentId / subscriptionId is expected to be set for the
 * MVP (checkout-abandonment cases may have neither, referencing only a
 * transient checkout context — left null here and tracked via
 * revenueAtRisk + metadata in a later phase if needed).
 */
@Entity
@Table(name = "recovery_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @NotNull
    @Column(name = "revenue_at_risk", nullable = false, precision = 15, scale = 2)
    private BigDecimal revenueAtRisk;

    @Column(name = "customer_lifetime_value", precision = 15, scale = 2)
    private BigDecimal customerLifetimeValue;

    @Column(name = "payment_success_rate", precision = 5, scale = 4)
    private BigDecimal paymentSuccessRate;

    @Column(name = "recovery_probability", precision = 5, scale = 4)
    private BigDecimal recoveryProbability;

    @Column(name = "expected_recovery_value", precision = 15, scale = 2)
    private BigDecimal expectedRecoveryValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", length = 30)
    private ActionType recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_action", length = 30)
    private ActionType finalAction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecoveryCaseStatus status = RecoveryCaseStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
