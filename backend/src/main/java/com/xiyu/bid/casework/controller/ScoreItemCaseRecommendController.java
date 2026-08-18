package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.service.ScoreItemCaseRecommendAppService;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendRequest;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiyu.bid.scoreparse.entity.ScoreItem;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

/**
 * 评分项与案例推荐接口控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ScoreItemCaseRecommendController {

    private final ScoreItemCaseRecommendAppService recommendAppService;

    /**
     * 基于评分项（或关键词）检索高匹配度历史案例 (PRD 4.2 契约)
     */
    @PostMapping("/cases/recommend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoreItemCaseRecommendResponse> recommend(
            @PathVariable Long projectId,
            @RequestBody(required = false) ScoreItemCaseRecommendRequest request) {
        if (request == null) {
            request = new ScoreItemCaseRecommendRequest();
        }
        return ResponseEntity.ok(recommendAppService.recommend(projectId, request));
    }

    /**
     * 获取当前项目的评分项列表 (PRD 4.2 契约)
     */
    @GetMapping("/score-items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScoreItem>> getScoreItems(@PathVariable Long projectId) {
        return ResponseEntity.ok(recommendAppService.getScoreItems(projectId));
    }
}
