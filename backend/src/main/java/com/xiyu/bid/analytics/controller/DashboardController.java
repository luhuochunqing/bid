// Input: DashboardAnalyticsService
// Output: REST API Endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.DashboardOverviewDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponse;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponseDTO;
import com.xiyu.bid.analytics.service.DashboardAnalyticsService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for dashboard analytics
 * Provides endpoints for dashboard data aggregation
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardAnalyticsService dashboardAnalyticsService;

    /**
     * Get complete dashboard overview
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DashboardOverviewDTO>> getOverview() {
        DashboardOverviewDTO overview = dashboardAnalyticsService.getOverview();
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    /**
     * Get summary statistics only
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<SummaryStats>> getSummaryStats() {
        SummaryStats stats = dashboardAnalyticsService.getSummaryStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Get trend analysis
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, List<TrendData>>>> getTrends() {
        List<TrendData> tenderTrends =
                dashboardAnalyticsService.getTenderTrends();
        List<TrendData> projectTrends =
                dashboardAnalyticsService.getProjectTrends();

        Map<String, List<TrendData>> trends = Map.of(
                "tenders", tenderTrends,
                "projects", projectTrends
        );

        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    /**
     * Get competitor analysis
     */
    @GetMapping("/competitors")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CompetitorData>>> getTopCompetitors(
            @RequestParam(required = false, defaultValue = "5") Integer limit
    ) {
        List<CompetitorData> competitors =
                dashboardAnalyticsService.getTopCompetitors(limit);
        return ResponseEntity.ok(ApiResponse.success(competitors));
    }

    /**
     * Get regional analysis
     */
    @GetMapping("/regions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<RegionalData>>>
            getRegionalDistribution() {
        List<RegionalData> regions =
                dashboardAnalyticsService.getRegionalDistribution();
        return ResponseEntity.ok(ApiResponse.success(regions));
    }

    @GetMapping("/product-lines")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProductLineData>>> getProductLines() {
        List<ProductLineData> productLines =
                dashboardAnalyticsService.getProductLinePerformance();
        return ResponseEntity.ok(ApiResponse.success(productLines));
    }

    @GetMapping("/drill-down")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsDrillDownResponse>>
            getDrillDown(
            @RequestParam String type,
            @RequestParam String key
    ) {
        AnalyticsDrillDownResponse response =
                dashboardAnalyticsService.getDrillDown(type, key);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/drilldown/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsDrillDownResponseDTO>>
            getRevenueDrillDown(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        AnalyticsDrillDownResponseDTO response =
                dashboardAnalyticsService.getRevenueDrillDown(
                        status, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/drilldown/win-rate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsDrillDownResponseDTO>>
            getWinRateDrillDown(
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        AnalyticsDrillDownResponseDTO response =
                dashboardAnalyticsService.getWinRateDrillDown(
                        outcome, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/drilldown/team")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsDrillDownResponseDTO>>
            getTeamDrillDown(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        AnalyticsDrillDownResponseDTO response =
                dashboardAnalyticsService.getTeamDrillDown(
                        role, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/drilldown/projects")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsDrillDownResponseDTO>>
            getProjectDrillDown(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        AnalyticsDrillDownResponseDTO response =
                dashboardAnalyticsService.getProjectDrillDown(
                        status, startDate, endDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get status distribution
     */
    @GetMapping("/status-distribution")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Long>>>
            getStatusDistribution() {
        Map<String, Long> distribution =
                dashboardAnalyticsService.getStatusDistribution();
        return ResponseEntity.ok(ApiResponse.success(distribution));
    }

    /**
     * Clear dashboard cache
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> clearCache() {
        dashboardAnalyticsService.clearOverviewCache();
        return ResponseEntity.ok(
                ApiResponse.success("Cache cleared successfully", null));
    }
}
