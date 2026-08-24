package com.reviveai.enums;

/**
 * RecoveryCase lifecycle.
 *
 * OPEN            -> case created from a revenue-risk event
 * ANALYZING       -> decision metrics being calculated / AI being consulted
 * ACTION_PROPOSED -> AI has recommended an action, pending policy check
 * BLOCKED         -> Policy Engine rejected the AI-recommended action outright
 * HUMAN_REVIEW    -> Policy Engine requires manual approval (or action was BLOCKED)
 * APPROVED        -> action cleared for execution (auto-allowed or human-approved)
 * EXECUTED        -> RecoveryActionExecutor has run the action
 * RECOVERED       -> the at-risk revenue was actually recovered
 * FAILED          -> the recovery attempt did not succeed
 */
public enum RecoveryCaseStatus {
    OPEN,
    ANALYZING,
    ACTION_PROPOSED,
    BLOCKED,
    HUMAN_REVIEW,
    APPROVED,
    EXECUTED,
    RECOVERED,
    FAILED
}
