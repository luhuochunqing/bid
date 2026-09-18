// Input: DimensionRow / TimeDimensionRow lists from query services
// Output: TrendComputationResult (categories + bid/win/winRate series)
// Pos: Computation Service/纯计算层（投标数=全部项目口径；竞品维度中标数=notes「已中标」口径，见 computeCompetitorTrend）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.service.DimensionRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import com.xiyu.bid.entity.Project;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 趋势分析纯计算服务（无副作用，输入行列表 → 图表序列）。
 *
 * <p>输入行已由上游 {@link TrendAnalysisQueryService} / {@link TrendAnalysisDimensionQueryService}
 * 经 {@code ProjectAccessScopeService} 完成项目级数据权限过滤，本服务不接触存储、
 * 不做权限判定，仅做分组/去重/补零等纯计算。</p>
 */
@Component
class TrendAnalysisComputationService {

    /**
     * 按维度标签分组，计算投标数、中标数、中标率。
     * 投标数 = 全部项目（含进行中状态；M0 卡片「投标数」为 4 状态口径，与 M1 不同，见 .wiki/pages/data-analysis-revamp.md；旧 WON+LOST 口径曾致区域维度全 0）。
     * 中标数 = 已中标(WON) 的项目数。
     * 中标率 = 中标数 / (已中标 + 未中标)（进行中/流标/弃标等不计入分母；项目状态与竞品维度除外）。
     * 按 bidCount 降序排列（PRD 6.5）。
     * @param expectedCategories 可选，期望在 X 轴出现的分类（数量为 0 也展示），仅 xAxis 为 customerType/projectType 时传入
     */
    TrendComputationResult computeDimensionTrend(List<DimensionRow> rows, List<String> expectedCategories) {
        Map<String, TrendSeriesAssembler.Bucket> bucketMap = new LinkedHashMap<>();
        // P1-2 去重：LEFT JOIN 结果/竞品表会使同一项目在同一分类下产生多行，
        // 按 (category, projectId) 去重，与下钻 COUNT(DISTINCT p.id) 口径对齐；
        // 竞品维度下一个项目可合法分属多个竞品分类，故去重键含 category。
        Set<String> countedKeys = new HashSet<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            if (row.projectId() != null && !countedKeys.add(key + "|" + row.projectId())) {
                continue;
            }
            TrendSeriesAssembler.Bucket bucket = bucketMap.computeIfAbsent(key, ignored -> new TrendSeriesAssembler.Bucket());
            // 投标数口径：全部项目计入
            bucket.bidCount++;
            if (row.status() == Project.Status.WON) {
                bucket.winCount++;
            } else if (row.status() == Project.Status.LOST) {
                bucket.notWonCount++;
            }
        }

        return TrendSeriesAssembler.assembleWithOutcomeRate(bucketMap, expectedCategories);
    }

    /**
     * 竞品维度专用计算（specs/055）。
     * 投标数 = 该竞品参与的项目数（(category, projectId) 去重后，与下钻 COUNT(DISTINCT p.id) 口径对齐）。
     * 中标数 = 「是否中标」列标记为「已中标」的次数 —— 数据源 project_result_competitor.notes 经
     * {@link CompetitorWonStatusSupport#parseCompetitorWon} 解析；<b>禁止</b>用项目级 status 推导
     * 竞品中标事实（语义层级错配，wiki data-analysis-revamp §10、lessons-learned §127，M1 复发修复）。
     * 中标率 = 中标数 / 投标数，业务语义为「该竞品对我司的中标率」；补零与排序同 computeDimensionTrend。
     */
    TrendComputationResult computeCompetitorTrend(List<DimensionRow> rows, List<String> expectedCategories) {
        Map<String, TrendSeriesAssembler.Bucket> bucketMap = new LinkedHashMap<>();
        // 去重：同 computeDimensionTrend，按 (category, projectId) 去重对齐下钻口径；
        // 去重后再判 notes，重复脏行取首行语义
        Set<String> countedKeys = new HashSet<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            if (row.projectId() != null && !countedKeys.add(key + "|" + row.projectId())) {
                continue;
            }
            TrendSeriesAssembler.Bucket bucket = bucketMap.computeIfAbsent(key, ignored -> new TrendSeriesAssembler.Bucket());
            // 投标数口径：该竞品参与的全部项目计入（维持 specs/055 FR-02 不变承诺）
            bucket.bidCount++;
            // 中标数口径：仅竞品行「是否中标」=已中标 计入（竞品级事实）
            if (CompetitorWonStatusSupport.parseCompetitorWon(row.competitorWonStatus())) {
                bucket.winCount++;
            }
        }

        // 竞品维度维持 specs/055 FR-02：中标率 = 中标数/该竞品参与项目数（口径变更排除项）
        return TrendSeriesAssembler.assembleWithBidRate(bucketMap, expectedCategories);
    }

    /**
     * 项目状态维度专用计算：统计每个状态的所有项目数量（不限于 WON+LOST）。
     * 因为 X 轴本身就是项目状态，每个状态的所有项目都应计入数量。
     * 该维度不展示中标率系列，winRateSeries 保持 win/bid 旧口径（口径变更排除项）。
     * 按 count 降序排列（PRD 6.5）。
     * @param expectedCategories 可选，期望在 X 轴上出现的分类列表（数量为 0 的也会展示），
     *                           仅当 xAxis 为 projectStatus 时传入
     */
    TrendComputationResult computeProjectStatusTrend(List<DimensionRow> rows, List<String> expectedCategories) {
        Map<String, TrendSeriesAssembler.Bucket> bucketMap = new LinkedHashMap<>();
        // P1-2 去重：同 computeDimensionTrend，按 (category, projectId) 去重对齐下钻口径。
        Set<String> countedKeys = new HashSet<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            if (row.projectId() != null && !countedKeys.add(key + "|" + row.projectId())) {
                continue;
            }
            TrendSeriesAssembler.Bucket bucket = bucketMap.computeIfAbsent(key, ignored -> new TrendSeriesAssembler.Bucket());
            // 项目状态维度：统计所有项目（每个状态的项目都计入 bidCount）
            bucket.bidCount++;
            if (row.status() == Project.Status.WON) {
                bucket.winCount++;
            }
        }

        return TrendSeriesAssembler.assembleWithBidRate(bucketMap, expectedCategories);
    }

    /**
     * 按指定时间粒度（day/week/month/year）分组计算投标数/中标数/中标率。
     * 投标数=全部项目（含进行中），中标数=status=WON；
     * 中标率 = 中标数 / (已中标 + 未中标)，与维度趋势新口径一致（项目状态/竞品维度除外）。
     * startDate/endDate 齐全时补全区间内全部时间槽（缺失补 0），确保 X 轴连续。
     */
    TrendComputationResult computeTimeTrend(List<TimeDimensionRow> rows,
                                            LocalDate startDate, LocalDate endDate,
                                            String timeDimension) {
        String td = timeDimension != null ? timeDimension : "month";

        Map<String, TrendSeriesAssembler.Bucket> bucketMap = new LinkedHashMap<>();
        // P1-2 去重：防御式按 projectId 去重，与下钻 COUNT(DISTINCT p.id) 口径对齐。
        Set<Long> countedProjectIds = new HashSet<>();
        for (TimeDimensionRow row : rows) {
            if (row.projectId() != null && !countedProjectIds.add(row.projectId())) {
                continue;
            }
            String key = buildPeriodKey(row, td);
            TrendSeriesAssembler.Bucket bucket = bucketMap.computeIfAbsent(key, ignored -> new TrendSeriesAssembler.Bucket());
            // 投标数口径：全部项目计入
            bucket.bidCount++;
            if (row.status() == Project.Status.WON) {
                bucket.winCount++;
            } else if (row.status() == Project.Status.LOST) {
                bucket.notWonCount++;
            }
        }

        // 生成完整时间序列：startDate ~ endDate 之间所有时间槽（正序），
        // 缺失补 0，确保 X 轴从区间起始连续显示。
        List<String> sortedKeys = buildContinuousKeys(startDate, endDate, bucketMap.keySet(), td);

        List<String> categories = new ArrayList<>();
        List<Long> bidSeries = new ArrayList<>();
        List<Long> winSeries = new ArrayList<>();
        List<Double> winRateSeries = new ArrayList<>();

        for (String key : sortedKeys) {
            categories.add(key);
            TrendSeriesAssembler.Bucket bucket = bucketMap.get(key);
            long bid = bucket == null ? 0L : bucket.bidCount;
            long win = bucket == null ? 0L : bucket.winCount;
            long notWon = bucket == null ? 0L : bucket.notWonCount;
            bidSeries.add(bid);
            winSeries.add(win);
            winRateSeries.add(TrendSeriesAssembler.ratio(win, win + notWon));
        }

        return new TrendComputationResult(categories, bidSeries, winSeries, winRateSeries);
    }

    /**
     * 根据时间粒度生成 period key：month→"YYYY-MM"、week→"YYYY-WXX"、day→"YYYY-MM-DD"、year→"YYYY"。
     */
    private String buildPeriodKey(TimeDimensionRow row, String timeDimension) {
        return switch (timeDimension != null ? timeDimension : "month") {
            case "year" -> row.year() != null ? String.valueOf(row.year()) : "未知";
            case "week" -> row.year() != null && row.week() != null
                    ? String.format("%d-W%02d", row.year(), row.week()) : "未知";
            case "day" -> row.year() != null && row.month() != null && row.day() != null
                    ? String.format("%d-%02d-%02d", row.year(), row.month(), row.day()) : "未知";
            default -> {
                if (row.year() == null) yield "未知";
                if (row.month() == null) yield String.valueOf(row.year());
                yield String.format("%d-%02d", row.year(), row.month());
            }
        };
    }

    /**
     * 生成 startDate~endDate 全部时间槽 key（粒度由 timeDimension 决定：month 逐月 / week 逐周
     * ISO 基准 / day 逐日 / year 逐年），正序排列；区间外但有数据的 key 追加末尾；
     * startDate/endDate 任一为空时退化为仅按已有数据 key 排序。
     */
    private List<String> buildContinuousKeys(LocalDate startDate, LocalDate endDate,
                                              java.util.Set<String> dataKeys, String timeDimension) {
        if (startDate == null || endDate == null) {
            List<String> fallback = new ArrayList<>(dataKeys);
            fallback.sort(String::compareTo);
            return fallback;
        }

        String td = timeDimension != null ? timeDimension : "month";
        List<String> keys = new ArrayList<>();

        switch (td) {
            case "year" -> {
                int curYear = startDate.getYear();
                int endYear = endDate.getYear();
                while (curYear <= endYear) {
                    keys.add(String.valueOf(curYear));
                    curYear++;
                }
            }
            case "week" -> {
                LocalDate cur = startDate;
                while (!cur.isAfter(endDate)) {
                    int year = cur.get(IsoFields.WEEK_BASED_YEAR);
                    int week = cur.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    keys.add(String.format("%d-W%02d", year, week));
                    cur = cur.plusWeeks(1);
                }
            }
            case "day" -> {
                LocalDate cur = startDate;
                while (!cur.isAfter(endDate)) {
                    keys.add(cur.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    cur = cur.plusDays(1);
                }
            }
            default -> {
                // month
                YearMonth cur = YearMonth.from(startDate);
                YearMonth end = YearMonth.from(endDate);
                while (!cur.isAfter(end)) {
                    keys.add(String.format("%d-%02d", cur.getYear(), cur.getMonthValue()));
                    cur = cur.plusMonths(1);
                }
            }
        }

        // 追加区间外但实际有数据的 key（防御性，正常不会触发）
        for (String k : dataKeys) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        keys.sort(String::compareTo);
        return keys;
    }
}

