package com.reviveai.controller;

import com.reviveai.dto.response.AgentActivityResponse;
import com.reviveai.dto.response.DashboardSummaryResponse;
import com.reviveai.dto.response.RevenueBreakdownResponse;
import com.reviveai.repository.AgentActionRepository;
import com.reviveai.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AgentActionRepository agentActionRepository;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/revenue-risk")
    public RevenueBreakdownResponse revenueRisk() {
        return dashboardService.getRevenueBreakdown();
    }

    /** Backs the Agent Activity page (spec section 35). */
    @GetMapping("/agent-activity")
    public Page<AgentActivityResponse> agentActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return agentActionRepository.findAllByOrderByProposedAtDesc(PageRequest.of(page, size))
                .map(AgentActivityResponse::from);
    }
}
