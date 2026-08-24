package com.reviveai.repository;

import com.reviveai.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, java.util.UUID> {

    Optional<WebhookEvent> findByExternalEventId(String externalEventId);

    /**
     * The idempotency check: called before any processing begins. If this
     * returns true, WebhookController must short-circuit and return
     * safely without touching Payment / RecoveryCase state.
     */
    boolean existsByExternalEventId(String externalEventId);
}
