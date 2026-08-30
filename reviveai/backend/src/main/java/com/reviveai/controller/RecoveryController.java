package com.reviveai.controller;

import com.reviveai.dto.response.RecoveryCaseDetailResponse;
import com.reviveai.dto.response.RecoveryCaseSummaryResponse;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.enums.Priority;
import com.reviveai.enums.RecoveryCaseStatus;
import com.reviveai.repository.AgentActionRepository;
import com.reviveai.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/recovery-cases")
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final AgentActionRepository agentActionRepository;

    @GetMapping
    public Page<RecoveryCaseSummaryResponse> list(
            @RequestParam(required = false) RecoveryCaseStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return recoveryService.list(status, priority, pageable).map(RecoveryCaseSummaryResponse::from);
    }

    @GetMapping("/{id}")
    public RecoveryCaseDetailResponse getById(@PathVariable UUID id) {
        return toDetail(recoveryService.getById(id));
    }

    @PostMapping("/{id}/analyze")
    public RecoveryCaseDetailResponse analyze(@PathVariable UUID id) {
        return toDetail(recoveryService.analyzeById(id));
    }

    @PostMapping("/{id}/execute")
    public RecoveryCaseDetailResponse execute(@PathVariable UUID id) {
        return toDetail(recoveryService.execute(id));
    }

    @PostMapping("/{id}/approve")
    public RecoveryCaseDetailResponse approve(@PathVariable UUID id) {
        return toDetail(recoveryService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public RecoveryCaseDetailResponse reject(@PathVariable UUID id) {
        return toDetail(recoveryService.reject(id));
    }

    private RecoveryCaseDetailResponse toDetail(RecoveryCase recoveryCase) {
        var actions = agentActionRepository.findByRecoveryCaseIdOrderByProposedAtAsc(recoveryCase.getId());
        return RecoveryCaseDetailResponse.from(recoveryCase, actions);
    }
}
