package com.reviveai.dto.response;

import com.reviveai.enums.ActionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRecommendationResponse {
    private ActionType action;
    private double confidence;
    /** Concise, user-facing explanation only — never raw chain-of-thought. */
    private String reasoning;
    /** Only meaningful when action == OFFER_DISCOUNT. */
    private Integer discountPercent;
    /** Only meaningful when action == RETRY_PAYMENT. */
    private Integer suggestedDelayHours;
}
