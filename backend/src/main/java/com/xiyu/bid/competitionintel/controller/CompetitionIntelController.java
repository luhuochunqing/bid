// Input: competitionintel service and request DTOs
// Output: Competition Intel REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.competitionintel.controller;

import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.competitionintel.dto.CompetitorDTO;
import com.xiyu.bid.competitionintel.service.CompetitionIntelService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
public class CompetitionIntelController {

    private final CompetitionIntelService competitionIntelService;

    /**
     * 获取所有竞争对手
     */
    @GetMapping("/competitors")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CompetitorDTO>>> getAllCompetitors() {
        log.info("GET /api/ai/competition/competitors - Fetching all competitors");
        List<CompetitorDTO> competitors = competitionIntelService.getAllCompetitors();
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved competitors", competitors));
    }

    /**
     * 创建竞争对手
     */
    @PostMapping("/competitors")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CompetitorDTO>> createCompetitor(
            @Valid @RequestBody CompetitorCreateRequest request) {
        log.info("POST /api/ai/competition/competitors - Creating competitor: {}", request.getName());
        CompetitorDTO competitor = competitionIntelService.createCompetitor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Competitor created successfully", competitor));
    }

    /**
     * 获取项目的竞争分析
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CompetitionAnalysisDTO>>> getHistoricalPerformance(
            @PathVariable Long id) {
        log.info("GET /api/ai/competition/competitor/{}/history - Fetching historical performance", id);
        List<CompetitionAnalysisDTO> history = competitionIntelService.getHistoricalPerformance(id);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved historical performance", history));
    }
}
