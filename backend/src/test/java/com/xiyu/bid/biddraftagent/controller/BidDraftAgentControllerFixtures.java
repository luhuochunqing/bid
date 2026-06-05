package com.xiyu.bid.biddraftagent.controller;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.domain.validation.BrandAuthMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult.KnowledgeBaseSummary;
import com.xiyu.bid.biddraftagent.domain.validation.PerformanceMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.PersonnelCertMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchResult;
import com.xiyu.bid.biddraftagent.application.CommercialClassificationAppService;
import com.xiyu.bid.biddraftagent.application.FullAnalysisAppService;
import com.xiyu.bid.biddraftagent.application.RiskClassificationAppService;
import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService;
import com.xiyu.bid.biddraftagent.application.TechnicalClassificationAppService;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentApplyResponseDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentArtifactDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentCreateRunRequest;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentReviewDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentRunDTO;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentDTO;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentParseDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class BidDraftAgentControllerFixtures {

    private BidDraftAgentControllerFixtures() {
    }

    static BidDraftAgentRunDTO sampleRunDto() {
        return BidDraftAgentRunDTO.builder()
                .id(100L)
                .projectId(11L)
                .tenderId(22L)
                .projectName("华东智慧园区改造项目")
                .tenderTitle("2026园区改造招标公告")
                .status("DRAFTED")
                .snapshot(sampleSnapshot())
                .requirementClassification(new RequirementClassification(
                        List.of("价格"),
                        List.of("合同"),
                        List.of("资质"),
                        List.of("技术"),
                        List.of("交付"),
                        List.of("商务"),
                        List.of("项目背景")
                ))
                .materialMatchScore(sampleMaterialMatchScore())
                .gapCheck(new GapCheckResult(true, List.of(), List.of()))
                .manualConfirmation(new ManualConfirmationDecision(true, true, true, List.of("需要人工确认")))
                .writeCoverage(new WriteCoverageDecision(100, true,
                        List.of("项目概况", "商务响应"), List.of(), List.of("项目概况")))
                .draftText("draft text")
                .reviewText("review text")
                .artifacts(List.of(BidDraftAgentArtifactDTO.builder()
                        .id(200L)
                        .runId(100L)
                        .artifactType("DRAFT_TEXT")
                        .title("自动生成投标草稿")
                        .content("draft text")
                        .handoffTarget("document-writer")
                        .status("DRAFTED")
                        .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                        .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                        .build()))
                .reviewedAt(LocalDateTime.of(2026, 4, 22, 11, 0))
                .appliedAt(null)
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();
    }

    static BidTenderDocumentParseDTO sampleParseDto() {
        return BidTenderDocumentParseDTO.builder()
                .document(BidTenderDocumentDTO.builder()
                        .id(501L)
                        .projectId(11L)
                        .tenderId(22L)
                        .name("file")
                        .fileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .snapshotId(601L)
                        .extractedTextLength(1200)
                        .build())
                .requirementProfile(new TenderRequirementProfile(
                        "华东智慧园区改造项目",
                        "2026园区改造招标公告",
                        "智慧园区建设",
                        "上海采购集团",
                        List.of("提供有效资质证书"),
                        List.of("提供实施方案"),
                        List.of("响应商务条款"),
                        List.of("技术方案50分"),
                        "2026-05-30",
                        List.of("投标函", "授权书"),
                        List.of("报价需人工确认"),
                        List.of("智慧园区"),
                        List.of()
                ))
                .message("招标文件已解析，已更新招标要求快照")
                .build();
    }

    static BidDraftAgentCreateRunRequest sampleCreateRunRequest() {
        return new BidDraftAgentCreateRunRequest(601L);
    }

    static BidDraftAgentReviewDTO sampleReviewDto() {
        return BidDraftAgentReviewDTO.builder()
                .runId(100L)
                .projectId(11L)
                .status("REVIEWED")
                .reviewSummary("updated review")
                .draftText("draft text")
                .requirementClassification(new RequirementClassification(
                        List.of("价格"),
                        List.of("合同"),
                        List.of("资质"),
                        List.of("技术"),
                        List.of("交付"),
                        List.of("商务"),
                        List.of("项目背景")
                ))
                .materialMatchScore(sampleMaterialMatchScore())
                .gapCheck(new GapCheckResult(true, List.of(), List.of()))
                .manualConfirmation(new ManualConfirmationDecision(true, true, true, List.of("需要人工确认")))
                .writeCoverage(new WriteCoverageDecision(100, true,
                        List.of("项目概况", "商务响应"), List.of(), List.of("项目概况")))
                .nextActions(List.of("项目概况", "商务响应"))
                .reviewedAt(LocalDateTime.of(2026, 4, 22, 11, 0))
                .build();
    }

    private static BidDraftSnapshot sampleSnapshot() {
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

    private static MaterialMatchScore sampleMaterialMatchScore() {
        return new MaterialMatchScore(100, 6, 6,
                List.of("pricing", "legal", "qualification", "technical", "delivery", "commercial"),
                List.of(), List.of(), List.of());
    }

    static QualificationMatchResult emptyQualificationMatchResult() {
        return new QualificationMatchResult(List.of());
    }

    static TechnicalClassificationAppService.TechnicalClassificationResult emptyTechnicalResult() {
        return new TechnicalClassificationAppService.TechnicalClassificationResult(List.of());
    }

    static CommercialClassificationAppService.CommercialClassificationResult emptyCommercialResult() {
        return new CommercialClassificationAppService.CommercialClassificationResult(List.of());
    }

    static RiskClassificationAppService.RiskClassificationResult emptyRiskResult() {
        return new RiskClassificationAppService.RiskClassificationResult(List.of());
    }

    static ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult emptyScoringResult() {
        return ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult.empty();
    }

    static KnowledgeBaseMatchResult emptyKnowledgeBaseMatchResult() {
        return new KnowledgeBaseMatchResult(
                new QualificationMatchResult(List.of()),
                new PersonnelCertMatcher.PersonnelMatchResult(List.of()),
                new BrandAuthMatcher.BrandAuthMatchResult(List.of()),
                new PerformanceMatcher.PerformanceMatchResult(List.of()),
                new KnowledgeBaseSummary(0, 0, 0));
    }

    static FullAnalysisAppService.FullAnalysisResult emptyFullAnalysisResult() {
        return new FullAnalysisAppService.FullAnalysisResult(
                emptyKnowledgeBaseMatchResult(),
                emptyScoringResult(),
                emptyTechnicalResult(),
                emptyCommercialResult(),
                emptyRiskResult(),
                new FullAnalysisAppService.RiskSummary(0, 0, 0));
    }
}
