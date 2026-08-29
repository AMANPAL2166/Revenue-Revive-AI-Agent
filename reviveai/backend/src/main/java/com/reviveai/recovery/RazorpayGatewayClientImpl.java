package com.reviveai.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.ReviveAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Real integration with Razorpay's Payment Links API
 * (https://razorpay.com/docs/api/payments/payment-links/). Only
 * instantiated by GatewayConfig when demo mode is off AND real credentials
 * are configured. Never fabricates a result if the API call fails —
 * failures propagate so RecoveryActionExecutor records a failed
 * RecoveryResult rather than silently pretending success, per the spec's
 * "do not fake successful payment results" rule.
 *
 * Not a @Component: instantiated explicitly by GatewayConfig so there is
 * never any ambiguity about which RazorpayGatewayClient bean is active.
 */
public class RazorpayGatewayClientImpl implements RazorpayGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGatewayClientImpl.class);
    private static final String PAYMENT_LINKS_URL = "https://api.razorpay.com/v1/payment_links";

    private final ReviveAiProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RazorpayGatewayClientImpl(ReviveAiProperties properties, WebClient webClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentLinkResult createPaymentLink(BigDecimal amount, String currency, String customerEmail, String description) {
        String keyId = properties.getRazorpay().getKeyId();
        String keySecret = properties.getRazorpay().getKeySecret();
        String basicAuth = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        long amountPaise = amount.movePointRight(2).longValueExact();

        Map<String, Object> body = Map.of(
                "amount", amountPaise,
                "currency", currency,
                "description", description,
                "customer", Map.of("email", customerEmail),
                "notify", Map.of("sms", false, "email", true)
        );

        try {
            String response = webClient.post()
                    .uri(PAYMENT_LINKS_URL)
                    .header("Authorization", "Basic " + basicAuth)
                    .header("content-type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return PaymentLinkResult.builder()
                    .id(root.path("id").asText())
                    .shortUrl(root.path("short_url").asText())
                    .simulated(false)
                    .build();
        } catch (Exception e) {
            log.error("Razorpay payment link creation failed: {}", e.getMessage());
            throw new IllegalStateException("Razorpay payment link creation failed", e);
        }
    }
}
