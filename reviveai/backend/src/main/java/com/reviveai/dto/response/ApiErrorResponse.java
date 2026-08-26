package com.reviveai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error shape returned by GlobalExceptionHandler. Never carries a
 * stack trace or raw exception message that could leak internals.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private String error;
    private String message;
    private Instant timestamp;
    private List<String> details;
}
