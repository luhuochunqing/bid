// Input: authenticated project id for an independent estimate refresh
// Output: refreshed score items after project access and freshness guards
// Pos: scoreparse/controller HTTP adapter; business decisions stay in application/domain
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 README.md。
package com.xiyu.bid.scoreparse.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.ScoreEstimateRefreshService;
import com.xiyu.bid.scoreparse.application.ScoreParseAppService;
import com.xiyu.bid.scoreparse.dto.ScoreEstimateRefreshDTO;
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
    public ApiResponse<ScoreEstimateRefreshDTO> refresh(@PathVariable Long projectId) {
        var receipt = estimates.refresh(projectId);
        var read = parser.getItems(projectId);
        if (read == null || !receipt.matchesSavedResults(read.items())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "预判结果在刷新后发生变化，请重新更新知识库预判");
        }
        return ApiResponse.success(new ScoreEstimateRefreshDTO(read.items(), read.summary(), read.meta(), receipt));
    }
}
