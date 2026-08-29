package com.reviveai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.recovery.RazorpayGatewayClient;
import com.reviveai.recovery.RazorpayGatewayClientImpl;
import com.reviveai.recovery.SimulatedRazorpayGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Chooses between the real Razorpay adapter and the simulated one at
 * startup, based on demo mode and whether real credentials are configured.
 * This is the ONLY place that decision is made — RecoveryActionExecutor
 * depends solely on the RazorpayGatewayClient interface and never knows,
 * or needs to know, which implementation is active.
 */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public RazorpayGatewayClient razorpayGatewayClient(ReviveAiProperties properties, ObjectMapper objectMapper) {
        boolean hasRealCredentials =
                notBlank(properties.getRazorpay().getKeyId()) && notBlank(properties.getRazorpay().getKeySecret());

        if (!properties.getDemo().isEnabled() && hasRealCredentials) {
            log.info("RazorpayGatewayClient: using the REAL Razorpay adapter");
            return new RazorpayGatewayClientImpl(properties, WebClient.builder().build(), objectMapper);
        }

        log.info("RazorpayGatewayClient: using the SIMULATED adapter (demo mode {}, credentials {})",
                properties.getDemo().isEnabled() ? "on" : "off",
                hasRealCredentials ? "present" : "missing");
        return new SimulatedRazorpayGatewayClient();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
