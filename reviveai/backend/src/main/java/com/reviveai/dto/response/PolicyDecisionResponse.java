package com.reviveai.dto.response;

import com.reviveai.enums.PolicyStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyDecisionResponse {
    private PolicyStatus status;
    private String reason;
}
