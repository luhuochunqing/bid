package com.xiyu.bid.biddraftagent.controller;

import com.xiyu.bid.biddraftagent.application.FullAnalysisAppService;
import com.xiyu.bid.biddraftagent.application.KnowledgeBaseMatchAppService;
import com.xiyu.bid.biddraftagent.application.BidDraftAgentAppService;
import com.xiyu.bid.biddraftagent.application.BidTenderDocumentImportAppService;
import com.xiyu.bid.biddraftagent.application.CommercialClassificationAppService;
import com.xiyu.bid.biddraftagent.application.QualificationMatchAppService;
import com.xiyu.bid.biddraftagent.application.RiskClassificationAppService;
import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService;
import com.xiyu.bid.biddraftagent.application.TechnicalClassificationAppService;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchResult;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentApplyResponseDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentCreateRunRequest;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentReviewDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentRunDTO;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentParseDTO;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/bid-agent")
@RequiredArgsConstructor
public class BidDraftAgentController {

    private final BidDraftAgentAppService bidDraftAgentAppService;
    private final BidTenderDocumentImportAppService bidTenderDocumentImportAppService;
    private final QualificationMatchAppService qualificationMatchAppService;
    private final TechnicalClassificationAppService technicalClassificationAppService;
    private final CommercialClassificationAppService commercialClassificationAppService;
    private final RiskClassificationAppService riskClassificationAppService;
    private final ScoringCriteriaClassificationAppService scoringCriteriaClassificationAppService;
    private final KnowledgeBaseMatchAppService knowledgeBaseMatchAppService;
    private final FullAnalysisAppService fullAnalysisAppService;

    @PostMapping(value = "/tender-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidTenderDocumentParseDTO>> importTenderDocument(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        BidTenderDocumentParseDTO result = bidTenderDocumentImportAppService.parseTenderDocument(projectId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.getMessage(), result));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidDraftAgentRunDTO>> createRun(
            @PathVariable Long projectId,
            @RequestBody(required = false) BidDraftAgentCreateRunRequest request) {
        Long snapshotId = request == null ? null : request.snapshotId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("草稿运行已创建", bidDraftAgentAppService.createRun(projectId, snapshotId)));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidDraftAgentRunDTO>> getRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        return ResponseEntity.ok(ApiResponse.success(bidDraftAgentAppService.getRun(projectId, runId)));
    }

    @PostMapping("/runs/{runId}/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidDraftAgentApplyResponseDTO>> applyRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        return ResponseEntity.ok(ApiResponse.success("草稿产物已准备交给文档写手", bidDraftAgentAppService.applyRun(projectId, runId)));
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidDraftAgentReviewDTO>> reviewCurrentDraft(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success("草稿审阅完成", bidDraftAgentAppService.reviewCurrentDraft(projectId)));
    }

    @PostMapping("/runs/{runId}/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<BidDraftAgentReviewDTO>> reviewRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        return ResponseEntity.ok(ApiResponse.success("草稿审阅完成", bidDraftAgentAppService.reviewRun(projectId, runId)));
    }

    @GetMapping("/qualification-match")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<QualificationMatchResult>> getQualificationMatch(
            @PathVariable Long projectId) {
        QualificationMatchResult result = qualificationMatchAppService.matchForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("资质匹配完成", result));
    }

    @GetMapping("/technical-requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<TechnicalClassificationAppService.TechnicalClassificationResult>> getTechnicalRequirements(
            @PathVariable Long projectId) {
        var result = technicalClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("技术要点分类完成", result));
    }

    @GetMapping("/commercial-requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CommercialClassificationAppService.CommercialClassificationResult>> getCommercialRequirements(
            @PathVariable Long projectId) {
        var result = commercialClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("商务条款分类完成", result));
    }

    @GetMapping("/risk-classification")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RiskClassificationAppService.RiskClassificationResult>> getRiskClassification(
            @PathVariable Long projectId) {
        var result = riskClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("风险分类完成", result));
    }

    @GetMapping("/scoring-criteria")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult>> getScoringCriteria(
            @PathVariable Long projectId) {
        var result = scoringCriteriaClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("评分标准解析完成", result));
    }

    @GetMapping("/knowledge-base-match")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<KnowledgeBaseMatchResult>> getKnowledgeBaseMatch(
            @PathVariable Long projectId) {
        var result = knowledgeBaseMatchAppService.matchForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("四库联动匹配完成", result));
    }

    @GetMapping("/full-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<FullAnalysisAppService.FullAnalysisResult>> getFullAnalysis(
            @PathVariable Long projectId) {
        var result = fullAnalysisAppService.analyzeForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("全维度分析完成", result));
    }
}
