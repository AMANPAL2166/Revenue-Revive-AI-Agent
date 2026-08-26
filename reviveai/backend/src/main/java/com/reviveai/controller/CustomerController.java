package com.reviveai.controller;

import com.reviveai.dto.response.CustomerResponse;
import com.reviveai.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable UUID id) {
        return CustomerResponse.from(customerService.getById(id));
    }
}
