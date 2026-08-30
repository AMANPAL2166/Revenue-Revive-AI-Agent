package com.reviveai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Typed access to the `reviveai.*` configuration block, so services depend
 * on a single injectable bean instead of scattering @Value("${...}")
 * annotations across the codebase. Backs both the demo-mode delay switches
 * and the merchant Policy Engine limits (Day 4).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reviveai")
public class ReviveAiProperties {

    private final Demo demo = new Demo();
    private final Razorpay razorpay = new Razorpay();
    private final Llm llm = new Llm();
    private final Policy policy = new Policy();
    private final Frontend frontend = new Frontend();

    @Getter
    @Setter
    public static class Demo {
        private boolean enabled = true;
        private int retryDelaySeconds = 30;
        private int reminderDelaySeconds = 20;
    }

    @Getter
    @Setter
    public static class Razorpay {
        private String keyId;
        private String keySecret;
        private String webhookSecret;
    }

    @Getter
    @Setter
    public static class Llm {
        private String apiKey;
        private String model;
    }

    @Getter
    @Setter
    public static class Policy {
        private int maxDiscountPercent = 10;
        private BigDecimal maxAutomaticRefundAmount = BigDecimal.valueOf(2000);
        private int maxPaymentRetries = 3;
        private BigDecimal highValuePaymentThreshold = BigDecimal.valueOf(50000);
        private boolean highValueActionsRequireHumanApproval = true;
        private boolean refundRequiresHumanApproval = true;
    }

    @Getter
    @Setter
    public static class Frontend {
        /** The Vite dev server origin by default; override via CORS_ALLOWED_ORIGIN in production. */
        private String origin = "http://localhost:5173";
    }
}
