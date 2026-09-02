package com.reviveai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Every inbound Razorpay webhook delivery is persisted here first, keyed by
 * Razorpay's own event id. The unique constraint on external_event_id is
 * what makes webhook processing idempotent: a duplicate delivery fails the
 * insert / is detected by an existence check before any Payment or
 * RecoveryCase is touched.
 *
 * payload is stored as raw JSON text. Postgres JSONB (via a library such as
 * hypersistence-utils) would allow querying inside the payload, but is not
 * required for the MVP and is intentionally deferred to avoid an extra
 * dependency in Phase 1.
 */
@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = "external_event_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "external_event_id", nullable = false, unique = true)
    private String externalEventId;

    @NotBlank
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }
}
