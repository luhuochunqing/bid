package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.AnalyticsFilterOptionDTO;
import com.xiyu.bid.analytics.dto.EnhancedOverviewResponse;
import com.xiyu.bid.analytics.dto.TrendAnalysisResponse;
import com.xiyu.bid.analytics.service.TrendAnalysisService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TrendAnalysisController {

    private final TrendAnalysisService trendAnalysisService;

    /**
     * M0: 增强关键指标，支持按日期筛选。
     */
    @GetMapping("/overview/enhanced")
    @PreAuthorize("hasAuthority('dashboard')")
    public ResponseEntity<ApiResponse<EnhancedOverviewResponse>> getEnhancedOverview(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                trendAnalysisService.getEnhancedOverview(startDate, endDate)
        ));
    }

    /**
     * M1: 增强趋势分析，支持维度筛选参数。
     */
    @GetMapping("/trends/enhanced")
    @PreAuthorize("hasAuthority('dashboard')")
    public ResponseEntity<ApiResponse<TrendAnalysisResponse>> getEnhancedTrends(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "time") String xAxis,
            @RequestParam(required = false) List<Long> departmentIds,
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) List<Long> regionIds,
            @RequestParam(required = false) List<String> customerTypes,
            @RequestParam(required = false) List<String> projectTypes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> tenderEntities,
            @RequestParam(required = false) List<String> competitorNames
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                trendAnalysisService.getEnhancedTrends(
                        startDate, endDate, xAxis,
                        departmentIds, userIds, regionIds,
                        customerTypes, projectTypes, statuses,
                        tenderEntities, competitorNames
                )
        ));
    }

    /**
     * M1: PRD §6.2 筛选区下拉选项一次性加载。
     * 返回 7 个维度的 DISTINCT 选项（部门/人员/区域/客户类型/项目类型/招标主体/竞品公司）。
     * 项目状态由前端常量定义，不在此返回。
     */
    @GetMapping("/filter-options")
    @PreAuthorize("hasAuthority('dashboard')")
    public ResponseEntity<ApiResponse<Map<String, List<AnalyticsFilterOptionDTO>>>> getFilterOptions() {
        return ResponseEntity.ok(ApiResponse.success(
                trendAnalysisService.getFilterOptions()
        ));
    }

    /**
     * M1: PRD §6.4 部门-人员联动 — 根据已选部门名称列表刷新人员下拉选项。
     * departmentNames 不传时返回全部人员。
     */
    @GetMapping("/filter-options/persons")
    @PreAuthorize("hasAuthority('dashboard')")
    public ResponseEntity<ApiResponse<List<AnalyticsFilterOptionDTO>>> getPersonsByDepartments(
            @RequestParam(required = false) List<String> departmentNames
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                trendAnalysisService.getPersonOptionsByDepartments(departmentNames)
        ));
    }
}