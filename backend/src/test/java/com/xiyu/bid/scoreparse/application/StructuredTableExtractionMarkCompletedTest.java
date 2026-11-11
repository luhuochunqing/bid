// Input: markEvidencePipelineCompleted 落库收口（spec 062 方案 A）
// Output: 多表自洽不误报归一化告警 + tableSplits 拆分明细落 run_summary + 单表失配正确告警
// Pos: Test/scoreparse/application — 命令式外壳 mock，无 Spring 上下文

package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.AiProviderDiagnostics;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StructuredTableExtractionMarkCompletedTest {

    @Test
    void multiTableAllSelfConsistent_noFalseNormalizationWarningAndTableSplitsInRunSummary()
            throws IOException, InterruptedException {
        StructuredTableResult structured = extract(multiTableArtifact(),
                "本项目评分总分100分。评分标准由技术评分表和商务评分表两张独立评分表组成。");
        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepository = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estimatedScoreService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-multi", structured, taskRepository, stateService,
                progressService, itemService, estimatedScoreService);

        // spec 062：多表各自自洽时不得以文档级 declaredTotal 对比全表 totalWeight 产生误报
        assertThat(task.getNormalizationWarning()).isNull();
        assertThat(task.getOriginalTotalWeight()).isNull();
        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.AUTO_APPROVED.name());
        // US2：run_summary 必须承载每张表的独立拆分明细
        assertThat(task.getRunSummary())
                .contains("\"tableSplits\":[")
                .contains("\"tableId\":\"m3-t0001\"")
                .contains("\"tableId\":\"m3-t0002\"")
                .contains("\"declaredTotal\":60.00")
                .contains("\"declaredTotal\":40.00")
                .contains("\"decision\":\"AUTO_APPROVED\"");
        verify(taskRepository).save(task);
    }

    @Test
    void singleTableWeightMismatch_emitsNormalizationWarningWithActualTotal()
            throws IOException, InterruptedException {
        StructuredTableResult structured = extract(singleTableMismatchArtifact(),
                "本项目评分总分30分。投标报价得分：等于基准价的得满分30分。");
        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepository = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estimatedScoreService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-single", structured, taskRepository, stateService,
                progressService, itemService, estimatedScoreService);

        // 单表失配：leafWeightSum=100 != 声明30 → 保留原告警语义
        assertThat(task.getNormalizationWarning())
                .isNotNull()
                .contains("评分权重合计为 100分")
                .contains("不符合30分声明总分");
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("100");
        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        assertThat(task.getFallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void partialApproval_reviewRequired_neverOverwritesOfficialItemsNorEstimates()
            throws IOException, InterruptedException {
        // 回归D 编排层：技术表通过 + 商务表缺项（声明40 实际 20）→ 文档级 REVIEW_REQUIRED
        StructuredTableResult structured = extract(partialApprovalArtifact(),
                "本项目评分总分100分。评分标准由技术评分表和商务评分表两张独立评分表组成。");
        assertThat(structured.candidates())
                .as("前置：技术表 3 项通过，部分候选非空")
                .hasSize(3);
        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepository = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estimatedScoreService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-partial", structured, taskRepository, stateService,
                progressService, itemService, estimatedScoreService);

        assertThat(task.getDecisionStatus())
                .as("执行结束（COMPLETED）不得把业务决策改成 AUTO_APPROVED")
                .isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        // 部分候选不得进入项目级全量替换与预计得分计算
        verify(itemService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        // 本次提取事实与复核候选仍须保存（供复核 UI 按任务读取）
        verify(itemService).persistFactsAndEvidence(any(), any(), anyList(), anyList());
        verify(taskRepository).save(task);
        verify(stateService).markCompleted("task-partial");
    }

    @Test
    void fullApproval_persistsItemsAndTriggersEstimate()
            throws IOException, InterruptedException {
        // 回归F 防过度修复：两表全通过时，正式发布与预计得分计算路径必须照常工作
        StructuredTableResult structured = extract(multiTableArtifact(),
                "本项目评分总分100分。评分标准由技术评分表和商务评分表两张独立评分表组成。");
        assertThat(structured.candidates()).hasSize(5);
        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepository = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estimatedScoreService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-full", structured, taskRepository, stateService,
                progressService, itemService, estimatedScoreService);

        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.AUTO_APPROVED.name());
        verify(itemService).persistItems(any(), any(), anyList());
        verify(estimatedScoreService).estimateForProject(any());
        verify(stateService).markCompleted("task-full");
    }

    @Test
    void autoDecisionWithoutOriginalProofCannotReplaceOfficialItems()
            throws IOException, InterruptedException {
        StructuredTableResult verified = extract(multiTableArtifact(),
                "评分标准由技术评分表和商务评分表两张独立评分表组成。");
        StructuredTableResult unverified = StructuredTableResult.evidencePipeline(verified.facts(),
                verified.candidates(), verified.completeness(), verified.tableCompletenesses());
        ScoreParseTask task = new ScoreParseTask();
        ScoreItemPersistenceService items = mock(ScoreItemPersistenceService.class);
        StructuredTableExtractionHandler.markEvidencePipelineCompleted(task, "task-unverified", unverified,
                mock(ScoreParseTaskRepository.class), mock(ScoreParseTaskStateService.class),
                mock(ScoreParseProgressService.class), items, mock(EstimatedScoreService.class));
        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        verify(items, never()).persistItems(any(), any(), anyList());
        verify(items).persistFactsAndEvidence(any(), any(), anyList(), anyList());
    }

    @Test
    void rejectedParseSavesTaskFactsWithoutDeletingExistingItemsOrScores()
            throws IOException, InterruptedException {
        StructuredTableResult structured = extract(partialApprovalArtifact(),
                "评分标准由技术评分表和商务评分表两张独立评分表组成。");
        ScoreItemRepository officialItems = mock(ScoreItemRepository.class);
        ScoreResultRepository oldScores = mock(ScoreResultRepository.class);
        ScoreParseFactRepository reviewFacts = mock(ScoreParseFactRepository.class);
        ScoreParseEvidenceRepository reviewEvidence = mock(ScoreParseEvidenceRepository.class);
        ScoreItemPersistenceService persistence = new ScoreItemPersistenceService(
                officialItems, oldScores, reviewFacts, reviewEvidence, new ObjectMapper());
        ScoreParseTask task = new ScoreParseTask();
        StructuredTableExtractionHandler.markEvidencePipelineCompleted(task, "task-protect", structured,
                mock(ScoreParseTaskRepository.class), mock(ScoreParseTaskStateService.class),
                mock(ScoreParseProgressService.class), persistence, mock(EstimatedScoreService.class));
        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        verify(officialItems, never()).deleteByProjectId(any());
        verify(oldScores, never()).deleteByScoreItemIdIn(anyList());
        verify(reviewFacts).saveAll(anyList());
    }

    private static StructuredTableResult extract(
            DocumentArtifact artifact, String tenderText)
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 mark-completed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "mark-completed.pdf", "memory://mark-completed.pdf", tenderText);
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        return artifact;
                    }
                };
        return StructuredTableExtractionHandler.extract(source, resolver, artifactExtractor, null);
    }

    private static DocumentArtifact multiTableArtifact() {
        List<List<String>> technicalRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("2", "技术团队\n（20分）", "团队优秀得满分20分。"),
                List.of("3", "技术培训\n（10分）", "培训完整得满分10分。"),
                List.of("合计\n得分", "满分\n（60分）", ""));
        List<List<String>> commercialRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "商务报价\n（20分）", "报价合理得满分20分。"),
                List.of("2", "售后服务\n（20分）", "服务完善得满分20分。"),
                List.of("合计\n得分", "满分\n（40分）", ""));
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:mark-multi", "mark-multi", "PDF", "pdf-table-v1"),
                List.of(buildScoreTable("m3-t0001", technicalRows),
                        buildScoreTable("m3-t0002", commercialRows)),
                List.of());
    }

    private static DocumentArtifact singleTableMismatchArtifact() {
        List<List<String>> rows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "投标报价\n（40分）", "报价合理得满分40分。"),
                List.of("2", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("3", "售后服务\n（30分）", "服务完善得满分30分。"));
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:mark-single", "mark-single", "PDF", "pdf-table-v1"),
                List.of(buildScoreTable("m3-t0101", rows)),
                List.of());
    }

    private static DocumentArtifact partialApprovalArtifact() {
        // 技术表完整（3 项 60 分声明）；商务表真实缺项：声明 40 分，仅抽到 1 项 20 分
        List<List<String>> technicalRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("2", "技术团队\n（20分）", "团队优秀得满分20分。"),
                List.of("3", "技术培训\n（10分）", "培训完整得满分10分。"),
                List.of("合计\n得分", "满分\n（60分）", ""));
        List<List<String>> commercialRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "商务报价\n（20分）", "报价合理得满分20分。"),
                List.of("合计\n得分", "满分\n（40分）", ""));
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:mark-partial", "mark-partial", "PDF", "pdf-table-v1"),
                List.of(buildScoreTable("m3-t0001", technicalRows),
                        buildScoreTable("m3-t0002", commercialRows)),
                List.of());
    }

    private static DocumentArtifact.CanonicalTable buildScoreTable(String tableId, List<List<String>> rows) {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        tableId + "-r" + row + "-c" + col, row, col, text,
                        text.replaceAll("\\s+", " ").trim(), 1, 1, false,
                        "pdf:page=1;table=" + tableId + ";row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact.CanonicalTable(
                tableId, 1, "本项目评分办法", rows.size(), rows.get(0).size(), List.of(), cells, true);
    }

    @Test
    void markEvidencePipelineCompleted_carriesAiProviderDiagnosticsInRunSummary() {
        AiProviderDiagnostics diag = AiProviderDiagnostics.success("deepseek", "deepseek-chat", 2100L,
                List.of(new AiProviderDiagnostics.AiCallDetail("TABLE_EXTRACTION", "deepseek", "deepseek-chat", "SUCCESS", 2100L, null, null)));
        StructuredTableResult structured = StructuredTableResult.evidencePipeline(
                List.of(), List.of(), null, List.of(), List.of(), diag);
        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepo = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-diag", structured, taskRepo, stateService, progressService, itemService, estService);

        assertThat(task.getRunSummary())
                .contains("\"llmModel\":\"deepseek-chat\"")
                .contains("\"aiProviderDiagnostics\":{")
                .contains("\"provider\":\"deepseek\"")
                .contains("\"latencyMs\":2100");
    }

    @Test
    void candidatesWeightSumMismatchWithDeclaredTotal_downgradesToReviewRequired_neverPersistsItems()
            throws IOException, InterruptedException {
        StructuredTableResult verified = extract(multiTableArtifact(),
                "本项目评分总分100分。评分标准由技术评分表和商务评分表两张独立评分表组成。");
        var reducedCandidates = verified.candidates().subList(0, verified.candidates().size() - 1);
        var mockProof = mock(com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.PublicationProof.class);
        org.mockito.Mockito.when(mockProof.matches(any(), any())).thenReturn(true);
        StructuredTableResult reduced = StructuredTableResult.evidencePipeline(verified.facts(),
                reducedCandidates, verified.completeness(), verified.tableCompletenesses(), List.of(mockProof));

        ScoreParseTask task = new ScoreParseTask();
        ScoreParseTaskRepository taskRepo = mock(ScoreParseTaskRepository.class);
        ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
        ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
        ScoreItemPersistenceService itemService = mock(ScoreItemPersistenceService.class);
        EstimatedScoreService estService = mock(EstimatedScoreService.class);

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, "task-weight-mismatch", reduced, taskRepo, stateService,
                progressService, itemService, estService);

        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        assertThat(task.getFallbackReason()).isEqualTo("CANDIDATE_WEIGHT_SUM_MISMATCH");
        verify(itemService, never()).persistItems(any(), any(), anyList());
        verify(estService, never()).estimateForProject(any());
        assertThat(task.getNormalizationWarning()).contains("不符合100分声明总分");
        verify(taskRepo).save(task);
    }
}
