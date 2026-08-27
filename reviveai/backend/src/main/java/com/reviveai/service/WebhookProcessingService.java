package com.reviveai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.entity.WebhookEvent;
import com.reviveai.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public enum Outcome { PROCESSED, IGNORED_DUPLICATE, IGNORED_UNHANDLED }

    /**
     * Entry point for the webhook pipeline. The idempotency check, event
     * storage, and payment/customer state updates all happen inside one
     * transaction, so a crash mid-processing cannot leave a WebhookEvent
     * marked "received" without its downstream effects (or vice versa).
     *
     * Day 3+ hook point: once RevenueRiskService exists, a payment
     * transitioning to FAILED here is where RecoveryCase creation gets
     * triggered. Left out of this method for now to keep Day 2's
     * transaction boundary focused on ingestion + payment/customer state.
     */
    @Transactional
    public Outcome processEvent(String externalEventId, String eventType, String rawPayload) {
        if (webhookEventRepository.existsByExternalEventId(externalEventId)) {
            log.info("Ignoring duplicate webhook event: {}", externalEventId);
            return Outcome.IGNORED_DUPLICATE;
        }

        WebhookEvent event = WebhookEvent.builder()
                .externalEventId(externalEventId)
                .eventType(eventType)
                .payload(rawPayload)
                .processed(false)
                .build();
        webhookEventRepository.save(event);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            boolean handled = dispatch(eventType, root);
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
            return handled ? Outcome.PROCESSED : Outcome.IGNORED_UNHANDLED;
        } catch (Exception e) {
            log.error("Failed to process webhook event {} ({}): {}",
                    externalEventId, eventType, e.getMessage(), e);

            throw new IllegalStateException(
                    "Failed to process webhook event " + externalEventId, e);
        }
    }

    private boolean dispatch(String eventType, JsonNode root) {
        if (eventType == null) {
            return false;
        }
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");

        return switch (eventType) {
            case "payment.failed", "payment.captured", "payment.authorized" -> {
                if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
                    log.warn("Event {} had no payload.payment.entity node", eventType);
                    yield false;
                }
                paymentService.upsertFromRazorpayPayload(paymentEntity);
                yield true;
            }
            default -> {
                log.info("Unhandled event type (stored, not processed further): {}", eventType);
                yield false;
            }
        };
    }
}
