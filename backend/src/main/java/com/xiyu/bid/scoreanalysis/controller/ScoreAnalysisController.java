// Input: scoreanalysis service and request DTOs
// Output: Score Analysis REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreanalysis.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisCreateRequest;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.service.ScoreAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评分分析控制器
 * 提供评分分析的HTTP端点
 */
@RestController
@RequestMapping("/api/ai/score-analysis")
@RequiredArgsConstructor
@Slf4j
public class ScoreAnalysisController {

    private final ScoreAnalysisService scoreAnalysisService;

    /**
     * 获取项目的评分分析
     * @param projectId 项目ID
     * @return 评分分析
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ApiResponse<ScoreAnalysisDTO> getAnalysisByProject(@PathVariable Long projectId) {
        log.info("获取项目{}的评分分析", projectId);
        return scoreAnalysisService.getAnalysisByProject(projectId);
    }

    /**
     * 获取项目的历史分析记录
     * @param projectId 项目ID
     * @return 历史分析列表
     */
    @GetMapping("/project/{projectId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ApiResponse<List<ScoreAnalysisDTO>> getAnalysisHistory(@PathVariable Long projectId) {
        log.info("获取项目{}的历史分析", projectId);
        return scoreAnalysisService.getAnalysisHistory(projectId);
    }

    /**
     * 创建新的评分分析
     * @param request 创建请求
     * @return 创建的评分分析
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ScoreAnalysisDTO> createAnalysis(
            @Valid @RequestBody ScoreAnalysisCreateRequest request) {
        log.info("创建项目{}的评分分析", request.getProjectId());
        return scoreAnalysisService.createAnalysis(request);
    }

    /**
     * 比较两个项目的评分
     * @param id1 项目1 ID
     * @param id2 项目2 ID
     * @return 两个项目的评分对比
     */
    @GetMapping("/compare/{id1}/{id2}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ApiResponse<List<ScoreAnalysisDTO>> compareProjects(
            @PathVariable Long id1, @PathVariable Long id2) {
        log.info("比较项目{}和项目{}的评分", id1, id2);
        return scoreAnalysisService.compareProjects(id1, id2);
    }
}
