package com.reviveai.entity;

import com.reviveai.enums.ActionType;
import com.reviveai.enums.PolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a single AI-proposed action against a RecoveryCase, together with
 * the Policy Engine's verdict and (if executed) when it ran. A RecoveryCase
 * may accumulate multiple AgentActions over its lifetime (e.g. an initial
 * proposal that gets BLOCKED, followed by a human-approved alternative).
 */
@Entity
@Table(name = "agent_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ActionType actionType;

    /** Concise, user-facing explanation only — never raw chain-of-thought. */
    @Lob
    @Column(nullable = false)
    private String reasoning;

    @NotNull
    @Column(nullable = false)
    private Double confidence;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_status", nullable = false, length = 20)
    @Builder.Default
    private PolicyStatus policyStatus = PolicyStatus.PENDING_REVIEW;

    @Column(name = "executed_at")
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (this.proposedAt == null) {
            this.proposedAt = Instant.now();
        }
    }
}
