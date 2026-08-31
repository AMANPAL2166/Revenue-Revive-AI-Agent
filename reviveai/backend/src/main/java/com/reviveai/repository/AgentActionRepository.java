package com.reviveai.repository;

import com.reviveai.entity.AgentAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {

    @EntityGraph(attributePaths = {
            "recoveryCase",
            "recoveryCase.customer"
    })
    Page<AgentAction> findAllByOrderByProposedAtDesc(Pageable pageable);

    List<AgentAction> findByRecoveryCaseIdOrderByProposedAtAsc(
            UUID recoveryCaseId
    );
}