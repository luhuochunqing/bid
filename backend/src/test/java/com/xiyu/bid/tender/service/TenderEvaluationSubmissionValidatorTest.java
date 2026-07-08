package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.core.ValidationResult;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderEvaluationSubmissionValidator - 评估表提交校验")
class TenderEvaluationSubmissionValidatorTest {

    @Test
    @DisplayName("CRM 对接人 CONTACT_METHOD 为空时仍可提交成功")
    void validate_blankContactMethod_fromCrm_isValid() {
        TenderEvaluationSubmitRequest req = buildRequest(
                List.of(
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "NAME", "张三", "TEXT"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_INFO", "18688888888", "TEXT"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_METHOD", "", "ENUM7"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_GENERAL_MANAGER", "NAME", "李四", "TEXT")
                ),
                new EvaluationRecommendationDTO(true, null)
        );

        ValidationResult result = TenderEvaluationSubmissionValidator.validate(req);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("完整评估表请求校验通过")
    void validate_completeRequest_isValid() {
        TenderEvaluationSubmitRequest req = buildRequest(
                List.of(
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "NAME", "张三", "TEXT"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_METHOD", "1", "ENUM7"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACTED", "是", "DROPDOWN"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "INFO_CLEAR_WINNER_BID", "false", "SWITCH")
                ),
                new EvaluationRecommendationDTO(true, null)
        );

        ValidationResult result = TenderEvaluationSubmissionValidator.validate(req);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("未选择是否投标时仍然校验失败")
    void validate_missingShouldBid_isInvalid() {
        TenderEvaluationSubmitRequest req = buildRequest(
                List.of(
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "NAME", "张三", "TEXT"),
                        new EvaluationCustomerInfoDTO("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_METHOD", "1", "ENUM7")
                ),
                new EvaluationRecommendationDTO(null, null)
        );

        ValidationResult result = TenderEvaluationSubmissionValidator.validate(req);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors())
                .extracting(com.xiyu.bid.tender.core.FieldError::message)
                .contains("是否投标不能为空");
    }

    private TenderEvaluationSubmitRequest buildRequest(
            List<EvaluationCustomerInfoDTO> customerInfos,
            EvaluationRecommendationDTO recommendation) {
        return new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND,
                new EvaluationBasicDTO(2, null, null, null, null, null, null, null, null, null),
                customerInfos,
                recommendation
        );
    }
}
