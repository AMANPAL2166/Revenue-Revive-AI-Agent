package com.reviveai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.config.ReviveAiProperties;
import com.reviveai.exception.InvalidWebhookSignatureException;
import com.reviveai.service.WebhookProcessingService;
import com.reviveai.util.RazorpaySignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookProcessingService webhookProcessingService;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final ReviveAiProperties properties;
    private final ObjectMapper objectMapper;

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> receiveRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader
    ) {
        String webhookSecret = properties.getRazorpay().getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!signatureVerifier.verify(rawPayload, signature, webhookSecret)) {
                throw new InvalidWebhookSignatureException("Signature verification failed");
            }
        } else {
            log.warn("RAZORPAY_WEBHOOK_SECRET not configured — skipping signature verification (demo-only behavior)");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed webhook JSON payload");
        }

        String eventType = root.hasNonNull("event") ? root.get("event").asText() : null;
        String externalEventId = resolveExternalEventId(eventIdHeader, root, rawPayload);

        WebhookProcessingService.Outcome outcome =
                webhookProcessingService.processEvent(externalEventId, eventType, rawPayload);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "eventId", externalEventId,
                "outcome", outcome.name()
        ));
    }

    /**
     * Razorpay does not always carry a stable top-level id for the *event*
     * itself (as opposed to the payment/subscription entity inside it).
     * Resolution order:
     *   1. X-Razorpay-Event-Id header, when Razorpay sends one
     *   2. a top-level "id" field in the JSON body, if present
     *   3. a SHA-256 hash of the raw body as a last-resort deterministic
     *      key — identical redeliveries hash identically and are still
     *      deduplicated correctly.
     */
    private String resolveExternalEventId(String headerEventId, JsonNode root, String rawPayload) {
        if (headerEventId != null && !headerEventId.isBlank()) {
            return headerEventId;
        }
        if (root.hasNonNull("id")) {
            return root.get("id").asText();
        }
        return "sha256:" + sha256Hex(rawPayload);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
