// Input: TenderTextSource + Sidecar 表格客户端
// Output: 结构化表格候选，或结构缺失保留策略所需的失败标记
// Pos: scoreparse/application — docx 结构化表格通道与结构缺失任务收口
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
final class StructuredTableExtractionHandler {

    private StructuredTableExtractionHandler() {
    }

    static Result extract(TenderTextSource source,
                          InitiationTenderTextResolver textResolver,
                          SidecarTableExtractor sidecarTableExtractor) {
        String fileName = source.fileName();
        if (fileName == null || !fileName.toLowerCase().endsWith(".docx")) {
            return Result.ok(List.of());
        }
        try {
            byte[] content = textResolver.loadBytes(source.fileUrl());
            if (content == null || content.length == 0) {
                return Result.ok(List.of());
            }
            var report = StructuredTableCandidateExtractor.extractWithSectionReport(
                    sidecarTableExtractor.extract(content, fileName));
            if (!report.sectionDimMissTables().isEmpty()) {
                log.warn("结构化表格章节推导缺失（疑似新措辞独立分表，需人工复核）: file={}, missTables={}",
                        fileName, report.sectionDimMissTables());
            }
            return Result.ok(report.candidates());
        } catch (RuntimeException | IOException exception) {
            log.warn("结构化表格提取失败，标记结构不完整，不走 markdown: file={}, msg={}",
                    fileName, exception.getMessage());
            return Result.structureIncomplete("STRUCTURE_DEFECT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("结构化表格提取被中断: file={}", fileName);
            return Result.structureIncomplete("STRUCTURE_DEFECT");
        }
    }

    static void markIncomplete(ScoreParseTask task,
                               String taskId,
                               String failureReason,
                               ScoreParseTaskRepository taskRepository,
                               ScoreParseTaskStateService stateService,
                               ScoreParseProgressService progressService) {
        String reason = (failureReason == null || failureReason.isBlank()) ? "STRUCTURE_DEFECT" : failureReason;
        task.setRunSummary(RunSummaryAssembler.assemble(
                new RecallOutcome(List.of(), 0),
                0,
                new RunSummaryAssembler.MergeView(false, false, 0, 0, 0),
                new WeightSumCheck.Result(BigDecimal.ZERO, true, true, Map.of(), Map.of()),
                0,
                "",
                ScoreParsePipeline.LEGACY_FALLBACK,
                ScoreParseDecision.STRUCTURE_INCOMPLETE.name(),
                reason).json());
        task.setPipelineVersion(ScoreParsePipeline.LEGACY_FALLBACK);
        task.setDecisionStatus(ScoreParseDecision.STRUCTURE_INCOMPLETE.name());
        task.setFallbackReason(reason);
        taskRepository.save(task);
        stateService.updateProgress(taskId, 85, "结构化异常，保留现有评分项不覆盖");
        progressService.updateProgress(taskId,
                new com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO(
                        taskId, "PROCESSING", 85, "结构化异常，保留现有评分项不覆盖", null, null, null));
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    record Result(List<ScoreCandidate> candidates, boolean structureIncomplete, String failureReason) {

        static Result ok(List<ScoreCandidate> candidates) {
            return new Result(candidates, false, null);
        }

        static Result structureIncomplete(String failureReason) {
            return new Result(List.of(), true, failureReason);
        }
    }
}
