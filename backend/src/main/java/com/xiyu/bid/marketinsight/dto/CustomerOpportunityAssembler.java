package com.xiyu.bid.marketinsight.dto;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketinsight.entity.CustomerPrediction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户商机 DTO 组装器。
 * 无状态，无依赖，无副作用。
 */
public final class CustomerOpportunityAssembler {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final long WAN_DIVISOR = 10_000L;

    /** 关键采购阈值（万元） */
    private static final long KEY_PURCHASE_THRESHOLD_WAN = 100L;

    private CustomerOpportunityAssembler() {
    }

    /**
     * 将实体映射到客户洞察 DTO。
     *
     * @param entity 客户预测实体
     * @return 客户洞察 DTO
     */
    public static CustomerInsightDTO toInsightDTO(final CustomerPrediction entity) {
        return CustomerInsightDTO.builder()
                .customerId(entity.getPurchaserHash())
                .customerName(entity.getPurchaserName())
                .region(entity.getRegion())
                .industry(entity.getIndustry())
                .salesRep(entity.getSalesRep())
                .opportunityScore(entity.getOpportunityScore() != null
                        ? entity.getOpportunityScore() : 0)
                .predictedNextWindow(entity.getPredictedWindow())
                .status(entity.getStatus() != null
                        ? entity.getStatus().name().toLowerCase() : "watch")
                .mainCategories(parseCommaSeparated(entity.getMainCategories()))
                .avgBudget(toWan(entity.getAvgBudget()))
                .cycleType(entity.getCycleType())
                .build();
    }

    /**
     * 将标讯实体映射到客户采购记录 DTO。
     *
     * @param tender        标讯实体
     * @param purchaserHash 采购人哈希值
     * @param industry      行业分类
     * @return 客户采购记录 DTO
     */
    public static CustomerPurchaseDTO toPurchaseDTO(final Tender tender,
                                                    final String purchaserHash,
                                                    final String industry) {
        return toPurchaseDTO(
                tender.getId(),
                purchaserHash,
                tender.getCreatedAt(),
                tender.getTitle(),
                industry,
                tender.getBudget());
    }

    public static CustomerPurchaseDTO toPurchaseDTO(final Long recordId,
                                                    final String purchaserHash,
                                                    final LocalDateTime publishDate,
                                                    final String title,
                                                    final String industry,
                                                    final BigDecimal budget) {
        long budgetWan = toWan(budget);
        return CustomerPurchaseDTO.builder()
                .recordId(recordId)
                .customerId(purchaserHash)
                .publishDate(publishDate != null
                        ? publishDate.format(DATE_FORMATTER) : null)
                .title(title)
                .category(industry)
                .budget(budgetWan)
                .isKey(budgetWan > KEY_PURCHASE_THRESHOLD_WAN)
                .extractedTags(Collections.emptyList())
                .build();
    }

    /**
     * 将实体映射到客户商机预测 DTO。
     *
     * @param entity 客户预测实体
     * @return 客户商机预测 DTO
     */
    public static CustomerPredictionDTO toDTO(final CustomerPrediction entity) {
        return CustomerPredictionDTO.builder()
                .opportunityId(entity.getId())
                .customerId(entity.getPurchaserHash())
                .suggestedProjectName(buildProjectName(entity))
                .predictedCategory(entity.getPredictedCategory())
                .predictedBudgetMin(toWan(entity.getPredictedBudgetMin()))
                .predictedBudgetMax(toWan(entity.getPredictedBudgetMax()))
                .predictedWindow(entity.getPredictedWindow())
                .confidence(entity.getConfidence() != null
                        ? entity.getConfidence().doubleValue() : 0.0)
                .reasoningSummary(entity.getReasoningSummary())
                .evidenceRecords(parseCommaSeparatedLongs(entity.getEvidenceRecordIds()))
                .convertedProjectId(entity.getConvertedProjectId())
                .build();
    }

    private static String buildProjectName(final CustomerPrediction entity) {
        String name = Objects.toString(entity.getPurchaserName(), "");
        String window = Objects.toString(entity.getPredictedWindow(), "");
        String category = Objects.toString(entity.getPredictedCategory(), "");
        if (name.isEmpty() && window.isEmpty() && category.isEmpty()) {
            return "待智能研判";
        }
        return name + (window.isEmpty() ? "" : window) + (category.isEmpty() ? "" : category) + "采购";
    }

    private static List<String> parseCommaSeparated(final String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<Long> parseCommaSeparatedLongs(final String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private static long toWan(final BigDecimal value) {
        if (value == null) {
            return 0L;
        }
        return value.divide(BigDecimal.valueOf(WAN_DIVISOR), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
    }
}
