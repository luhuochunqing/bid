// Input: TenderEvaluationSubmitRequest（评估表请求值载体）
// Output: ValidationResult（字段级错误集合，全部一次性收集；空集合 == 通过）
// Pos: 纯核心层（core）- 不依赖 Spring / JPA / 任何外部框架
// 维护声明: 业务规则的唯一权威；service 层不允许重复校验或绕过此策略。
//          错误码集合是稳定契约：REQUIRED / INVALID_RANGE / MIN_VALUE。
package com.xiyu.bid.tender.core;

import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 标讯项目评估表提交校验策略（纯函数，V1026 字段重构后）。
 *
 * <p>规则（V1026 设计基线）：
 * <ul>
 *   <li>所有基础字段均为 CRM 自动带入，业务层不做强制必填校验</li>
 *   <li>plannedShortlistedCount：如填写则 ≥ 1 → MIN_VALUE</li>
 *   <li>mroOfficeFlowAmount / customerRevenue：如填写则 ≥ 0 → MIN_VALUE</li>
 *   <li>evaluationRecommendation.shouldBid：SUBMIT 时必填（由调用方控制）</li>
 * </ul>
 *
 * <p>调用约定：
 * <ul>
 *   <li>{@code validate(null)} → 抛 NullPointerException（程序员错误，快速失败）</li>
 *   <li>合法输入 → 返回 {@code ValidationResult} 含 0..N 条 FieldError，永不抛出业务错误</li>
 * </ul>
 */
public final class TenderEvaluationFormPolicy {

    private static final String CODE_MIN_VALUE = "MIN_VALUE";

    private TenderEvaluationFormPolicy() {
        // 工具类不可实例化
    }

    /**
     * 校验提交请求。错误一次性收集，不在首条出错时短路。
     *
     * @param req 评估表请求；不可为 null
     * @return 校验结果；调用方按 {@link ValidationResult#isValid()} 分流
     * @throws NullPointerException 当 {@code req == null}
     */
    public static ValidationResult validate(TenderEvaluationSubmitRequest req) {
        Objects.requireNonNull(req, "request must not be null");

        List<FieldError> errors = new ArrayList<>();

        var basic = req.evaluationBasic();
        if (basic != null) {
            // plannedShortlistedCount: 如填写则 ≥ 1
            if (basic.plannedShortlistedCount() != null && basic.plannedShortlistedCount() < 1) {
                errors.add(new FieldError(
                        "plannedShortlistedCount", CODE_MIN_VALUE, "计划入围供应商数量不能小于 1"));
            }
            // mroOfficeFlowAmount: 如填写则 ≥ 0
            if (basic.mroOfficeFlowAmount() != null && basic.mroOfficeFlowAmount().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new FieldError(
                        "mroOfficeFlowAmount", CODE_MIN_VALUE, "电商MRO+办公流水金额不能为负数"));
            }
            // customerRevenue: 如填写则 ≥ 0
            if (basic.customerRevenue() != null && basic.customerRevenue().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new FieldError(
                        "customerRevenue", CODE_MIN_VALUE, "客户营收不能为负数"));
            }
        }

        return new ValidationResult(errors);
    }
}
