package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FrontTableAndResponsivenessIntegrationTest {

    @Test
    @DisplayName("前附表具体事实可核查，缺少原始招标条款的偏离声明仍需人工")
    void testEvaluateAll_WhenFrontFactsKnownButDeviationSourceMissing_SeparatesVerdicts() {
        String bidText = """
                第二章 响应说明
                第一节 投标人须知前附表关键条款响应表
                工期响应：承诺60日历天完成
                质保期响应：承诺质保期36个月
                投标有效期响应：承诺本投标有效期90日历天
                交货地点响应：上海市嘉定区
                付款方式响应：满足招标文件付款条件
                质量标准响应：合格

                第二节 商务与技术规格偏离表
                我司郑重承诺：完全响应招标文件全部商务和技术条款，无任何偏离。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", bidText);
        FrontTableRequirement frontReq = new FrontTableRequirement(
                true, "测试项目", "ZB001", "60日历天", "36个月", "90日历天", "上海市", "货到付款", "合格", Map.of(), null);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(), false)
                .withFrontTableRequirement(frontReq);

        AtomicInteger llmCallCount = new AtomicInteger();
        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.FRONT_TABLE_RESPONSE.getTitle() + "」")
                    || prompt.contains("裁决检查项「" + BidQualityCheckItem.RESPONSIVENESS_REVIEW.getTitle() + "」")) {
                llmCallCount.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "通过", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, DocumentContent.empty(), Collections.emptyList(), req, Set.of(), llm, (p, s) -> {});

        QualityCheckItemResult frontResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();
        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(llmCallCount.get())
                .as("强事实直通场景，前附表与偏离表坚决不触发 LLM 调用")
                .isEqualTo(0);
        assertThat(frontResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(frontResult.conclusion()).contains("前附表关键条款填写完整");
        assertThat(respResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(respResult.conclusion()).contains("招标原文不可用");
    }

    @Test
    @DisplayName("前附表与偏离表引擎集成：未填占位符与负偏离直接 FAIL 硬拦截")
    void testEvaluateAll_WhenPlaceholderAndNegativeDeviation_FailsDirectly() {
        String bidText = """
                第一节 投标人须知前附表响应表
                工期：_______日历天
                质保期：【请填写质保期】

                第二节 偏离表
                条款2.1交货期：招标要求30天，我方45天，存在负偏离。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_fail", bidText);
        TenderRequirements req = new TenderRequirements(
                List.of(), List.of(), List.of(), List.of(), List.of(), false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, DocumentContent.empty(), Collections.emptyList(), req, Set.of());

        QualityCheckItemResult frontResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();
        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(frontResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(frontResult.conclusion()).contains("占位符或下划线留白");
        assertThat(respResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(respResult.conclusion()).contains("实质性负偏离");
    }
}
