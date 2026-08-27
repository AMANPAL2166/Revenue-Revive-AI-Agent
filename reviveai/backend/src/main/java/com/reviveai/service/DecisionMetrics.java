package com.reviveai.service;

import com.reviveai.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output of RevenueRiskService: the deterministic, explainable decision
 * metrics for a single revenue-risk event. This is what gets copied onto
 * a RecoveryCase and what gets sent to the AI Agent as authoritative,
 * read-only context — the AI never recalculates or overrides any of these
 * values (see AgentService, Day 5).
 */
@Getter
@Builder
@AllArgsConstructor
public class DecisionMetrics {
    private BigDecimal revenueAtRisk;
    private BigDecimal customerLifetimeValue;
    /** 0..1 fraction, e.g. 0.9167 for 91.7%. */
    private BigDecimal paymentSuccessRate;
    /** 0..1 fraction, e.g. 0.86 for 86%. */
    private BigDecimal recoveryProbability;
    private BigDecimal expectedRecoveryValue;
    private Priority priority;

    /**
     * Human-readable, step-by-step signal breakdown for how
     * recoveryProbability and priority were derived. Intended for logs and
     * internal transparency — never forwarded verbatim as the AI's
     * user-facing reasoning (which is a separately generated, concise
     * explanation per the "no exposed chain-of-thought" rule).
     */
    private List<String> reasoningTrace;
}
