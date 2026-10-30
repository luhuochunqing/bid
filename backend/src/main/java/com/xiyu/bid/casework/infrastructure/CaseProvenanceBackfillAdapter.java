// Input: ACTIVE 案例实体 / 来源字段批量更新命令（附扫描快照期望值）
// Output: 回填候选快照 / 按项目原子持久化的五字段（条件更新保护 ACTIVE 状态、并发隔离与快照一致性）
// Pos: casework 持久化适配器；不承载章节定位与清空判断
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CaseProvenanceBackfillAdapter implements CaseProvenanceBackfillPort {
    private final KnowledgeCaseRepository repository;

    @Override
    public List<Candidate> findActiveCandidates() {
        return repository.findByStatus("ACTIVE").stream()
                .map(kase -> new Candidate(kase.getId(), kase.getSourceProjectId(), kase.getResponseText(),
                        kase.getScoringPointTitle(), kase.getSourceSectionTitle(), kase.getSourceSectionPath(),
                        kase.getSourceSectionIndex(), kase.getSourceDocumentName(), kase.getSourcePageNumber()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(List<Update> updates) {
        if (updates.isEmpty()) {
            return;
        }
        for (Update update : updates) {
            int updated = repository.updateProvenanceIfActive(
                    update.id(),
                    update.sourceSectionTitle(),
                    update.sourceSectionPath(),
                    update.sourceSectionIndex(),
                    update.sourceDocumentName(),
                    update.sourcePageNumber(),
                    update.expectedResponseText(),
                    update.expectedScoringPointTitle(),
                    update.expectedSourceProjectId());
            if (updated != 1) {
                throw new IllegalStateException(
                        "回填案例已不存在、非ACTIVE状态或扫描快照已过期（正文/评分项/来源项目变化）： " + update.id());
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllIfProvenanceEmpty(List<Update> updates) {
        for (Update update : updates) {
            int updated = repository.updateProvenanceIfActiveAndEmpty(
                    update.id(), update.sourceSectionTitle(), update.sourceSectionPath(),
                    update.sourceSectionIndex(), update.sourceDocumentName(), update.sourcePageNumber(),
                    update.expectedResponseText(), update.expectedScoringPointTitle(),
                    update.expectedSourceProjectId());
            if (updated != 1) {
                throw new IllegalStateException(
                        "回填案例不存在、非ACTIVE、快照过期或来源字段已非全空： " + update.id());
            }
        }
    }
}
