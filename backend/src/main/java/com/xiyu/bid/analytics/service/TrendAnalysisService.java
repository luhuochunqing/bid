package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsFilterOptionDTO;
import com.xiyu.bid.analytics.dto.EnhancedOverviewResponse;
import com.xiyu.bid.analytics.dto.TrendAnalysisResponse;
import com.xiyu.bid.analytics.service.TrendAnalysisComputationService.TrendComputationResult;
import com.xiyu.bid.analytics.service.DimensionRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.OverviewRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import com.xiyu.bid.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final TrendAnalysisQueryService queryService;
    private final TrendAnalysisDimensionQueryService dimensionQueryService;
    private final TrendAnalysisComputationService computationService;
    private final FilterOptionsQueryService filterOptionsQueryService;

    public TrendAnalysisResponse getEnhancedTrends(
            LocalDate startDate,
            LocalDate endDate,
            String xAxis,
            String timeDimension,
            List<String> departmentIds,
            List<String> userIds,
            List<String> regionIds,
            List<String> customerTypes,
            List<String> projectTypes,
            List<String> statuses,
            List<String> tenderEntities,
            List<String> competitorNames
    ) {
        // 将字符串状态转换为枚举
        List<Project.Status> statusEnums = statuses != null
                ? statuses.stream()
                        .map(s -> {
                            try {
                                return Project.Status.valueOf(s);
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .toList()
                : null;

// 统一构建查询参数，所有维度共用
        TrendQueryCriteria criteria = buildCriteria(startDate, endDate,
                departmentIds, userIds, regionIds,
                customerTypes, projectTypes, statusEnums,
                tenderEntities, competitorNames);

        // 按 xAxis 路由到不同维度的查询 + 计算（PRD 6.3）
        String ax = xAxis != null ? xAxis : "time";
        String td = timeDimension != null ? timeDimension.toLowerCase() : "month";
        TrendComputationResult result = switch (ax) {
            case "time" -> {
                var rows = queryService.fetchTimeTrendRows(criteria, td);
                yield computationService.computeTimeTrend(rows, startDate, endDate, td);
            }
            case "dept" -> {
                var rows = dimensionQueryService.fetchDeptRows(criteria);
                yield computationService.computeDimensionTrend(rows, null);
            }
            case "person" -> {
                var rows = dimensionQueryService.fetchPersonRows(criteria);
                yield computationService.computeDimensionTrend(rows, null);
            }
            case "region" -> {
                var rows = dimensionQueryService.fetchRegionRows(criteria);
                yield computationService.computeDimensionTrend(rows, null);
            }
            case "customerType" -> {
                var rows = dimensionQueryService.fetchCustomerTypeRows(criteria);
                yield computationService.computeDimensionTrend(rows, customerTypes);
            }
            case "projectType" -> {
                var rows = dimensionQueryService.fetchProjectTypeRows(criteria);
                yield computationService.computeDimensionTrend(rows, projectTypes);
            }
            case "projectStatus" -> {
                var rows = dimensionQueryService.fetchStatusRows(criteria);
                yield computationService.computeProjectStatusTrend(rows, statuses);
            }
            case "tenderEntity" -> {
                var rows = dimensionQueryService.fetchTenderEntityRows(criteria);
                yield computationService.computeDimensionTrend(rows, null);
            }
            case "competitor" -> {
                var rows = dimensionQueryService.fetchCompetitorRows(criteria);
                yield computationService.computeDimensionTrend(rows, null);
            }
            default -> {
                var rows = queryService.fetchTimeTrendRows(criteria, td);
                yield computationService.computeTimeTrend(rows, startDate, endDate, td);
            }
        };

        return TrendAnalysisResponse.builder()
                .categories(result.categories())
                .bidSeries(result.bidSeries())
                .winSeries(result.winSeries())
                .winRateSeries(result.winRateSeries())
                .build();
    }

    public EnhancedOverviewResponse getEnhancedOverview(LocalDate startDate, LocalDate endDate) {
        // PRD §3.1 本期数据
        OverviewRow row = queryService.fetchOverviewRow(startDate, endDate);

        long totalCount = row.totalCount() != null ? row.totalCount() : 0L;
        long biddingCount = row.biddingCount() != null ? row.biddingCount() : 0L;
        long wonCount = row.wonCount() != null ? row.wonCount() : 0L;
        long notWonCount = row.notWonCount() != null ? row.notWonCount() : 0L;
        // 中标率 = 中标数 / (已中标 + 未中标)
        double winRate = (wonCount + notWonCount) == 0 ? 0.0
                : Math.round(wonCount * 1000.0 / (wonCount + notWonCount)) / 10.0;

        // PRD §3.1 去年同期数据（去年同期 = 上一年度同一日期范围）
        LocalDate yoyStart = startDate != null ? startDate.minusYears(1) : null;
        LocalDate yoyEnd = endDate != null ? endDate.minusYears(1) : null;
        OverviewRow yoyRow = queryService.fetchOverviewRow(yoyStart, yoyEnd);

        long yoyTotalCount = yoyRow.totalCount() != null ? yoyRow.totalCount() : 0L;
        long yoyBiddingCount = yoyRow.biddingCount() != null ? yoyRow.biddingCount() : 0L;
        long yoyWonCount = yoyRow.wonCount() != null ? yoyRow.wonCount() : 0L;
        long yoyNotWonCount = yoyRow.notWonCount() != null ? yoyRow.notWonCount() : 0L;
        // 中标率 = 中标数 / (已中标 + 未中标)
        double yoyWinRate = (yoyWonCount + yoyNotWonCount) == 0 ? 0.0
                : Math.round(yoyWonCount * 1000.0 / (yoyWonCount + yoyNotWonCount)) / 10.0;

        // PRD §3.1 同比计算（去年同期无数据时返回 null，前端显示「—」）
        Double totalCountYoy = yoyTotalCount > 0
                ? round1((totalCount - yoyTotalCount) * 100.0 / yoyTotalCount)
                : null;
        Double biddingCountYoy = yoyBiddingCount > 0
                ? round1((biddingCount - yoyBiddingCount) * 100.0 / yoyBiddingCount)
                : null;
        Double wonCountYoy = yoyWonCount > 0
                ? round1((wonCount - yoyWonCount) * 100.0 / yoyWonCount)
                : null;
        // 中标率同比 = 百分点差值（非百分比变化）
        Double winRateYoy = yoyTotalCount > 0
                ? round1(winRate - yoyWinRate)
                : null;

        // PRD §3.1 今日新增（投标总数卡片底部显示）
        long todayNewCount = queryService.fetchTodayNewCount();

        return EnhancedOverviewResponse.builder()
                .totalCount(totalCount)
                .biddingCount(biddingCount)
                .wonCount(wonCount)
                .notWonCount(notWonCount)
                .winRate(winRate)
                .totalCountYoy(totalCountYoy)
                .biddingCountYoy(biddingCountYoy)
                .wonCountYoy(wonCountYoy)
                .winRateYoy(winRateYoy)
                .todayNewCount(todayNewCount)
                .build();
    }

    /** 保留 1 位小数 */
    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    /** 构建 TrendQueryCriteria，统一查询参数传递 */
    private static TrendQueryCriteria buildCriteria(
            LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes,
            List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return new TrendQueryCriteria(startDate, endDate,
                departmentIds, userIds, regionIds,
                customerTypes, projectTypes, statuses,
                tenderEntities, competitorNames);
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项一次性加载。
     * 返回 7 个维度的 DISTINCT 选项（项目状态由前端常量定义，不在此返回）。
     * key = 维度 key（department/person/region/customerType/projectType/tenderEntity/competitor），
     * value = 选项列表（label 与 value 同值，便于前端选中后直接作为 API 参数传递）。
     */
    public Map<String, List<AnalyticsFilterOptionDTO>> getFilterOptions() {
        Map<String, List<AnalyticsFilterOptionDTO>> result = new LinkedHashMap<>();
        result.put("department", toStringOptions(filterOptionsQueryService.fetchDistinctDepartments()));
        result.put("person", toStringOptions(filterOptionsQueryService.fetchDistinctPersons(null)));
        result.put("region", toStringOptions(filterOptionsQueryService.fetchDistinctRegions()));
        result.put("customerType", toStringOptions(filterOptionsQueryService.fetchDistinctCustomerTypes()));
        result.put("projectType", toStringOptions(filterOptionsQueryService.fetchDistinctProjectTypes()));
        result.put("tenderEntity", toStringOptions(filterOptionsQueryService.fetchDistinctTenderEntitiesForFilter()));
        result.put("competitor", toStringOptions(filterOptionsQueryService.fetchDistinctCompetitorNames()));
        return result;
    }

    /**
     * PRD §6.4 部门-人员联动：根据已选部门名称列表刷新人员下拉选项。
     */
    public List<AnalyticsFilterOptionDTO> getPersonOptionsByDepartments(List<String> departmentNames) {
        return toStringOptions(filterOptionsQueryService.fetchDistinctPersons(departmentNames));
    }

    private static List<AnalyticsFilterOptionDTO> toStringOptions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(v -> AnalyticsFilterOptionDTO.builder().label(v).value(v).build())
                .toList();
    }
}