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
import lombok.extern.slf4j.Slf4j;
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
@PreAuthorize("isAuthenticated()")
@Slf4j
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidTenderDocumentParseDTO>> importTenderDocument(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        log.info("BidAgent importTenderDocument: projectId={}, fileName={}, fileSize={}",
                projectId, file.getOriginalFilename(), file.getSize());
        try {
            BidTenderDocumentParseDTO result = bidTenderDocumentImportAppService.parseTenderDocument(projectId, file);
            log.info("BidAgent importTenderDocument success: projectId={}, message={}",
                    projectId, result.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(result.getMessage(), result));
        } catch (RuntimeException ex) {
            log.error("BidAgent importTenderDocument failed: projectId={}, fileName={}",
                    projectId, file.getOriginalFilename(), ex);
            throw ex;
        }
    }

    @PostMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDraftAgentRunDTO>> createRun(
            @PathVariable Long projectId,
            @RequestBody(required = false) BidDraftAgentCreateRunRequest request) {
        Long snapshotId = request == null ? null : request.snapshotId();
        log.info("BidAgent createRun: projectId={}, snapshotId={}", projectId, snapshotId);
        try {
            BidDraftAgentRunDTO result = bidDraftAgentAppService.createRun(projectId, snapshotId);
            log.info("BidAgent createRun success: projectId={}, runId={}", projectId, result.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("草稿运行已创建", result));
        } catch (RuntimeException ex) {
            log.error("BidAgent createRun failed: projectId={}", projectId, ex);
            throw ex;
        }
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDraftAgentRunDTO>> getRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        log.info("BidAgent getRun: projectId={}, runId={}", projectId, runId);
        return ResponseEntity.ok(ApiResponse.success(bidDraftAgentAppService.getRun(projectId, runId)));
    }

    @PostMapping("/runs/{runId}/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDraftAgentApplyResponseDTO>> applyRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        log.info("BidAgent applyRun: projectId={}, runId={}", projectId, runId);
        try {
            BidDraftAgentApplyResponseDTO result = bidDraftAgentAppService.applyRun(projectId, runId);
            log.info("BidAgent applyRun success: projectId={}, runId={}", projectId, runId);
            return ResponseEntity.ok(ApiResponse.success("草稿产物已准备交给文档写手", result));
        } catch (RuntimeException ex) {
            log.error("BidAgent applyRun failed: projectId={}, runId={}", projectId, runId, ex);
            throw ex;
        }
    }

    @PostMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDraftAgentReviewDTO>> reviewCurrentDraft(@PathVariable Long projectId) {
        log.info("BidAgent reviewCurrentDraft: projectId={}", projectId);
        try {
            BidDraftAgentReviewDTO result = bidDraftAgentAppService.reviewCurrentDraft(projectId);
            log.info("BidAgent reviewCurrentDraft success: projectId={}", projectId);
            return ResponseEntity.ok(ApiResponse.success("草稿审阅完成", result));
        } catch (RuntimeException ex) {
            log.error("BidAgent reviewCurrentDraft failed: projectId={}", projectId, ex);
            throw ex;
        }
    }

    @PostMapping("/runs/{runId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BidDraftAgentReviewDTO>> reviewRun(
            @PathVariable Long projectId,
            @PathVariable Long runId) {
        log.info("BidAgent reviewRun: projectId={}, runId={}", projectId, runId);
        try {
            BidDraftAgentReviewDTO result = bidDraftAgentAppService.reviewRun(projectId, runId);
            log.info("BidAgent reviewRun success: projectId={}, runId={}", projectId, runId);
            return ResponseEntity.ok(ApiResponse.success("草稿审阅完成", result));
        } catch (RuntimeException ex) {
            log.error("BidAgent reviewRun failed: projectId={}, runId={}", projectId, runId, ex);
            throw ex;
        }
    }

    @GetMapping("/qualification-match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<QualificationMatchResult>> getQualificationMatch(
            @PathVariable Long projectId) {
        log.info("BidAgent getQualificationMatch: projectId={}", projectId);
        QualificationMatchResult result = qualificationMatchAppService.matchForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("资质匹配完成", result));
    }

    @GetMapping("/technical-requirements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TechnicalClassificationAppService.TechnicalClassificationResult>> getTechnicalRequirements(
            @PathVariable Long projectId) {
        log.info("BidAgent getTechnicalRequirements: projectId={}", projectId);
        var result = technicalClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("技术要点分类完成", result));
    }

    @GetMapping("/commercial-requirements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommercialClassificationAppService.CommercialClassificationResult>> getCommercialRequirements(
            @PathVariable Long projectId) {
        log.info("BidAgent getCommercialRequirements: projectId={}", projectId);
        var result = commercialClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("商务条款分类完成", result));
    }

    @GetMapping("/risk-classification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RiskClassificationAppService.RiskClassificationResult>> getRiskClassification(
            @PathVariable Long projectId) {
        log.info("BidAgent getRiskClassification: projectId={}", projectId);
        var result = riskClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("风险分类完成", result));
    }

    @GetMapping("/scoring-criteria")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult>> getScoringCriteria(
            @PathVariable Long projectId) {
        log.info("BidAgent getScoringCriteria: projectId={}", projectId);
        var result = scoringCriteriaClassificationAppService.classifyForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("评分标准解析完成", result));
    }

    @GetMapping("/knowledge-base-match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<KnowledgeBaseMatchResult>> getKnowledgeBaseMatch(
            @PathVariable Long projectId) {
        log.info("BidAgent getKnowledgeBaseMatch: projectId={}", projectId);
        var result = knowledgeBaseMatchAppService.matchForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("四库联动匹配完成", result));
    }

    @GetMapping("/full-analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FullAnalysisAppService.FullAnalysisResult>> getFullAnalysis(
            @PathVariable Long projectId) {
        log.info("BidAgent getFullAnalysis: projectId={}", projectId);
        var result = fullAnalysisAppService.analyzeForProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("全维度分析完成", result));
    }
}
