// Input: 项目结果类型 / 客户类型 / 开标时间 / 服务周期（年）/ 服务周期截止时间
// Output: 预测招标时间（Optional<LocalDate>）+ 展示窗口/临近判断
// Pos: workbench/domain/ - 工作台商机预测纯核心策略
package com.xiyu.bid.workbench.domain;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 工作台 AI 商机预测 — 提醒规则纯核心策略。
 *
 * <p>提醒通知渠道不在本策略范围内，本策略只负责"预测下一轮招标时间"：
 * <ol>
 *   <li>流标（FAILED）：预测招标时间 = 开标时间 + 2 周；</li>
 *   <li>有服务周期截止时间：预测招标时间 = 截止时间 − 提前月数（央企提前 6 个月，其他提前 3 个月）；</li>
 *   <li>无截止时间、有项目服务周期（年）：预测招标时间 = 开标时间 + 项目服务周期（年）；</li>
 *   <li>两者皆无：预测招标时间 = 开标时间 + 1 年。</li>
 * </ol>
 *
 * <p>纯函数：无副作用，不依赖 Spring，可独立单测。
 */
@UtilityClass
public class OpportunityPredictionPolicy {

    /** 只显示未来 1 年（12 个月）内的预测商机。 */
    public static final int VISIBLE_WINDOW_MONTHS = 12;

    /** "3 个月内"红点提醒阈值（天）。 */
    public static final long NEAR_THRESHOLD_DAYS = 90;

    /** 流标：开标时间推迟 2 周。 */
    public static final int FAILED_POSTPONE_WEEKS = 2;

    /** 央企：服务周期截止前 6 个月预测招标。 */
    public static final int CENTRAL_SOE_ADVANCE_MONTHS = 6;

    /** 其他客户类型：服务周期截止前 3 个月预测招标。 */
    public static final int DEFAULT_ADVANCE_MONTHS = 3;

    /**
     * 服务周期（年）合理性上限：超过视为脏数据（导入/手填异常），
     * 回退规则 3，避免超大月数导致日期运算异常拖垮整个列表接口。
     */
    public static final int MAX_SERVICE_PERIOD_YEARS = 100;

    /** 央企客户类型枚举名（与 projects.customer_type 存储值一致）。 */
    public static final String CUSTOMER_TYPE_CENTRAL_SOE = "CENTRAL_SOE";

    /** 流标结果类型（与 project_result.result_type 存储值一致）。 */
    public static final String RESULT_FAILED = "FAILED";

    /** 预测所需输入（各字段均可能为空）。 */
    public record Input(
            String resultType,
            String customerType,
            LocalDate bidOpenTime,
            BigDecimal servicePeriodYears,
            LocalDate servicePeriodEndDate
    ) {}

    /**
     * 按提醒规则预测下一轮招标时间。
     *
     * <p>规则优先级：流标 &gt; 服务周期截止时间 &gt; 项目服务周期（年）&gt; 开标时间 + 1 年。
     *
     * @return 规则所需输入缺失（无开标时间且无服务周期截止时间）时返回空
     */
    public static Optional<LocalDate> predict(final Input input) {
        // 规则 4：流标 — 开标时间推迟 2 周（优先于截止时间规则）
        if (RESULT_FAILED.equals(input.resultType())) {
            return input.bidOpenTime() == null
                    ? Optional.empty()
                    : Optional.of(input.bidOpenTime().plusWeeks(FAILED_POSTPONE_WEEKS));
        }
        // 规则 1：有服务周期截止时间 — 央企提前 6 个月，其他提前 3 个月
        if (input.servicePeriodEndDate() != null) {
            int advanceMonths = CUSTOMER_TYPE_CENTRAL_SOE.equals(input.customerType())
                    ? CENTRAL_SOE_ADVANCE_MONTHS
                    : DEFAULT_ADVANCE_MONTHS;
            return Optional.of(input.servicePeriodEndDate().minusMonths(advanceMonths));
        }
        if (input.bidOpenTime() == null) {
            return Optional.empty();
        }
        // 规则 2：无截止时间、有项目服务周期（年）— 开标时间 + 服务周期
        // 超过 MAX_SERVICE_PERIOD_YEARS 视为脏数据，回退规则 3
        if (isReasonablePeriodYears(input.servicePeriodYears())) {
            long months = input.servicePeriodYears()
                    .multiply(BigDecimal.valueOf(12))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            return Optional.of(input.bidOpenTime().plusMonths(months));
        }
        // 规则 3：两者皆无 — 开标时间 + 1 年
        return Optional.of(input.bidOpenTime().plusYears(1));
    }

    /** 服务周期（年）是否有效：非空、为正数且不超过合理性上限（防脏数据）。 */
    private static boolean isReasonablePeriodYears(final BigDecimal years) {
        return years != null
                && years.signum() > 0
                && years.compareTo(BigDecimal.valueOf(MAX_SERVICE_PERIOD_YEARS)) <= 0;
    }

    /** 是否落入展示窗口：预测时间在 [今天, 今天 + 12 个月（1 年）] 内。 */
    public static boolean isVisible(final LocalDate predicted, final LocalDate today) {
        return predicted != null
                && !predicted.isBefore(today)
                && !predicted.isAfter(today.plusMonths(VISIBLE_WINDOW_MONTHS));
    }

    /** 预测时间是否在 3 个月（90 天）内，用于红点高亮提醒。 */
    public static boolean isNear(final LocalDate predicted, final LocalDate today) {
        return predicted != null
                && !predicted.isBefore(today)
                && ChronoUnit.DAYS.between(today, predicted) <= NEAR_THRESHOLD_DAYS;
    }
}
