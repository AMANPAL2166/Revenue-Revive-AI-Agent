package com.reviveai.enums;

/**
 * The closed set of recovery actions the AI Agent is allowed to recommend.
 * The AI must select exactly one of these — free-form actions are rejected
 * during AgentService response validation.
 */
public enum ActionType {
    RETRY_PAYMENT,
    SEND_REMINDER,
    CREATE_PAYMENT_LINK,
    OFFER_DISCOUNT,
    ESCALATE_TO_HUMAN,
    NO_ACTION
}
