// Input: authenticated project id for an independent estimate refresh
// Output: refreshed score items after project access and freshness guards
// Pos: scoreparse/controller HTTP adapter; business decisions stay in application/domain
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 README.md。
package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.ScoreEstimateRefreshService;
import com.xiyu.bid.scoreparse.application.ScoreParseAppService;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/score-parse/estimates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ScoreEstimateController {
    private final ScoreEstimateRefreshService estimates;
    private final ScoreParseAppService parser;

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ScoreParseItemsDTO> refresh(@PathVariable Long projectId) {
        estimates.refresh(projectId);
        return ApiResponse.success(parser.getItems(projectId));
    }
}
