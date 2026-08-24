package com.reviveai.enums;

/**
 * Outcome of PolicyEngine evaluation for a proposed AgentAction.
 *
 * PENDING_REVIEW    -> not yet evaluated
 * ALLOWED           -> action may proceed straight to execution
 * BLOCKED           -> action is rejected outright (e.g. discount exceeds limit)
 * REQUIRES_APPROVAL -> action is plausible but merchant policy mandates a human
 *                      sign-off first (e.g. refunds, high-value payments)
 */
public enum PolicyStatus {
    PENDING_REVIEW,
    ALLOWED,
    BLOCKED,
    REQUIRES_APPROVAL
}
