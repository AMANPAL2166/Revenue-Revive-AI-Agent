package com.reviveai.enums;

/**
 * Not explicitly enumerated in the build spec's field list, but a
 * Subscription needs a bounded status set to be queryable/reportable.
 * Kept minimal and specific to the recovery use case.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    RENEWAL_FAILED,
    CANCELLED
}
