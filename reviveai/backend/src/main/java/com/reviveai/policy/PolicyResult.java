package com.reviveai.policy;

import com.reviveai.enums.PolicyStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyResult {
    private boolean allowed;
    private PolicyStatus status;
    private String reason;

    public static PolicyResult allowed(String reason) {
        return PolicyResult.builder().allowed(true).status(PolicyStatus.ALLOWED).reason(reason).build();
    }

    public static PolicyResult blocked(String reason) {
        return PolicyResult.builder().allowed(false).status(PolicyStatus.BLOCKED).reason(reason).build();
    }

    public static PolicyResult requiresApproval(String reason) {
        return PolicyResult.builder().allowed(false).status(PolicyStatus.REQUIRES_APPROVAL).reason(reason).build();
    }
}
