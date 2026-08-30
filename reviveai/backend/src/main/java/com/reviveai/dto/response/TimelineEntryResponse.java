package com.reviveai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TimelineEntryResponse {
    private String label;
    private Instant timestamp;
    private String description;
}
