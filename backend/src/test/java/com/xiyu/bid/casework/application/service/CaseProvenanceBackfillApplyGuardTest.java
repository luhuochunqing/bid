// Input: preview 计划、人工确认的批次/候选数/清单摘要回执
// Output: 仅匹配回执时写入 FILLABLE 项；任一回执不匹配时零写入
// Pos: Backend/casework 应用层测试——生产回填 apply 的 fail-closed 门禁
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CaseProvenanceBackfillApplyGuardTest {
    private final CaseProvenanceBackfillPort port = mock(CaseProvenanceBackfillPort.class);
    private final CaseProvenanceBackfillAppService service = new CaseProvenanceBackfillAppService(
            port, mock(ProjectRepository.class), mock(BidDocumentLoader.class));
    private final CaseProvenanceBackfillArtifactWriter writer = new CaseProvenanceBackfillArtifactWriter();

    @Test
    void refusesAnyReceiptMismatchWithoutWriting() {
        CaseProvenanceBackfillPlan plan = plan();
        String digest = writer.manifestSha256("batch-1", plan);

        assertThatThrownBy(() -> service.apply(plan,
                new CaseProvenanceBackfillAppService.PreviewReceipt("batch-1", 2, digest)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.apply(plan,
                new CaseProvenanceBackfillAppService.PreviewReceipt("batch-1", 1, "0".repeat(64))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.apply(plan,
                new CaseProvenanceBackfillAppService.PreviewReceipt("batch-2", 1, digest)))
                .isInstanceOf(IllegalStateException.class);

        verify(port, never()).saveAllIfProvenanceEmpty(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void writesOnlyFillableRowsWhenReceiptMatches() {
        CaseProvenanceBackfillPlan plan = plan();
        var receipt = new CaseProvenanceBackfillAppService.PreviewReceipt(
                "batch-1", 1, writer.manifestSha256("batch-1", plan));

        var result = service.apply(plan, receipt);

        assertThat(result.updated()).isEqualTo(1);
        verify(port).saveAllIfProvenanceEmpty(org.mockito.ArgumentMatchers.argThat(updates ->
                updates.size() == 1
                        && updates.getFirst().id().equals(1L)
                        && updates.getFirst().sourceSectionTitle().equals("1. 标题")));
    }

    @Test
    void submitsCrossProjectUpdatesAsOneAtomicBatch() {
        Candidate first = new Candidate(1L, 10L, "证据一", "评分项", null, null, null, null, null);
        Candidate second = new Candidate(2L, 20L, "证据二", "评分项", null, null, null, null, null);
        CaseProvenanceBackfillPlan plan = new CaseProvenanceBackfillPlan(List.of(
                fillable(first, "1. 标题", "a".repeat(64)),
                fillable(second, "2. 标题", "b".repeat(64))));
        var receipt = new CaseProvenanceBackfillAppService.PreviewReceipt(
                "batch-cross-project", 2, writer.manifestSha256("batch-cross-project", plan));

        service.apply(plan, receipt);

        verify(port).saveAllIfProvenanceEmpty(org.mockito.ArgumentMatchers.argThat(updates ->
                updates.size() == 2
                        && updates.stream().map(CaseProvenanceBackfillPort.Update::expectedSourceProjectId)
                        .toList().equals(List.of(10L, 20L))));
    }

    private static CaseProvenanceBackfillPlan.Decision fillable(
            Candidate candidate, String title, String digest) {
        return new CaseProvenanceBackfillPlan.Decision(candidate,
                CaseProvenanceBackfillPlan.DecisionType.FILLABLE, "unique match",
                new CaseProvenanceBackfillPlan.Proposed(title, title, 0, "标书.pdf", 3), digest);
    }

    private static CaseProvenanceBackfillPlan plan() {
        Candidate fillable = new Candidate(1L, 10L, "证据", "评分项", null, null, null, null, null);
        Candidate existing = new Candidate(2L, 10L, "已有", "评分项", "旧标题", null, null, null, null);
        return new CaseProvenanceBackfillPlan(List.of(
                new CaseProvenanceBackfillPlan.Decision(fillable,
                        CaseProvenanceBackfillPlan.DecisionType.FILLABLE, "unique match",
                        new CaseProvenanceBackfillPlan.Proposed("1. 标题", "1. 标题", 0, "标书.pdf", 3),
                        "a".repeat(64)),
                new CaseProvenanceBackfillPlan.Decision(existing,
                        CaseProvenanceBackfillPlan.DecisionType.SKIP_EXISTING, "existing provenance",
                        null, "b".repeat(64))));
    }
}
