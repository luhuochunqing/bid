// Input: 预置正式评分项与打分的项目 + 部分通过/全通过的 Evidence Pipeline 结果
// Output: 部分通过不覆盖正式结果、候选按任务隔离、全通过正常发布的持久化证据
// Pos: scoreparse/application — @DataJpaTest 真实持久化集成（回归 D/E/F/G）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 部分通过不得覆盖正式评分项（真实持久化验证，不用 mock 持久化服务）。
 * <p>回归 D/E/F/G：旧评分项与实际打分保持不变、旧任务证据不被删、候选按 parseTaskId
 * 隔离、重复保存幂等、全通过仍可正常发布并触发预计得分计算。
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({ScoreItemPersistenceService.class, ScorePartialOverwritePersistenceTest.JacksonTestConfig.class})
class ScorePartialOverwritePersistenceTest {

    /** @DataJpaTest 不含 Jackson 自动配置，补最小 ObjectMapper 依赖。 */
    @org.springframework.boot.test.context.TestConfiguration
    static class JacksonTestConfig {
        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private final ScoreParseTaskStateService stateService = mock(ScoreParseTaskStateService.class);
    private final ScoreParseProgressService progressService = mock(ScoreParseProgressService.class);
    private final EstimatedScoreService estimatedScoreService = mock(EstimatedScoreService.class);

    @Autowired
    private ScoreItemRepository itemRepository;
    @Autowired
    private ScoreResultRepository resultRepository;
    @Autowired
    private ScoreParseFactRepository factRepository;
    @Autowired
    private ScoreParseEvidenceRepository evidenceRepository;
    @Autowired
    private ScoreParseTaskRepository taskRepository;
    @Autowired
    private ScoreItemPersistenceService persistenceService;

    @Test
    void partialApproval_withExistingOfficialItems_keepsItemsResultsAndOldEvidenceIntact() {
        // 回归D：预置完整 60+40 正式结果与实际打分，新解析技术表通过、商务表缺项 → 需要复核
        Long projectId = 9101L;
        ScoreParseTask oldTask = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("old-parse-task").projectId(projectId).taskType("PARSE")
                .status("COMPLETED").build());
        List<BigDecimal> oldWeights = List.of(bd("30"), bd("20"), bd("10"), bd("20"), bd("20"));
        List<ScoreItem> oldItems = new ArrayList<>();
        for (int i = 0; i < oldWeights.size(); i++) {
            oldItems.add(ScoreItem.builder()
                    .projectId(projectId).parseTaskId(oldTask.getId()).itemIndex(i + 1)
                    .code(String.valueOf(i + 1)).dim("旧评分项" + (i + 1)).detail("旧明细" + (i + 1))
                    .weight(oldWeights.get(i)).scoreType("OBJECTIVE").statusStage1("OK")
                    .estScore(oldWeights.get(i)).sourceText("旧原文" + (i + 1))
                    .sourceCellIds("[\"old-c" + (i + 1) + "\"]")
                    .extractionMethod("EVIDENCE_PIPELINE").validationStatus("AUTO_APPROVED")
                    .build());
        }
        List<ScoreItem> savedOldItems = itemRepository.saveAllAndFlush(oldItems);
        List<Long> oldItemIds = savedOldItems.stream().map(ScoreItem::getId).toList();
        List<BigDecimal> actualScores = List.of(bd("25"), bd("18"), bd("8"), bd("15"), bd("12"));
        for (int i = 0; i < savedOldItems.size(); i++) {
            resultRepository.saveAndFlush(ScoreResult.builder()
                    .scoreItemId(savedOldItems.get(i).getId()).scoringTaskId(77L)
                    .actualScore(actualScores.get(i)).statusStage2("OK").evidence("旧打分依据" + (i + 1))
                    .build());
        }
        // 旧任务的解析事实与单元格证据（旧正式结果的复核证据链，不得被新任务删除）
        ScoreParseFact oldFact = factRepository.saveAndFlush(ScoreParseFact.builder()
                .projectId(projectId).parseTaskId(oldTask.getId()).factId("old-fact-1")
                .sourceTableId("old-t1").sourceCellIds("[\"old-c1\"]").sourceText("旧事实原文")
                .code("1").dimension("技术方案").weight(bd("30")).extractionMethod("DETERMINISTIC")
                .confidence(BigDecimal.ONE).validationStatus("AUTO_APPROVED").build());
        evidenceRepository.saveAndFlush(ScoreParseEvidence.builder()
                .factId("old-fact-1").parseTaskId(oldTask.getId()).cellId("old-c1")
                .kind("EXPLICIT_WEIGHT_COLUMN").quotedText("30分").startOffset(0).endOffset(3)
                .explanation("旧证据").build());

        // 新解析：部分通过（技术表 AUTO_APPROVED + 商务表缺项 REVIEW_REQUIRED）
        ScoreParseTask newTask = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("new-parse-task").projectId(projectId).taskType("PARSE")
                .status("PROCESSING").build());
        StructuredTableResult structured = extract(partialApprovalArtifact());

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                newTask, newTask.getTaskId(), structured, taskRepository, stateService,
                progressService, persistenceService, estimatedScoreService);

        // 1) 正式评分项：ID/数量/分值/业务字段/归属任务全部不变（未删除重建）
        List<ScoreItem> afterItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(afterItems).hasSize(5);
        assertThat(afterItems).extracting(ScoreItem::getId).containsExactlyElementsOf(oldItemIds);
        assertThat(afterItems).extracting(ScoreItem::getWeight).isEqualTo(oldWeights);
        assertThat(afterItems).extracting(ScoreItem::getParseTaskId)
                .containsOnly(oldTask.getId());
        assertThat(afterItems).extracting(ScoreItem::getDim)
                .containsExactly("旧评分项1", "旧评分项2", "旧评分项3", "旧评分项4", "旧评分项5");
        // 2) 实际打分记录及关联不变
        List<ScoreResult> afterResults = resultRepository.findAll();
        assertThat(afterResults).hasSize(5);
        assertThat(afterResults).extracting(ScoreResult::getId)
                .doesNotContainNull();
        assertThat(afterResults).extracting(ScoreResult::getScoreItemId)
                .containsExactlyInAnyOrderElementsOf(oldItemIds);
        assertThat(afterResults).extracting(ScoreResult::getActualScore).isEqualTo(actualScores);
        assertThat(afterResults).extracting(ScoreResult::getScoringTaskId).containsOnly(77L);
        // 3) 旧正式结果依赖的证据不被破坏（旧任务 fact/evidence 仍在）
        assertThat(factRepository.findByParseTaskId(oldTask.getId()))
                .extracting(ScoreParseFact::getFactId).containsExactly("old-fact-1");
        assertThat(evidenceRepository.findByParseTaskId(oldTask.getId()))
                .extracting(ScoreParseEvidence::getCellId).containsExactly("old-c1");
        assertThat(oldFact.getId()).isNotNull();
        // 4) 本次复核候选按新任务可读
        List<ScoreParseFact> newFacts = factRepository.findByParseTaskId(newTask.getId());
        assertThat(newFacts).isNotEmpty();
        assertThat(newFacts).extracting(ScoreParseFact::getSourceTableId)
                .contains("tech-t1", "biz-t2");
        assertThat(evidenceRepository.findByParseTaskId(newTask.getId())).isNotEmpty();
        // 5) 未触发对部分候选的预计得分计算；决策保持需要复核
        verify(estimatedScoreService, never()).estimateForProject(any());
        assertThat(newTask.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
    }

    @Test
    void partialApproval_withoutExistingOfficialItems_keepsOfficialItemsEmpty() {
        // 回归E：项目无旧评分项 → 部分通过只作复核候选，正式评分项保持为空
        Long projectId = 9102L;
        ScoreParseTask newTask = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("fresh-partial-task").projectId(projectId).taskType("PARSE")
                .status("PROCESSING").build());
        StructuredTableResult structured = extract(partialApprovalArtifact());

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                newTask, newTask.getTaskId(), structured, taskRepository, stateService,
                progressService, persistenceService, estimatedScoreService);

        assertThat(itemRepository.findByProjectIdOrderByItemIndexAsc(projectId)).isEmpty();
        assertThat(factRepository.findByParseTaskId(newTask.getId())).isNotEmpty();
        assertThat(newTask.getDecisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED.name());
        verify(estimatedScoreService, never()).estimateForProject(any());
    }

    @Test
    void fullApproval_publishesFiveItemsAndTriggersEstimate() {
        // 回归F：两表全通过 → 正常发布 5 项 100 分并触发预计得分计算
        Long projectId = 9103L;
        ScoreParseTask task = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("full-approve-task").projectId(projectId).taskType("PARSE")
                .status("PROCESSING").build());
        StructuredTableResult structured = extract(twoTableArtifact());

        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                task, task.getTaskId(), structured, taskRepository, stateService,
                progressService, persistenceService, estimatedScoreService);

        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(items).hasSize(5);
        assertThat(items).extracting(i -> i.getWeight().stripTrailingZeros().toPlainString())
                .containsExactly("30", "20", "10", "20", "20");
        assertThat(items.stream().map(ScoreItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(task.getDecisionStatus()).isEqualTo(ScoreParseDecision.AUTO_APPROVED.name());
        verify(estimatedScoreService, times(1)).estimateForProject(projectId);
    }

    @Test
    void reviewCandidateFacts_isolatedByParseTask_andIdempotentPerTask() {
        // 回归G：同项目两个复核任务互不串任务；同任务重复保存不产生重复事实；其他项目不受影响
        Long projectId = 9104L;
        Long otherProjectId = 9105L;
        ScoreParseTask taskA = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("review-task-a").projectId(projectId).taskType("PARSE")
                .status("COMPLETED").build());
        ScoreParseTask taskB = taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("review-task-b").projectId(projectId).taskType("PARSE")
                .status("COMPLETED").build());
        taskRepository.saveAndFlush(ScoreParseTask.builder()
                .taskId("other-project-task").projectId(otherProjectId).taskType("PARSE")
                .status("COMPLETED").build());
        persistenceService.persistFactsAndEvidence(projectId, taskA.getId(),
                List.of(fact("fact-a1", "table-a")));
        // 其他项目先落一份事实，验证本项目后续保存不波及
        persistenceService.persistFactsAndEvidence(otherProjectId,
                taskRepository.findByTaskId("other-project-task").orElseThrow().getId(),
                List.of(fact("fact-other-1", "table-other")));

        persistenceService.persistFactsAndEvidence(projectId, taskB.getId(),
                List.of(fact("fact-b1", "table-b"), fact("fact-b2", "table-b")));
        // 同任务重复保存：先删本任务再插，不产生重复事实
        persistenceService.persistFactsAndEvidence(projectId, taskB.getId(),
                List.of(fact("fact-b1", "table-b"), fact("fact-b2", "table-b")));

        assertThat(factRepository.findByParseTaskId(taskA.getId()))
                .extracting(ScoreParseFact::getFactId).containsExactly("fact-a1");
        assertThat(factRepository.findByParseTaskId(taskB.getId()))
                .extracting(ScoreParseFact::getFactId).containsExactly("fact-b1", "fact-b2");
        Long otherTaskId = taskRepository.findByTaskId("other-project-task").orElseThrow().getId();
        assertThat(factRepository.findByParseTaskId(otherTaskId))
                .extracting(ScoreParseFact::getFactId).containsExactly("fact-other-1");
        assertThat(itemRepository.findByProjectIdOrderByItemIndexAsc(projectId)).isEmpty();
        assertThat(itemRepository.findByProjectIdOrderByItemIndexAsc(otherProjectId)).isEmpty();
    }

    // ===== 夹具 =====

    private com.xiyu.bid.scoreparse.domain.ScoreFact fact(String factId, String tableId) {
        String quoted = "得30分";
        com.xiyu.bid.scoreparse.domain.ScoreEvidence ev =
                new com.xiyu.bid.scoreparse.domain.ScoreEvidence(
                        com.xiyu.bid.scoreparse.domain.ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                        quoted, tableId + "-c1", 0, quoted.length(), "明确分值");
        return new com.xiyu.bid.scoreparse.domain.ScoreFact(
                factId, tableId, List.of(tableId + "-c1"), "技术方案 30分", "1", "技术方案",
                bd("30"), ev, null, "", "DETERMINISTIC", 1.0,
                com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(2);
    }

    /** 与 StructuredTableExtractionMarkCompletedTest 相同的真实 extract 路径（mock Sidecar 边界）。 */
    private static StructuredTableResult extract(DocumentArtifact artifact) {
        byte[] pdfBytes = "%PDF-1.4 partial-overwrite".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "partial-overwrite.pdf", "memory://partial-overwrite.pdf", "评分标准由两张独立评分表组成。");
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

    /** 技术表完整（60 分）+ 商务表真实缺项（声明 40 仅抽到 20）→ 部分通过。 */
    private static DocumentArtifact partialApprovalArtifact() {
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
                new DocumentArtifact.ArtifactMeta("sha256:po-partial", "po-partial", "PDF", "pdf-table-v1"),
                List.of(scoreTable("tech-t1", technicalRows), scoreTable("biz-t2", commercialRows)),
                List.of());
    }

    /** 双表完整（60 分 + 40 分声明各自自洽）→ 全通过。 */
    private static DocumentArtifact twoTableArtifact() {
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
                new DocumentArtifact.ArtifactMeta("sha256:po-full", "po-full", "PDF", "pdf-table-v1"),
                List.of(scoreTable("tech-t1", technicalRows), scoreTable("biz-t2", commercialRows)),
                List.of());
    }

    private static DocumentArtifact.CanonicalTable scoreTable(String tableId, List<List<String>> rows) {
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
                tableId, 1, "", rows.size(), rows.get(0).size(), List.of(), cells, true);
    }
}
