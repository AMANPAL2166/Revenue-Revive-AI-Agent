package com.reviveai.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviveai.entity.Customer;
import com.reviveai.entity.Payment;
import com.reviveai.entity.WebhookEvent;
import com.reviveai.enums.PaymentStatus;
import com.reviveai.repository.WebhookEventRepository;
import com.reviveai.service.PaymentOutcomeService;
import com.reviveai.service.PaymentService;
import com.reviveai.service.RecoveryService;
import com.reviveai.service.WebhookProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookIdempotencyTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private RecoveryService recoveryService;

    @Mock
    private PaymentOutcomeService paymentOutcomeService;

    private WebhookProcessingService service;

    private static final String EVENT_ID = "evt_test_123";
    private static final String PAYLOAD = """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_test_1",
                    "amount": 499900,
                    "currency": "INR",
                    "status": "failed",
                    "email": "test@example.com",
                    "error_description": "Insufficient funds"
                  }
                }
              }
            }
            """;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WebhookProcessingService(
                webhookEventRepository, paymentService, recoveryService, paymentOutcomeService, new ObjectMapper());
    }

    @Test
    void firstDeliveryIsProcessed() {
        when(webhookEventRepository.existsByExternalEventId(EVENT_ID)).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer customer = Customer.builder().email("test@example.com").build();
        Payment failedPayment = Payment.builder()
                .externalPaymentId("pay_test_1")
                .customer(customer)
                .amount(BigDecimal.valueOf(4999))
                .currency("INR")
                .status(PaymentStatus.FAILED)
                .retryCount(0)
                .build();
        when(paymentService.upsertFromRazorpayPayload(any())).thenReturn(failedPayment);

        WebhookProcessingService.Outcome outcome = service.processEvent(EVENT_ID, "payment.failed", PAYLOAD);

        assertThat(outcome).isEqualTo(WebhookProcessingService.Outcome.PROCESSED);
        verify(paymentService, times(1)).upsertFromRazorpayPayload(any());
        // A FAILED payment must trigger RecoveryCase creation — this is the
        // Day 3+ hook point WebhookProcessingService now fulfills.
        verify(recoveryService, times(1)).createCaseForFailedPayment(failedPayment);
        verify(paymentOutcomeService, never()).handlePaymentSuccess(any());
    }

    @Test
    void successfulPaymentTriggersPaymentOutcomeServiceNotRecoveryCaseCreation() {
        when(webhookEventRepository.existsByExternalEventId("evt_success")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer customer = Customer.builder().email("test@example.com").build();
        Payment successfulPayment = Payment.builder()
                .externalPaymentId("pay_test_2")
                .customer(customer)
                .amount(BigDecimal.valueOf(4999))
                .currency("INR")
                .status(PaymentStatus.SUCCESS)
                .retryCount(0)
                .build();
        when(paymentService.upsertFromRazorpayPayload(any())).thenReturn(successfulPayment);

        WebhookProcessingService.Outcome outcome = service.processEvent("evt_success", "payment.captured", PAYLOAD);

        assertThat(outcome).isEqualTo(WebhookProcessingService.Outcome.PROCESSED);
        verify(paymentOutcomeService, times(1)).handlePaymentSuccess(successfulPayment);
        verify(recoveryService, never()).createCaseForFailedPayment(any());
    }

    @Test
    void duplicateDeliveryIsIgnoredAndNeverTouchesPaymentService() {
        when(webhookEventRepository.existsByExternalEventId(EVENT_ID)).thenReturn(true);

        WebhookProcessingService.Outcome outcome = service.processEvent(EVENT_ID, "payment.failed", PAYLOAD);

        assertThat(outcome).isEqualTo(WebhookProcessingService.Outcome.IGNORED_DUPLICATE);
        verify(paymentService, never()).upsertFromRazorpayPayload(any());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void unhandledEventTypeIsStoredButNotDispatchedToAnyService() {
        when(webhookEventRepository.existsByExternalEventId("evt_other")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookProcessingService.Outcome outcome = service.processEvent("evt_other", "order.paid", "{}");

        assertThat(outcome).isEqualTo(WebhookProcessingService.Outcome.IGNORED_UNHANDLED);
        verify(paymentService, never()).upsertFromRazorpayPayload(any());
    }
}
