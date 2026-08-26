package com.reviveai.util;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Categorizes a raw payment-gateway failure reason into a coarse severity
 * bucket. Deliberately keyword-based and simple — this is an MVP heuristic,
 * not NLP, and is documented as such wherever it's consumed.
 */
@Component
public class FailureReasonClassifier {

    /** Transient failures — retrying or reminding the customer can plausibly succeed. */
    private static final Set<String> RECOVERABLE_KEYWORDS = Set.of(
            "insufficient funds", "insufficient balance", "card declined",
            "expired card", "issuer declined", "timeout", "network error",
            "bank server down", "payment not authorized", "3ds failure",
            "otp"
    );

    /** The payment method itself is unusable or the attempt was fraudulent/blocked. */
    private static final Set<String> SEVERE_KEYWORDS = Set.of(
            "fraud", "stolen card", "blocked", "card blacklisted",
            "restricted card", "lost card", "reported lost or stolen"
    );

    public enum Severity { RECOVERABLE, SEVERE, UNKNOWN }

    public Severity classify(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return Severity.UNKNOWN;
        }
        String normalized = failureReason.toLowerCase();
        if (SEVERE_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return Severity.SEVERE;
        }
        if (RECOVERABLE_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return Severity.RECOVERABLE;
        }
        return Severity.UNKNOWN;
    }

    public boolean isRecoverable(String failureReason) {
        return classify(failureReason) == Severity.RECOVERABLE;
    }

    public boolean isSevere(String failureReason) {
        return classify(failureReason) == Severity.SEVERE;
    }
}