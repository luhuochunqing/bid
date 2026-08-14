package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.AnalyticsFilterOptionDTO;
import com.xiyu.bid.analytics.dto.EnhancedOverviewResponse;
import com.xiyu.bid.analytics.dto.TrendAnalysisResponse;
import com.xiyu.bid.analytics.dto.TrendDrillDownResponse;
import com.xiyu.bid.analytics.service.TrendAnalysisService;
import com.xiyu.bid.analytics.service.TrendDrillDownService;
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
    private final TrendDrillDownService trendDrillDownService;

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

    /**
     * M1: PRD §6.6 趋势分析下钻 — 点击图表柱子弹窗展示符合条件的项目列表。
     *
     * <p>查询参数：</p>
     * <ul>
     *   <li>dimension：X 轴维度（time/dept/person/region/customerType/projectType/projectStatus/tenderEntity/competitor）</li>
     *   <li>axisValue：当前 X 轴维度值（如 "2026-03" 或 "华东区"）</li>
     *   <li>seriesName：系列名（"投标数" / "中标数"）</li>
     *   <li>startDate/endDate：日期范围</li>
     *   <li>其他筛选条件：departments/persons/regions/customerTypes/projectTypes/statuses/tenderEntities/competitorNames</li>
     *   <li>page/size：分页参数（默认 1/10）</li>
     * </ul>
     */
    @GetMapping("/trends/drilldown")
    @PreAuthorize("hasAuthority('dashboard')")
    public ResponseEntity<ApiResponse<TrendDrillDownResponse>> getTrendDrillDown(
            @RequestParam String dimension,
            @RequestParam String axisValue,
            @RequestParam String seriesName,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) List<String> departments,
            @RequestParam(required = false) List<String> persons,
            @RequestParam(required = false) List<String> regions,
            @RequestParam(required = false) List<String> customerTypes,
            @RequestParam(required = false) List<String> projectTypes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> tenderEntities,
            @RequestParam(required = false) List<String> competitorNames,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                trendDrillDownService.drillDown(
                        dimension, axisValue, seriesName,
                        startDate, endDate, departments, persons, regions,
                        customerTypes, projectTypes, statuses, tenderEntities,
                        competitorNames, page, size
                )
        ));
    }
}