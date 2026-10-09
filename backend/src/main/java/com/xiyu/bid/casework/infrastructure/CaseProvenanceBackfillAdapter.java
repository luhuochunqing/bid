// Input: ACTIVE 案例实体 / 来源字段批量更新命令
// Output: 回填候选快照 / 按项目原子持久化的五字段（条件更新保护 ACTIVE 状态与并发隔离）
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
                    update.sourcePageNumber());
            if (updated != 1) {
                throw new IllegalStateException("回填案例已不存在或非ACTIVE状态: " + update.id());
            }
        }
    }
}
