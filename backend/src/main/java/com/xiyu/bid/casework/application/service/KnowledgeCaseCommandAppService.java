package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeCaseCommandAppService {

    private final KnowledgeCaseRepository caseRepository;

    @Transactional
    public Map<String, Object> reuseCase(Long id) {
        KnowledgeCase c = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
        c.setReuseCount(c.getReuseCount() + 1);
        caseRepository.save(c);
        return Map.of(
                "caseId", c.getId(),
                "newReuseCount", c.getReuseCount()
        );
    }

    @Transactional
    public Map<String, Object> offShelfCase(Long id) {
        KnowledgeCase c = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
        c.setStatus("OFF_SHELF");
        caseRepository.save(c);
        return Map.of(
                "caseId", c.getId(),
                "status", "OFF_SHELF"
        );
    }

    @Transactional
    public Map<String, Object> pinCase(Long id) {
        KnowledgeCase c = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
        c.setIsPinned(true);
        caseRepository.save(c);
        return Map.of(
                "caseId", c.getId(),
                "pinned", true
        );
    }

    @Transactional
    public Map<String, Object> unpinCase(Long id) {
        KnowledgeCase c = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
        c.setIsPinned(false);
        caseRepository.save(c);
        return Map.of(
                "caseId", c.getId(),
                "pinned", false
        );
    }
}
