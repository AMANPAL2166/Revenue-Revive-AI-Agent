package com.reviveai.recovery;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentLinkResult {
    private String id;
    private String shortUrl;
    /** True when this came from SimulatedRazorpayGatewayClient rather than a real Razorpay API call. */
    private boolean simulated;
}
