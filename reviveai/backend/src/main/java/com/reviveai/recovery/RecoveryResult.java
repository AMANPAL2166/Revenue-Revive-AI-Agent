package com.reviveai.recovery;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class RecoveryResult {
    private boolean success;
    private String message;
    private Instant executedAt;
    /** True when this result came from a simulated adapter rather than a real Razorpay call. */
    private boolean simulated;
    /** e.g. a payment link id — null when the action has no external reference (SEND_REMINDER, ESCALATE_TO_HUMAN, NO_ACTION). */
    private String externalReference;
}
