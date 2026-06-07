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
    private final CaseReferenceAppService caseReferenceAppService;

    @Transactional
    public Map<String, Object> reuseCase(Long id, String userName) {
        KnowledgeCase c = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("案例不存在: " + id));
        c.setReuseCount(c.getReuseCount() + 1);
        caseRepository.save(c);

        // 创建复用引用记录，追踪谁在什么时候复用了此案例
        caseReferenceAppService.createReferenceRecord(
                id, userName, "REUSE", "从案例库复用应答片段", c.getSourceProjectName());

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
