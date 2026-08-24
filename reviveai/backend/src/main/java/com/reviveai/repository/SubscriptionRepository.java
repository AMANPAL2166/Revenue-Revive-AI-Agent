package com.reviveai.repository;

import com.reviveai.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId);

    boolean existsByExternalSubscriptionId(String externalSubscriptionId);

    List<Subscription> findByCustomerId(UUID customerId);
}
