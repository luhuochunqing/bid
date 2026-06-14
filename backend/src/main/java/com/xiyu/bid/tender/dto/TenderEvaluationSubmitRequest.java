package com.xiyu.bid.tender.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.xiyu.bid.tender.entity.TenderEvaluation.BidRecommendation;

import java.util.List;

/**
 * 标讯项目评估保存 / 提交请求 DTO（V130 三段式 + V1026 字段重构）。
 * <p>携带完整的 3 段评估数据：基础信息（9 字段）、客户信息（EAV 行列表）、
 * 投标负责人建议（2 字段）。
 * <p>校验仅做基础长度与存在性约束；业务级"必填"由各 Policy 负责。
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.ANY, isGetterVisibility = Visibility.ANY)
public final class TenderEvaluationSubmitRequest {

    private final BidRecommendation bidRecommendation;
    private final EvaluationBasicDTO evaluationBasic;
    private final List<EvaluationCustomerInfoDTO> evaluationCustomerInfos;
    private final EvaluationRecommendationDTO evaluationRecommendation;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TenderEvaluationSubmitRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("bidRecommendation") BidRecommendation bidRecommendation,
            @com.fasterxml.jackson.annotation.JsonProperty("evaluationBasic") EvaluationBasicDTO evaluationBasic,
            @com.fasterxml.jackson.annotation.JsonProperty("evaluationCustomerInfos") List<EvaluationCustomerInfoDTO> evaluationCustomerInfos,
            @com.fasterxml.jackson.annotation.JsonProperty("evaluationRecommendation") EvaluationRecommendationDTO evaluationRecommendation) {
        this.bidRecommendation = bidRecommendation;
        this.evaluationBasic = evaluationBasic;
        this.evaluationCustomerInfos = evaluationCustomerInfos;
        this.evaluationRecommendation = evaluationRecommendation;
    }

    public BidRecommendation bidRecommendation() { return bidRecommendation; }
    public EvaluationBasicDTO evaluationBasic() { return evaluationBasic; }
    public List<EvaluationCustomerInfoDTO> evaluationCustomerInfos() { return evaluationCustomerInfos; }
    public EvaluationRecommendationDTO evaluationRecommendation() { return evaluationRecommendation; }
}
