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

    private KnowledgeCase createEmptyProvenance(String status) {
        KnowledgeCase kase = create(status);
        kase.setSourceSectionTitle(null);
        kase.setSourceSectionPath(null);
        kase.setSourceSectionIndex(null);
        kase.setSourceDocumentName(null);
        kase.setSourcePageNumber(null);
        return repository.saveAndFlush(kase);
    }

    @Test
    void refusesWriteWhenAnyProvenanceFieldWasFilledAfterPreview() {
        var kase = createEmptyProvenance("ACTIVE");
        kase.setSourceDocumentName("并发补入.pdf");
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAllIfProvenanceEmpty(List.of(new Update(kase.getId(), "1. 新标题",
                "1. 新标题", 1, "回填.pdf", 2, kase.getResponseText(), kase.getScoringPointTitle(),
                kase.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        var unchanged = repository.findById(kase.getId()).orElseThrow();
        assertThat(unchanged.getSourceSectionTitle()).isNull();
        assertThat(unchanged.getSourceDocumentName()).isEqualTo("并发补入.pdf");
    }

    @Test
    void provenanceGuardFailureRollsBackTheWholeCrossProjectBatch() {
        var first = createEmptyProvenance("ACTIVE");
        first.setSourceProjectId(900075L);
        first = repository.saveAndFlush(first);
        var second = createEmptyProvenance("ACTIVE");
        second.setSourceProjectId(900076L);
        second.setSourceDocumentName("并发补入.pdf");
        second = repository.saveAndFlush(second);
        flushAndClear();

        KnowledgeCase firstSnapshot = first;
        KnowledgeCase secondSnapshot = second;
        assertThatThrownBy(() -> adapter.saveAllIfProvenanceEmpty(List.of(
                new Update(firstSnapshot.getId(), "1. 标题", "1. 标题", 1, "回填.pdf", 2,
                        firstSnapshot.getResponseText(), firstSnapshot.getScoringPointTitle(),
                        firstSnapshot.getSourceProjectId()),
                new Update(secondSnapshot.getId(), "2. 标题", "2. 标题", 2, "回填.pdf", 3,
                        secondSnapshot.getResponseText(), secondSnapshot.getScoringPointTitle(),
                        secondSnapshot.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        assertThat(repository.findById(firstSnapshot.getId()).orElseThrow().getSourceSectionTitle()).isNull();
        assertThat(repository.findById(secondSnapshot.getId()).orElseThrow().getSourceDocumentName())
                .isEqualTo("并发补入.pdf");
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
        adapter.saveAll(List.of(new Update(kase.getId(), "1. 已验证章节", "1. 已验证章节 > 1.1. 子章节", 2, "新文件.pdf", 3,
                kase.getResponseText(), kase.getScoringPointTitle(), kase.getSourceProjectId())));
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
        adapter.saveAll(List.of(new Update(kase.getId(), null, null, null, null, null,
                kase.getResponseText(), kase.getScoringPointTitle(), kase.getSourceProjectId())));
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
                new Update(kept.getId(), "新标题", null, null, null, null,
                        kept.getResponseText(), kept.getScoringPointTitle(), kept.getSourceProjectId()),
                new Update(deleted.getId(), "新标题", null, null, null, null,
                        "正文保留", "评分项保留", 900074L))))
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
                new Update(first.getId(), "新标题", null, null, null, null,
                        first.getResponseText(), first.getScoringPointTitle(), first.getSourceProjectId()),
                new Update(second.getId(), "过长".repeat(600), null, null, null, null,
                        second.getResponseText(), second.getScoringPointTitle(), second.getSourceProjectId()))))
                .isInstanceOf(RuntimeException.class);
        flushAndClear();
        assertThat(repository.findById(first.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(second.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }

    @Test
    void offShelfCaseAfterScanThrowsAndRollsBackWholeBatch() {
        var active1 = create("ACTIVE");
        var active2 = create("ACTIVE");
        // 模拟扫描后，active2 被下架（状态变为 OFF_SHELF）
        active2.setStatus("OFF_SHELF");
        repository.saveAndFlush(active2);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(active1.getId(), "新标题1", null, null, null, null,
                        active1.getResponseText(), active1.getScoringPointTitle(), active1.getSourceProjectId()),
                new Update(active2.getId(), "新标题2", null, null, null, null,
                        active2.getResponseText(), active2.getScoringPointTitle(), active2.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        // 验证全批次回滚：active1 没有被更新，active2 也没有被写入新标题
        assertThat(repository.findById(active1.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(active2.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(active2.getId()).orElseThrow().getStatus()).isEqualTo("OFF_SHELF");
    }

    @Test
    void staleResponseTextAfterScanRefusesWriteAndRollsBackBatch() {
        var fresh = create("ACTIVE");
        var stale = create("ACTIVE");
        // 模拟扫描后用户把正文改成了 B：库中已是 B，回填仍按快照 A 计算来源
        stale.setResponseText("用户修改后的正文B");
        repository.saveAndFlush(stale);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(fresh.getId(), "新标题", null, null, null, null,
                        fresh.getResponseText(), fresh.getScoringPointTitle(), fresh.getSourceProjectId()),
                new Update(stale.getId(), "基于过期正文的来源标题", null, null, null, null,
                        "正文保留", stale.getScoringPointTitle(), stale.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        // 过期来源不得写入，且同批次的合法写入一并回滚
        assertThat(repository.findById(stale.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(stale.getId()).orElseThrow().getResponseText()).isEqualTo("用户修改后的正文B");
        assertThat(repository.findById(fresh.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }

    @Test
    void staleScoringPointTitleAfterScanRefusesWrite() {
        var kase = create("ACTIVE");
        kase.setScoringPointTitle("用户修改后的评分项");
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kase.getId(), "基于过期评分项的来源标题", null, null, null, null,
                        kase.getResponseText(), "评分项保留", kase.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        assertThat(repository.findById(kase.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }

    @Test
    void staleSourceProjectAfterScanRefusesWrite() {
        var kase = create("ACTIVE");
        kase.setSourceProjectId(900075L);
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kase.getId(), "基于过期项目的来源标题", null, null, null, null,
                        kase.getResponseText(), kase.getScoringPointTitle(), 900074L))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        assertThat(repository.findById(kase.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }

    @Test
    void caseOnlyChangeInResponseTextIsDetectedAsStale() {
        // MySQL utf8mb4_unicode_ci 下普通 = 会忽略大小写：BINARY 精确比较才能发现该变化
        var kase = create("ACTIVE");
        kase.setResponseText("CaseABC正文");
        repository.saveAndFlush(kase);
        flushAndClear();
        kase.setResponseText("Caseabc正文");
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kase.getId(), "基于过期正文的来源标题", null, null, null, null,
                        "CaseABC正文", kase.getScoringPointTitle(), kase.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        assertThat(repository.findById(kase.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
        assertThat(repository.findById(kase.getId()).orElseThrow().getResponseText()).isEqualTo("Caseabc正文");
    }

    @Test
    void trailingSpaceChangeInResponseTextIsDetectedAsStale() {
        // BINARY 同样对尾随空格敏感（_ci 校对会忽略尾随空格），普通 = 会漏检该变化
        var kase = create("ACTIVE");
        kase.setResponseText("正文带空格 ");
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kase.getId(), "基于过期正文的来源标题", null, null, null, null,
                        "正文带空格", kase.getScoringPointTitle(), kase.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        assertThat(repository.findById(kase.getId()).orElseThrow().getSourceSectionTitle()).isEqualTo("旧标题");
    }

    @Test
    void interleavedConcurrentBusinessUpdateIsNotOverwritten() {
        var kase = create("ACTIVE");
        // 模拟业务侧并发修改了正文：回填快照已过期，写入必须失败回滚，业务的新正文不受损
        // （旧期望"过期快照仍可写入"与快照一致性要求矛盾，已按新契约改写；见任务问题二）
        kase.setResponseText("并发修改后的正文");
        repository.saveAndFlush(kase);
        flushAndClear();

        assertThatThrownBy(() -> adapter.saveAll(List.of(
                new Update(kase.getId(), "回填新标题", "回填路径", 1, "文件.pdf", 2,
                        "正文保留", kase.getScoringPointTitle(), kase.getSourceProjectId()))))
                .isInstanceOf(IllegalStateException.class);
        flushAndClear();

        var reloaded = repository.findById(kase.getId()).orElseThrow();
        // 过期来源没有写入
        assertThat(reloaded.getSourceSectionTitle()).isEqualTo("旧标题");
        // 并发修改的业务字段必须完好保留，未被旧快照覆盖
        assertThat(reloaded.getResponseText()).isEqualTo("并发修改后的正文");
    }
}

