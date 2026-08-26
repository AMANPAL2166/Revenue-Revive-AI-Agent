package com.reviveai.util;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies Razorpay webhook signatures per Razorpay's documented scheme:
 * signature = hex(HMAC_SHA256(rawBody, webhookSecret)).
 * Uses a constant-time comparison to avoid timing side-channels.
 */
@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";

    public boolean verify(String payload, String signatureHeader, String webhookSecret) {
        if (signatureHeader == null || signatureHeader.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
