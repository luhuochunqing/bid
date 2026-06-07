package com.xiyu.bid.biddraftagent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class BidDraftAgentAppServiceFixtures {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private BidDraftAgentAppServiceFixtures() {
    }

    static BidDraftSnapshot sampleSnapshot() {
        return new BidDraftSnapshot(
                11L,
                22L,
                "华东智慧园区改造项目",
                "项目需要报价、合同法务条款、实施方案和交付验收材料",
                "已确认招标背景和客户范围",
                "西域智算中心",
                "重点客户",
                "上海",
                "信息化",
                new BigDecimal("5000000"),
                LocalDate.of(2026, 5, 30),
                "2026园区改造招标公告",
                "投标要求包含资质、报价、合同和实施计划",
                "上海采购集团",
                "公开招标",
                List.of("智慧园区", "改造"),
                List.of("technical / 必须响应 / 实施方案 / 包含部署、运维和培训"),
                List.of("material / 必须响应 / 资质证书 / 提供有效资质证书"),
                List.of("scoring / 参考 / 技术方案评分 / 技术方案占50分"),
                List.of("建筑业企业资质证书 / CONSTRUCTION / FIRST"),
                List.of("法务合同模板 / LEGAL / 投标说明"),
                List.of("智慧园区实施案例 / 方案 / 交付验收 / 售后支持")
        );
    }

    static BidAgentRun baseRun(BidDraftSnapshot snapshot) throws Exception {
        RequirementClassification classification = new RequirementClassification(
                List.of("价格"),
                List.of("合同"),
                List.of("资质"),
                List.of("技术"),
                List.of("交付"),
                List.of("商务"),
                List.of("项目背景")
        );
        MaterialMatchScore materialMatchScore = new MaterialMatchScore(100, 6, 6,
                List.of("pricing", "legal", "qualification", "technical", "delivery", "commercial"),
                List.of(), List.of(), List.of());
        GapCheckResult gapCheck = new GapCheckResult(true, List.of(), List.of());
        ManualConfirmationDecision manualConfirmation = new ManualConfirmationDecision(true, true, true, List.of("需要人工确认"));
        WriteCoverageDecision writeCoverage = new WriteCoverageDecision(100, true,
                List.of("项目概况", "商务响应"), List.of(), List.of("项目概况"));

        return BidAgentRun.builder()
                .projectId(snapshot.projectId())
                .tenderId(snapshot.tenderId())
                .projectName(snapshot.projectName())
                .tenderTitle(snapshot.tenderTitle())
                .status(BidAgentRun.Status.DRAFTED)
                .snapshotJson(OBJECT_MAPPER.writeValueAsString(snapshot))
                .requirementClassificationJson(OBJECT_MAPPER.writeValueAsString(classification))
                .materialMatchScoreJson(OBJECT_MAPPER.writeValueAsString(materialMatchScore))
                .gapCheckJson(OBJECT_MAPPER.writeValueAsString(gapCheck))
                .manualConfirmationJson(OBJECT_MAPPER.writeValueAsString(manualConfirmation))
                .writeCoverageJson(OBJECT_MAPPER.writeValueAsString(writeCoverage))
                .draftText("draft text")
                .reviewText("review text")
                .generatorKey("openai-responses-v1")
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();
    }
}
