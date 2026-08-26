// Input: competitionintel service and request DTOs
// Output: Competition Intel REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.competitionintel.controller;

import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.dto.CompetitorMergeRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorUpsertRequest;
import com.xiyu.bid.competitionintel.service.CompetitionIntelService;
import com.xiyu.bid.competitionintel.service.CompetitorGovernanceService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 竞争情报控制器
 * 处理竞争对手和竞争分析相关的HTTP请求
 */
@RestController
@RequestMapping("/api/ai/competition")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class CompetitionIntelController {

    private final CompetitionIntelService competitionIntelService;
    private final CompetitorGovernanceService competitorGovernanceService;

    /**
     * 获取竞品列表（默认仅启用中；includeDisabled=true 返回全部供治理页）
     */
    @GetMapping("/competitors")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompetitorDTO>>> getAllCompetitors(
            @RequestParam(name = "includeDisabled", required = false, defaultValue = "false") boolean includeDisabled) {
        log.info("GET /api/ai/competition/competitors - Fetching competitors (includeDisabled={})", includeDisabled);
        List<CompetitorDTO> competitors = includeDisabled
                ? competitorGovernanceService.getAllCompetitors()
                : competitorGovernanceService.getActiveCompetitors();
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved competitors", competitors));
    }

    /**
     * 幂等 upsert 竞品（录入端输入即建，specs/055）
     */
    @PostMapping("/competitors")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.COMPETITOR_CREATE_PERMISSION + "')")
    public ResponseEntity<ApiResponse<CompetitorDTO>> upsertCompetitor(
            @Valid @RequestBody CompetitorUpsertRequest request) {
        log.info("POST /api/ai/competition/competitors - Upserting competitor: {}", request.getName());
        CompetitorDTO competitor = competitorGovernanceService.upsertCompetitor(request);
        return ResponseEntity.ok(ApiResponse.success("Competitor upserted successfully", competitor));
    }

    /**
     * 改名竞品（治理端，specs/055）
     */
    @PutMapping("/competitors/{id}")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.COMPETITOR_GOVERN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<CompetitorDTO>> renameCompetitor(
            @PathVariable Long id, @Valid @RequestBody CompetitorUpsertRequest request) {
        log.info("PUT /api/ai/competition/competitors/{} - Renaming competitor to {}", id, request.getName());
        CompetitorDTO competitor = competitorGovernanceService.renameCompetitor(id, request.getName());
        return ResponseEntity.ok(ApiResponse.success("Competitor renamed successfully", competitor));
    }

    /**
     * 合并竞品（治理端，specs/055）
     */
    @PostMapping("/competitors/{id}/merge")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.COMPETITOR_GOVERN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<CompetitorDTO>> mergeCompetitor(
            @PathVariable Long id, @Valid @RequestBody CompetitorMergeRequest request) {
        log.info("POST /api/ai/competition/competitors/{}/merge - Merging into {}", id, request.getTargetId());
        CompetitorDTO competitor = competitorGovernanceService.mergeCompetitor(id, request.getTargetId());
        return ResponseEntity.ok(ApiResponse.success("Competitor merged successfully", competitor));
    }

    /**
     * 停用竞品（治理端，specs/055）
     */
    @PostMapping("/competitors/{id}/disable")
    @PreAuthorize("hasAuthority('" + RoleProfileCatalog.COMPETITOR_GOVERN_PERMISSION + "')")
    public ResponseEntity<ApiResponse<CompetitorDTO>> disableCompetitor(@PathVariable Long id) {
        log.info("POST /api/ai/competition/competitors/{}/disable - Disabling competitor", id);
        CompetitorDTO competitor = competitorGovernanceService.disableCompetitor(id);
        return ResponseEntity.ok(ApiResponse.success("Competitor disabled successfully", competitor));
    }

    /**
     * 获取项目的竞争分析
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompetitionAnalysisDTO>>> getAnalysisByProject(
            @PathVariable Long projectId) {
        log.info("GET /api/ai/competition/project/{} - Fetching competition analysis", projectId);
        List<CompetitionAnalysisDTO> analyses = competitionIntelService.getAnalysisByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved competition analysis", analyses));
    }

    /**
     * 分析项目竞争情况
     */
    @PostMapping("/project/{projectId}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CompetitionAnalysisDTO>> analyzeCompetition(
            @PathVariable Long projectId) {
        log.info("POST /api/ai/competition/project/{}/analyze - Analyzing competition", projectId);
        CompetitionAnalysisDTO analysis = competitionIntelService.analyzeCompetition(projectId);
        return ResponseEntity.ok(ApiResponse.success("Competition analysis completed successfully", analysis));
    }

    /**
     * 创建竞争分析
     */
    @PostMapping("/analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CompetitionAnalysisDTO>> createAnalysis(
            @Valid @RequestBody AnalysisCreateRequest request) {
        log.info("POST /api/ai/competition/analysis - Creating analysis for project: {}", request.getProjectId());
        CompetitionAnalysisDTO analysis = competitionIntelService.createAnalysis(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Analysis created successfully", analysis));
    }

    /**
     * 获取竞争对手历史表现
     */
    @GetMapping("/competitor/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompetitionAnalysisDTO>>> getHistoricalPerformance(
            @PathVariable Long id) {
        log.info("GET /api/ai/competition/competitor/{}/history - Fetching historical performance", id);
        List<CompetitionAnalysisDTO> history = competitionIntelService.getHistoricalPerformance(id);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved historical performance", history));
    }
}
