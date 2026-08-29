package com.reviveai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends recovery reminders to customers. The MVP implementation is a mock
 * provider that logs the reminder rather than sending a real email/SMS/
 * WhatsApp message. The dashboard must only ever say "Reminder sent"
 * (generic), never claim a specific real channel like "WhatsApp message
 * delivered" unless a real provider is actually wired in here.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * @return true if the (mock) send succeeded. A real implementation
     * would replace the body of this method with a call to a provider
     * such as SendGrid, Twilio, or the WhatsApp Business API, and this
     * signature would not need to change.
     */
    public boolean sendReminder(String customerEmail, String message) {
        log.info("[MOCK NOTIFICATION] To: {} | Message: {}", customerEmail, message);
        return true;
    }
}
