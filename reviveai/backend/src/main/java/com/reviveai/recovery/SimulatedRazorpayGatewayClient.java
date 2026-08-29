package com.reviveai.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Demo/test adapter, used whenever real Razorpay credentials aren't
 * configured or DEMO_MODE=true. Every field it returns is unambiguously
 * marked as simulated: the generated id is prefixed "sim_" (never
 * "plink_" the way a real Razorpay payment link id would be), and the URL
 * points at a clearly fake domain — so nothing here can be mistaken for a
 * real Razorpay response in logs, in the database, or in the UI.
 *
 * Not a @Component: instantiated explicitly by GatewayConfig alongside
 * RazorpayGatewayClientImpl, so exactly one adapter is ever wired in.
 */
public class SimulatedRazorpayGatewayClient implements RazorpayGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedRazorpayGatewayClient.class);

    @Override
    public PaymentLinkResult createPaymentLink(BigDecimal amount, String currency, String customerEmail, String description) {
        String id = "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String url = "https://reviveai.demo/simulated-payment-link/" + id;
        log.info("[SIMULATED] Created demo payment link {} for {} {} ({})", id, amount, currency, description);
        return PaymentLinkResult.builder()
                .id(id)
                .shortUrl(url)
                .simulated(true)
                .build();
    }
}
