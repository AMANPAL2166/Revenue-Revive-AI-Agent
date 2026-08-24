package com.reviveai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ReviveAI - AI Revenue Recovery Agent.
 *
 * Central architectural principle:
 * AI recommends. Policy controls. Backend executes. Metrics measure.
 *
 * Scheduling is enabled here (used for delayed recovery actions such as
 * "retry after 24 hours" — configurable to seconds in demo mode).
 * Async is enabled to allow webhook ingestion to return quickly while
 * the Revenue Recovery Engine pipeline (metrics -> AI -> policy -> execution)
 * runs without blocking the webhook response.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ReviveAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviveAiApplication.class, args);
    }
}
