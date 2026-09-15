// Input: GET /api/workbench/opportunity-predictions（已认证用户）
// Output: 未来 1 年内的预测商机列表（ApiResponse<List<WorkbenchOpportunityDTO>>）
// Pos: workbench/controller/ - 工作台商机预测入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workbench.dto.WorkbenchOpportunityDTO;
import com.xiyu.bid.workbench.service.WorkbenchOpportunityQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作台 AI 商机预测 Controller。
 * 路径前缀 /api/workbench，与 WorkbenchDeadlineController / WorkbenchScheduleController 对齐。
 *
 * <p>端点：
 * - GET /api/workbench/opportunity-predictions：全状态项目按提醒规则
 *   预测下一轮招标时间，只返回未来 1 年内的商机（按预测时间升序）。
 *   提醒通知暂未接入，仅提供列表数据。
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class WorkbenchOpportunityController {

    private final WorkbenchOpportunityQueryService opportunityQueryService;

    @GetMapping("/opportunity-predictions")
    public ResponseEntity<ApiResponse<List<WorkbenchOpportunityDTO>>> getOpportunityPredictions() {
        log.info("GET /api/workbench/opportunity-predictions");
        return ResponseEntity.ok(ApiResponse.success(
                opportunityQueryService.getOpportunityPredictions(LocalDate.now())
        ));
    }
}
