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
}
