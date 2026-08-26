package com.reviveai.service;

import com.reviveai.entity.Customer;
import com.reviveai.exception.ResourceNotFoundException;
import com.reviveai.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Used by the webhook ingestion path: Razorpay payloads identify a
     * customer by email, and ReviveAI has no prior "create customer" step
     * of its own, so the first webhook mentioning a new email creates it.
     */
    @Transactional
    public Customer findOrCreateByEmail(String email, String name, String phone) {
        return customerRepository.findByEmail(email)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .name(name != null ? name : email)
                                .email(email)
                                .phone(phone)
                                .lifetimeValue(BigDecimal.ZERO)
                                .successfulPayments(0)
                                .failedPayments(0)
                                .build()
                ));
    }

    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    @Transactional
    public void recordSuccessfulPayment(Customer customer, BigDecimal amount) {
        customer.setSuccessfulPayments(customer.getSuccessfulPayments() + 1);
        customer.setLifetimeValue(customer.getLifetimeValue().add(amount));
        customerRepository.save(customer);
    }

    @Transactional
    public void recordFailedPayment(Customer customer) {
        customer.setFailedPayments(customer.getFailedPayments() + 1);
        customerRepository.save(customer);
    }
}
