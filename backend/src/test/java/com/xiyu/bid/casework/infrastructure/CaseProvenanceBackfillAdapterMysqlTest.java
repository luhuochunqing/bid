package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Update;
import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("flyway-mysql")
@Transactional(propagation = Propagation.NEVER)
class CaseProvenanceBackfillAdapterMysqlTest extends AbstractMysqlIntegrationTest {
    @Autowired private CaseProvenanceBackfillAdapter adapter;
    @Autowired private KnowledgeCaseRepository repository;
    private final List<Long> ids = new ArrayList<>();

    @AfterEach
    void cleanup() { repository.deleteAllById(ids); }

    private KnowledgeCase create(String status) {
        KnowledgeCase kase = new KnowledgeCase();
        kase.setSourceProjectId(900074L);
        kase.setSourceProjectName("架构回填测试");
        kase.setScoringPointTitle("评分项保留");
        kase.setRequirementRaw("要求保留");
        kase.setResponseText("正文保留");
        kase.setStatus(status);
        kase.setSourceSectionTitle("旧标题");
        kase.setSourceSectionPath("旧路径");
        kase.setSourceSectionIndex(8);
        kase.setSourceDocumentName("旧文件.pdf");
        kase.setSourcePageNumber(9);
        kase = repository.saveAndFlush(kase);
        ids.add(kase.getId());
        return kase;
    }

    @Test
    void queryIncludesOnlyActiveAndReturnsCompleteSnapshot() {
        var active = create("ACTIVE");
        var offShelf = create("OFF_SHELF");
        var candidates = adapter.findActiveCandidates();
        assertThat(candidates).noneMatch(c -> c.id().equals(offShelf.getId()));
        assertThat(candidates).filteredOn(c -> c.id().equals(active.getId())).singleElement()
                .satisfies(c -> {
                    assertThat(c.sourceProjectId()).isEqualTo(900074L);
                    assertThat(c.responseText()).isEqualTo("正文保留");
                    assertThat(c.scoringPointTitle()).isEqualTo("评分项保留");
                    assertThat(c.sourceSectionTitle()).isEqualTo("旧标题");
                    assertThat(c.sourceSectionPath()).isEqualTo("旧路径");
                    assertThat(c.sourceSectionIndex()).isEqualTo(8);
                    assertThat(c.sourceDocumentName()).isEqualTo("旧文件.pdf");
                    assertThat(c.sourcePageNumber()).isEqualTo(9);
                });
    }

    @Test
    void savesAndClearsFiveFieldsWithoutChangingOtherData() {
        var kase = create("ACTIVE");
        var createdAt = repository.findById(kase.getId()).orElseThrow().getCreatedAt();
        adapter.saveAll(List.of(new Update(kase.getId(), "1. 已验证章节", "1. 已验证章节 > 1.1. 子章节", 2, "新文件.pdf", 3)));
        flushAndClear();
        var saved = repository.findById(kase.getId()).orElseThrow();
        assertThat(saved.getSourceSectionTitle()).isEqualTo("1. 已验证章节");
        assertThat(saved.getSourceSectionPath()).isEqualTo("1. 已验证章节 > 1.1. 子章节");
        assertThat(saved.getSourceSectionIndex()).isEqualTo(2);
        assertThat(saved.getSourceDocumentName()).isEqualTo("新文件.pdf");
        assertThat(saved.getSourcePageNumber()).isEqualTo(3);
        assertThat(saved.getResponseText()).isEqualTo("正文保留");
        assertThat(saved.getRequirementRaw()).isEqualTo("要求保留");
        assertThat(saved.getScoringPointTitle()).isEqualTo("评分项保留");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        adapter.saveAll(List.of(new Update(kase.getId(), null, null, null, null, null)));
        flushAndClear();
        var cleared = repository.findById(kase.getId()).orElseThrow();
        assertThat(cleared.getSourceSectionTitle()).isNull();
        assertThat(cleared.getSourceSectionPath()).isNull();
        assertThat(cleared.getSourceSectionIndex()).isNull();
        assertThat(cleared.getSourceDocumentName()).isNull();
        assertThat(cleared.getSourcePageNumber()).isNull();
    }

    @Test
    void missingCaseRollsBackWholeBatchWithoutRecreatingDeletedCase() {
        var kept = create("ACTIVE");
        var deleted = create("ACTIVE");
        repository.deleteById(deleted.getId());
        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kept.getId(), "新标题", null, null, null, null),
                new Update(deleted.getId(), "新标题", null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();
        assertThat(repository.findById(kept.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(deleted.getId())).isEmpty();
    }

    @Test
    void databaseFailureRollsBackEarlierChanges() {
        var first = create("ACTIVE");
        var second = create("ACTIVE");
        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(first.getId(), "新标题", null, null, null, null),
                new Update(second.getId(), "过长".repeat(600), null, null, null, null))))
                .isInstanceOf(RuntimeException.class);
        flushAndClear();
        assertThat(repository.findById(first.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(second.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }
}
