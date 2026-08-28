package com.reviveai.exception;

/**
 * Thrown internally by AgentService when the raw LLM response is malformed,
 * missing required fields, or contains out-of-range values. Never
 * propagates to the controller layer — AgentService always catches this
 * and falls back to a safe ESCALATE_TO_HUMAN decision instead.
 */
public class InvalidAiResponseException extends RuntimeException {
    public InvalidAiResponseException(String message) {
        super(message);
    }
}
