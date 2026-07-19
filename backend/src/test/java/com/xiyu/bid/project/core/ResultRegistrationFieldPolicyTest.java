// Input: 4 result types × 字段/证据/合同信息 必填矩阵
// Output: JUnit5 断言覆盖 PRD §3.4.2 必填校验（含 CO-590 合同信息两字段）
// Pos: backend test source - pure JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultRegistrationFieldPolicyTest {

    private static final BigDecimal SAMPLE_PERIOD_YEARS = new BigDecimal("3.5");
    private static final LocalDate SAMPLE_PERIOD_END = LocalDate.of(2027, 6, 1);

    @Test
    void won_complete_allowed() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100000"))
                .contractStartDate(LocalDate.of(2026, 6, 1))
                .contractEndDate(LocalDate.of(2027, 6, 1))
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(101L))
                .summary("中标通知书已上传")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        assertTrue(d.allowed());
    }

    @Test
    void won_missingEvidence_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.WON)
                .awardAmount(new BigDecimal("100000"))
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        assertFalse(d.allowed());
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("evidenceFileIds"),
                "中标必须上传中标通知书证据");
    }

    @Test
    void lost_complete_allowed() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.LOST)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(202L))
                .summary("竞争对手 X 中标")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        assertTrue(d.allowed());
    }

    @Test
    void lost_missingEvidence_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.LOST)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .summary("竞争对手 X 中标")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("evidenceFileIds"),
                "未中标需上传中标公告作为证据");
    }

    @Test
    void failed_complete_allowed() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.FAILED)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(303L))
                .summary("流标说明：投标人不足 3 家")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        assertTrue(d.allowed());
    }

    @Test
    void failed_missingSummary_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.FAILED)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(303L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("summary"),
                "流标必须说明原因");
    }

    @Test
    void lost_missingSummary_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.LOST)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(303L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("summary"),
                "未中标必须填写丢标原因");
    }

    @Test
    void abandoned_complete_allowed() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.ABANDONED)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(404L))
                .summary("弃标决策说明")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        assertTrue(d.allowed());
    }

    @Test
    void abandoned_missingSummary_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.ABANDONED)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(404L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("summary"),
                "弃标必须说明决策依据");
    }

    @Test
    void abandoned_emptyEvidence_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.ABANDONED)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of())
                .summary("弃标说明")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("evidenceFileIds"));
    }

    @Test
    void nullResultType_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .evidenceFileIds(List.of(1L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("resultType"));
    }

    @Test
    void nullInput_denied() {
        var d = ResultRegistrationFieldPolicy.validate(null);
        assertFalse(d.allowed());
    }

    @Test
    void won_missingAll_aggregatesMissingFields() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.WON).build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        // CO-320: WON 必填 evidenceFileIds；CO-590: 所有结果类型必填 servicePeriodYears + servicePeriodEndDate
        assertEquals(3, deny.missing().size(), "expected evidenceFileIds + 合同信息两字段");
        assertTrue(deny.missing().contains("evidenceFileIds"));
        assertTrue(deny.missing().contains("servicePeriodYears"));
        assertTrue(deny.missing().contains("servicePeriodEndDate"));
    }

    // ===== CO-590: 合同信息模块必填校验 =====

    @Test
    void won_missingServicePeriodYears_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.WON)
                .servicePeriodEndDate(SAMPLE_PERIOD_END)
                .evidenceFileIds(List.of(101L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("servicePeriodYears"),
                "CO-590: 项目服务周期(年)必填");
    }

    @Test
    void won_missingServicePeriodEndDate_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.WON)
                .servicePeriodYears(SAMPLE_PERIOD_YEARS)
                .evidenceFileIds(List.of(101L))
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("servicePeriodEndDate"),
                "CO-590: 服务周期截止时间必填");
    }

    @Test
    void lost_missingContractInfo_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.LOST)
                .evidenceFileIds(List.of(202L))
                .summary("竞争对手 X 中标")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("servicePeriodYears"));
        assertTrue(deny.missing().contains("servicePeriodEndDate"));
    }

    @Test
    void abandoned_missingContractInfo_denied() {
        var input = ResultRegistrationFieldPolicy.ResultInput.builder()
                .resultType(BidResultType.ABANDONED)
                .evidenceFileIds(List.of(404L))
                .summary("弃标决策说明")
                .build();
        var d = ResultRegistrationFieldPolicy.validate(input);
        var deny = assertInstanceOf(ResultRegistrationFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains("servicePeriodYears"));
        assertTrue(deny.missing().contains("servicePeriodEndDate"));
    }
}
