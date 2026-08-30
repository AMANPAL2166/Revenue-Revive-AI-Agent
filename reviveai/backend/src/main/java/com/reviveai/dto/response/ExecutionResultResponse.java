package com.reviveai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ExecutionResultResponse {
    private boolean success;
    private String message;
    private boolean simulated;
    private Instant executedAt;
}
