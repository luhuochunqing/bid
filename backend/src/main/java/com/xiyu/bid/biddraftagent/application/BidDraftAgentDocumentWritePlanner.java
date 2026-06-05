package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BidDraftAgentDocumentWritePlanner {

    private static final String STRUCTURE_NAME = "AI 标书初稿";

    public BidDraftAgentDocumentWritePlan plan(
            BidAgentRun run,
            List<BidAgentArtifact> artifacts,
            GapCheckResult gapCheck,
            ManualConfirmationDecision manualConfirmation,
            WriteCoverageDecision writeCoverage
    ) {
        BidAgentArtifact draftArtifact = primaryDraftArtifact(artifacts);
        boolean manual = manualConfirmation.requiresConfirmation();
        BigDecimal confidence = BigDecimal.valueOf(writeCoverage.coverageScore()).movePointLeft(2);
        List<String> sourceReferences = sourceReferences(run, draftArtifact);

        List<BidDraftAgentDocumentSection> children = new ArrayList<>();
        children.add(new BidDraftAgentDocumentSection(
                "bid-agent-run-" + run.getId() + "-draft-text",
                "标书初稿正文",
                draftArtifact.getContent(),
                sourceReferences,
                confidence,
                manual,
                List.of()
        ));
        artifacts.stream()
                .filter(artifact -> "HANDOFF_CHECKLIST".equalsIgnoreCase(artifact.getArtifactType()))
                .findFirst()
                .ifPresent(artifact -> children.add(new BidDraftAgentDocumentSection(
                        "bid-agent-run-" + run.getId() + "-handoff-checklist",
                        "缺漏与人工确认清单",
                        artifact.getContent(),
                        sourceReferences(run, artifact),
                        confidence,
                        true,
                        List.of()
                )));

        return new BidDraftAgentDocumentWritePlan(
                String.valueOf(run.getId()),
                STRUCTURE_NAME,
                List.of(new BidDraftAgentDocumentSection(
                        "bid-agent-run-" + run.getId(),
                        "AI 标书初稿",
                        summaryContent(run, gapCheck, manualConfirmation, writeCoverage),
                        sourceReferences,
                        confidence,
                        manual,
                        children
                ))
        );
    }

    private BidAgentArtifact primaryDraftArtifact(List<BidAgentArtifact> artifacts) {
        return artifacts.stream()
                .filter(artifact -> "DRAFT_TEXT".equalsIgnoreCase(artifact.getArtifactType()))
                .findFirst()
                .orElse(artifacts.get(0));
    }

    private List<String> sourceReferences(BidAgentRun run, BidAgentArtifact artifact) {
        return List.of(
                "bid-agent-run:" + run.getId(),
                "bid-agent-artifact:" + artifact.getId(),
                "generator:" + run.getGeneratorKey()
        );
    }

    private String summaryContent(
            BidAgentRun run,
            GapCheckResult gapCheck,
            ManualConfirmationDecision manualConfirmation,
            WriteCoverageDecision writeCoverage
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("项目名称: " + run.getProjectName());
        lines.add("标讯标题: " + run.getTenderTitle());
        lines.add("覆盖评分: " + writeCoverage.coverageScore());
        lines.add("可直接写作: " + (writeCoverage.sufficient() ? "是" : "否"));
        lines.add("人工确认: " + (manualConfirmation.requiresConfirmation() ? "需要" : "不需要"));
        if (!manualConfirmation.reasons().isEmpty()) {
            lines.add("确认原因: " + String.join("；", manualConfirmation.reasons()));
        }
        if (!gapCheck.gaps().isEmpty()) {
            lines.add("缺漏项: " + String.join("；", gapCheck.gaps()));
        }
        if (!gapCheck.suggestions().isEmpty()) {
            lines.add("建议补充: " + String.join("；", gapCheck.suggestions()));
        }
        return String.join("\n", lines);
    }
}
