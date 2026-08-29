package com.reviveai.recovery;

import java.math.BigDecimal;

/**
 * Abstraction over Razorpay's payment-link creation, used by
 * CREATE_PAYMENT_LINK, OFFER_DISCOUNT (a link at a reduced amount), and
 * RETRY_PAYMENT (a fresh link the customer can use to retry, since
 * Razorpay has no literal "retry this payment" API). Exactly one
 * implementation is active at runtime — chosen in GatewayConfig — but
 * RecoveryActionExecutor only ever depends on this interface, never on a
 * concrete adapter.
 */
public interface RazorpayGatewayClient {
    PaymentLinkResult createPaymentLink(BigDecimal amount, String currency, String customerEmail, String description);
}
