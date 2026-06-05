// Input: TenderEvaluationSubmitRequest
// Output: ValidationResult
// Pos: 纯核心层（core）- 不依赖 Spring / JPA
// 维护声明: 仅做三段式数据完整性校验；不携带任何业务编排逻辑。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.core.FieldError;
import com.xiyu.bid.tender.core.TenderEvaluationCustomerInfoPolicy;
import com.xiyu.bid.tender.core.TenderEvaluationFormPolicy;
import com.xiyu.bid.tender.core.ValidationResult;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标讯项目评估表三段式数据完整性校验（V130）。
 * <p>纯核心层：无状态，无 Spring 依赖。一次性收集全部错误，不做首条短路。
 * <p>校验范围：
 * <ul>
 *   <li>原有 7 字段校验（{@link TenderEvaluationFormPolicy}）</li>
 *   <li>客户信息段 EAV 行校验（{@link TenderEvaluationCustomerInfoPolicy}）</li>
 *   <li>投标负责人建议段校验（shouldBid / reason）</li>
 * </ul>
 */
public final class TenderEvaluationSubmissionValidator {

    private static final String CODE_REQUIRED = "REQUIRED";

    private TenderEvaluationSubmissionValidator() {
        // 工具类，禁止实例化
    }

    /**
     * 执行三段式完整性校验，返回收集到的全部错误（无错误时 {@link ValidationResult#isValid()} = true）。
     */
    public static ValidationResult validate(TenderEvaluationSubmitRequest req) {
        List<FieldError> allErrors = new ArrayList<>();

        // 1) 原有 7 字段校验（TenderEvaluationFormPolicy）
        ValidationResult formResult = TenderEvaluationFormPolicy.validate(req);
        if (!formResult.isValid()) {
            allErrors.addAll(formResult.errors());
        }

        // 2) 客户信息段校验（TenderEvaluationCustomerInfoPolicy）
        List<TenderEvaluationCustomerInfoPolicy.CustomerInfoRow> customerRows = null;
        if (req.evaluationCustomerInfos() != null) {
            customerRows = req.evaluationCustomerInfos().stream()
                    .map(dto -> new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                            dto.roleKey(), dto.infoKey(), dto.value(), dto.valueType()))
                    .collect(Collectors.toList());
        }
        ValidationResult customerResult = TenderEvaluationCustomerInfoPolicy.validate(customerRows);
        if (!customerResult.isValid()) {
            allErrors.addAll(customerResult.errors());
        }

        // 3) 投标负责人建议段校验
        if (req.evaluationRecommendation() != null) {
            if (req.evaluationRecommendation().shouldBid() == null) {
                allErrors.add(new FieldError(
                        "evaluationRecommendation.shouldBid", CODE_REQUIRED, "是否投标不能为空"));
            } else if (!req.evaluationRecommendation().shouldBid()
                    && (req.evaluationRecommendation().reason() == null
                    || req.evaluationRecommendation().reason().isBlank())) {
                allErrors.add(new FieldError(
                        "evaluationRecommendation.reason", CODE_REQUIRED, "不建议投标时请填写理由"));
            }
        }

        return new ValidationResult(allErrors);
    }
}
