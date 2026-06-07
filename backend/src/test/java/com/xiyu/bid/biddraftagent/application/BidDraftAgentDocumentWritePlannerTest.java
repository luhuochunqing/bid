package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidDraftAgentDocumentWritePlannerTest {

    private final BidDraftAgentDocumentWritePlanner planner = new BidDraftAgentDocumentWritePlanner();

    @Test
    void plan_shouldPreserveSourceConfidenceAndManualBoundaries() {
        BidAgentRun run = BidAgentRun.builder()
                .id(100L)
                .projectId(11L)
                .projectName("华东智慧园区改造项目")
                .tenderTitle("2026园区改造招标公告")
                .generatorKey("openai-responses-v1")
                .build();
        List<BidAgentArtifact> artifacts = List.of(
                BidAgentArtifact.builder()
                        .id(200L)
                        .artifactType("DRAFT_TEXT")
                        .content("draft text")
                        .build(),
                BidAgentArtifact.builder()
                        .id(201L)
                        .artifactType("HANDOFF_CHECKLIST")
                        .content("人工确认: 需要")
                        .build()
        );

        BidDraftAgentDocumentWritePlan plan = planner.plan(
                run,
                artifacts,
                new GapCheckResult(false, List.of("材料覆盖度不足"), List.of("补充案例")),
                new ManualConfirmationDecision(true, true, true, List.of("价格与法务需确认")),
                new WriteCoverageDecision(73, false, List.of("项目概况"), List.of("报价"), List.of("人工确认后再提交"))
        );

        assertThat(plan.structureName()).isEqualTo("AI 标书初稿");
        assertThat(plan.runId()).isEqualTo("100");
        assertThat(plan.sections()).hasSize(1);

        BidDraftAgentDocumentSection root = plan.sections().get(0);
        assertThat(root.manual()).isTrue();
        assertThat(root.confidence()).isEqualByComparingTo(new BigDecimal("0.73"));
        assertThat(root.sourceReferences()).contains("bid-agent-run:100", "bid-agent-artifact:200");
        assertThat(root.content()).contains("人工确认: 需要", "材料覆盖度不足");
        assertThat(root.children()).extracting(BidDraftAgentDocumentSection::title)
                .containsExactly("标书初稿正文", "缺漏与人工确认清单");
    }
}
