package com.reviveai.enums;

/**
 * Lifecycle states for a Payment, mirroring Razorpay payment states
 * closely enough for the MVP's purposes.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}
