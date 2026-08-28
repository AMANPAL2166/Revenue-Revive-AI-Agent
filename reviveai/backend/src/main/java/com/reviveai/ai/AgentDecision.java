package com.reviveai.ai;

import com.reviveai.enums.ActionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentDecision {
    private ActionType action;
    /** 0..1 */
    private double confidence;
    /** Concise, user-facing explanation only — never raw chain-of-thought. */
    private String reason;
    private Integer suggestedDelayHours;
    /** Only meaningful when action == OFFER_DISCOUNT; normalized to 0 otherwise. */
    private Integer discountPercent;

    /**
     * False only when AgentService had to fall back to a safe default
     * because the raw LLM output failed validation (malformed JSON,
     * unrecognized action, out-of-range confidence, etc). Downstream code
     * should treat a false value here as "this case needs a human," not as
     * a genuine AI recommendation.
     */
    @Builder.Default
    private boolean validAiResponse = true;
}
