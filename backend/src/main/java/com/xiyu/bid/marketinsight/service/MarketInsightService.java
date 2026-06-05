// Input: TenderRepository, core policies (TrendAnalysis, PurchaserExtraction, IndustryClassification)
// Output: MarketInsightDTO aggregation (industry trends, purchaser patterns, forecast tips)
// Pos: Service/编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.marketinsight.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketinsight.core.IndustryClassificationPolicy;
import com.xiyu.bid.marketinsight.core.PurchaserExtractionPolicy;
import com.xiyu.bid.marketinsight.core.TrendAnalysisPolicy;
import com.xiyu.bid.marketinsight.dto.ForecastTipDTO;
import com.xiyu.bid.marketinsight.dto.IndustryTrendDTO;
import com.xiyu.bid.marketinsight.dto.MarketInsightAssembler;
import com.xiyu.bid.marketinsight.dto.MarketInsightDTO;
import com.xiyu.bid.marketinsight.dto.PurchaserPatternDTO;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 市场洞察服务 — 纯编排层
 * 加载 → 转换为核心类型 → 调用核心策略 → 映射为 DTO → 返回
 */
@Service
@RequiredArgsConstructor
public class MarketInsightService {

    private final TenderRepository tenderRepository;

    /** Enriched tender carrying purchaser and industry metadata. */
    record EnrichedTender(Tender tender, String purchaserName,
                          String purchaserHash, String industry) {
    }

    /**
     * 获取市场洞察聚合数据。
     * 流程：加载标讯 → 提取采购人/行业 → 按行业计算趋势 → 按采购人计算模式 → 生成预测提示
     *
     * @return 市场洞察 DTO
     */
    @Transactional(readOnly = true)
    public MarketInsightDTO getMarketInsight() {
        List<Tender> tenders = tenderRepository.findAll();
        if (tenders.isEmpty()) {
            return MarketInsightDTO.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentPeriodStart = now.minusMonths(3);
        LocalDateTime previousPeriodStart = now.minusMonths(6);

        List<EnrichedTender> enriched = enrichTenders(tenders);

        List<IndustryTrendDTO> industryTrends =
                computeIndustryTrends(enriched, currentPeriodStart, previousPeriodStart, now);
        List<PurchaserPatternDTO> purchaserPatterns =
                computePurchaserPatterns(enriched);
        List<ForecastTipDTO> forecastTips =
                computeForecastTips(industryTrends);

        return MarketInsightDTO.builder()
                .industryTrends(industryTrends)
                .purchaserPatterns(purchaserPatterns)
                .forecastTips(forecastTips)
                .build();
    }

    // ── private helpers ──────────────────────────────────────────

    private List<EnrichedTender> enrichTenders(List<Tender> tenders) {
        List<EnrichedTender> result = new ArrayList<>(tenders.size());
        for (Tender tender : tenders) {
            var extraction = PurchaserExtractionPolicy.extractPurchaser(tender.getTitle());
            String purchaserName = extraction.found() ? extraction.purchaserName() : "";
            String purchaserHash = extraction.found() ? extraction.purchaserHash() : "";
            String industry = IndustryClassificationPolicy.classifyIndustry(tender.getTitle());
            result.add(new EnrichedTender(tender, purchaserName, purchaserHash, industry));
        }
        return result;
    }

    private List<IndustryTrendDTO> computeIndustryTrends(
            List<EnrichedTender> enriched,
            LocalDateTime currentPeriodStart,
            LocalDateTime previousPeriodStart,
            LocalDateTime now) {

        // Group by industry
        Map<String, List<EnrichedTender>> byIndustry = new LinkedHashMap<>();
        for (EnrichedTender et : enriched) {
            byIndustry.computeIfAbsent(et.industry(), k -> new ArrayList<>()).add(et);
        }

        List<IndustryTrendDTO> trends = new ArrayList<>(byIndustry.size());
        for (var entry : byIndustry.entrySet()) {
            String industry = entry.getKey();
            List<EnrichedTender> group = entry.getValue();

            int currentCount = 0;
            int previousCount = 0;
            long totalAmount = 0L;

            for (EnrichedTender et : group) {
                LocalDateTime createdAt = et.tender().getCreatedAt();
                BigDecimal budget = et.tender().getBudget();
                long budgetYuan = budget != null ? budget.longValue() : 0L;

                if (createdAt != null) {
                    if (!createdAt.isBefore(currentPeriodStart) && !createdAt.isAfter(now)) {
                        currentCount++;
                    } else if (!createdAt.isBefore(previousPeriodStart)
                            && createdAt.isBefore(currentPeriodStart)) {
                        previousCount++;
                    }
                }
                totalAmount += budgetYuan;
            }

            var trendResult = TrendAnalysisPolicy.computeTrend(
                    industry, currentCount, previousCount, totalAmount);
            String color = IndustryClassificationPolicy.getColorForIndustry(industry);
            trends.add(MarketInsightAssembler.toDTO(trendResult, color));
        }
        return trends;
    }

    private List<PurchaserPatternDTO> computePurchaserPatterns(
            List<EnrichedTender> enriched) {

        // Group by purchaserHash, skip entries without a purchaser
        Map<String, List<EnrichedTender>> byPurchaser = new LinkedHashMap<>();
        for (EnrichedTender et : enriched) {
            if (et.purchaserHash() != null && !et.purchaserHash().isBlank()) {
                byPurchaser.computeIfAbsent(et.purchaserHash(), k -> new ArrayList<>())
                        .add(et);
            }
        }

        List<PurchaserPatternDTO> patterns = new ArrayList<>(byPurchaser.size());
        for (var entry : byPurchaser.entrySet()) {
            List<EnrichedTender> group = entry.getValue();

            List<TrendAnalysisPolicy.PurchaserTenderRecord> records = new ArrayList<>(group.size());
            for (EnrichedTender et : group) {
                BigDecimal budget = et.tender().getBudget();
                long budgetInWan = budget != null ? budget.longValue() / 10_000L : 0L;
                LocalDateTime createdAt = et.tender().getCreatedAt();
                int year = createdAt != null ? createdAt.getYear() : 0;
                int month = createdAt != null ? createdAt.getMonthValue() : 1;
                records.add(new TrendAnalysisPolicy.PurchaserTenderRecord(
                        et.tender().getTitle(), budgetInWan, year, month));
            }

            var patternResult = TrendAnalysisPolicy.computePurchaserPattern(records);
            patterns.add(MarketInsightAssembler.toDTO(patternResult));
        }
        return patterns;
    }

    private List<ForecastTipDTO> computeForecastTips(
            List<IndustryTrendDTO> industryTrends) {

        // Reconstruct TrendResult list from DTOs for core policy call
        List<TrendAnalysisPolicy.TrendResult> trendResults = new ArrayList<>(industryTrends.size());
        for (IndustryTrendDTO dto : industryTrends) {
            trendResults.add(new TrendAnalysisPolicy.TrendResult(
                    dto.getIndustry(), dto.getCount(), dto.getAmount(),
                    dto.getGrowth(), dto.getTrend(), dto.getHotLevel()));
        }

        List<TrendAnalysisPolicy.ForecastTip> tips =
                TrendAnalysisPolicy.generateForecastTips(trendResults);

        List<ForecastTipDTO> tipDtos = new ArrayList<>(tips.size());
        for (TrendAnalysisPolicy.ForecastTip tip : tips) {
            tipDtos.add(MarketInsightAssembler.toDTO(tip));
        }
        return tipDtos;
    }
}
