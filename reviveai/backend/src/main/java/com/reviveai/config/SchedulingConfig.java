package com.reviveai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Provides the TaskScheduler used for delayed recovery actions — e.g.
 * "retry after 24 hours", configurable down to seconds in demo mode via
 * reviveai.demo.retry-delay-seconds / reminder-delay-seconds. This is
 * plain Spring Scheduling (org.springframework.scheduling), not Quartz,
 * per the spec's engineering rules.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("reviveai-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
