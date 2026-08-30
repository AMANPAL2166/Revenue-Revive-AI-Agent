package com.reviveai.dto.response;

import com.reviveai.entity.AgentAction;
import com.reviveai.enums.ActionType;
import com.reviveai.enums.PolicyStatus;
import com.reviveai.enums.RecoveryCaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AgentActivityResponse {
    private UUID recoveryCaseId;
    private Instant time;
    private String customerName;
    private ActionType aiDecision;
    private double confidence;
    private PolicyStatus policyDecision;
    private RecoveryCaseStatus caseStatus;

    public static AgentActivityResponse from(AgentAction action) {
        return AgentActivityResponse.builder()
                .recoveryCaseId(action.getRecoveryCase().getId())
                .time(action.getProposedAt())
                .customerName(action.getRecoveryCase().getCustomer() != null
                        ? action.getRecoveryCase().getCustomer().getName() : null)
                .aiDecision(action.getActionType())
                .confidence(action.getConfidence())
                .policyDecision(action.getPolicyStatus())
                .caseStatus(action.getRecoveryCase().getStatus())
                .build();
    }
}
