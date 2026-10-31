// Input: ACTIVE 案例快照、项目元数据与可用原始标书
// Output: 只补不清的回填计划，不执行任何持久化
// Pos: casework 应用纯编排——生产 preview 的安全决策器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.application.MarkdownSourceSectionIndex;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.repository.ProjectRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CaseProvenancePreviewPlanner {
    private final ProjectRepository projects;
    private final BidDocumentLoader loader;

    CaseProvenancePreviewPlanner(ProjectRepository projects, BidDocumentLoader loader) {
        this.projects = projects;
        this.loader = loader;
    }

    CaseProvenanceBackfillPlan plan(List<Candidate> candidates) {
        List<CaseProvenanceBackfillPlan.Decision> decisions = new ArrayList<>();
        Map<Long, List<Candidate>> emptyByProject = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (hasAnyProvenance(candidate)) {
                decisions.add(decision(candidate, CaseProvenanceBackfillPlan.DecisionType.SKIP_EXISTING,
                        "existing provenance is preserved", null));
            } else if (candidate.sourceProjectId() == null) {
                decisions.add(decision(candidate, CaseProvenanceBackfillPlan.DecisionType.ORPHAN,
                        "source project id is missing", null));
            } else {
                emptyByProject.computeIfAbsent(candidate.sourceProjectId(), ignored -> new ArrayList<>()).add(candidate);
            }
        }
        for (Map.Entry<Long, List<Candidate>> entry : emptyByProject.entrySet()) {
            planProject(entry.getKey(), entry.getValue(), decisions);
        }
        decisions.sort(java.util.Comparator.comparing(CaseProvenanceBackfillPlan.Decision::caseId));
        return new CaseProvenanceBackfillPlan(decisions);
    }

    private void planProject(Long projectId, List<Candidate> candidates,
                             List<CaseProvenanceBackfillPlan.Decision> decisions) {
        BidDocumentLoader.BidDocument document;
        try {
            String projectName = projects.findById(projectId)
                    .map(project -> project.getName() == null ? "项目-" + projectId : project.getName())
                    .orElse("项目-" + projectId);
            document = loader.loadBidDocument(projectName, projectId);
        } catch (RuntimeException exception) {
            unavailable(candidates, decisions, "document load failed");
            return;
        }
        if (document == null || !document.available()) {
            unavailable(candidates, decisions, "document is unavailable");
            return;
        }
        MarkdownSourceSectionIndex index;
        try {
            index = MarkdownSourceSectionIndex.parse(document.markdown());
        } catch (RuntimeException exception) {
            unavailable(candidates, decisions, "document section parsing failed");
            return;
        }
        if (index.getSections().isEmpty()) {
            unavailable(candidates, decisions, "document has no valid sections");
            return;
        }
        for (Candidate candidate : candidates) {
            MarkdownSourceSectionIndex.Provenance provenance = locate(candidate, index);
            if (provenance == null) {
                decisions.add(decision(candidate, CaseProvenanceBackfillPlan.DecisionType.UNRESOLVED,
                        "source text cannot be uniquely located", null));
                continue;
            }
            Integer page = provenance.pageNumber() != null ? provenance.pageNumber() : document.pageNumber();
            var proposed = new CaseProvenanceBackfillPlan.Proposed(
                    provenance.title(), provenance.path(), provenance.index(), document.documentName(), page);
            decisions.add(decision(candidate, CaseProvenanceBackfillPlan.DecisionType.FILLABLE,
                    "unique source section verified", proposed));
        }
    }

    private static MarkdownSourceSectionIndex.Provenance locate(
            Candidate candidate, MarkdownSourceSectionIndex index) {
        if (candidate.responseText() == null || candidate.responseText().isBlank()) {
            return null;
        }
        try {
            return index.locate(candidate.responseText(), candidate.scoringPointTitle());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void unavailable(List<Candidate> candidates,
                                    List<CaseProvenanceBackfillPlan.Decision> decisions, String reason) {
        for (Candidate candidate : candidates) {
            decisions.add(decision(candidate, CaseProvenanceBackfillPlan.DecisionType.PROJECT_UNAVAILABLE,
                    reason, null));
        }
    }

    private static CaseProvenanceBackfillPlan.Decision decision(
            Candidate candidate, CaseProvenanceBackfillPlan.DecisionType type,
            String reason, CaseProvenanceBackfillPlan.Proposed proposed) {
        return new CaseProvenanceBackfillPlan.Decision(
                candidate, type, reason, proposed, sha256(candidate.responseText()));
    }

    private static boolean hasAnyProvenance(Candidate candidate) {
        return !blank(candidate.sourceSectionTitle())
                || !blank(candidate.sourceSectionPath())
                || candidate.sourceSectionIndex() != null
                || !blank(candidate.sourceDocumentName())
                || candidate.sourcePageNumber() != null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
