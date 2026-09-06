package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringAndClarificationIntegrationTest {

    @Test
    @DisplayName("评分响应与澄清响应集成：占位留白硬拦截 FAIL，不依赖大模型")
    void testEvaluateAll_WhenPlaceholdersPresent_FailsDirectly() {
        String bidText = """
                第三章 技术方案
                总体实施规划：【此处填写实施方案】
                
                第五章 澄清答疑确认函
                我司已收到第【填写澄清编号】号澄清答疑文件。
                投标人盖章：_______
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid1", bidText);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender1", "招标文件发布第1号澄清文件并要求澄清回执");
        TenderRequirements req = new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("技术方案", 40.0, "满分要求")),
                List.of(), List.of(), List.of(), List.of(), false);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, tenderDoc, Collections.emptyList(), req, Set.of());

        QualityCheckItemResult scoringResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.SCORING_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();
        QualityCheckItemResult clarResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CLARIFICATION_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(scoringResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(scoringResult.conclusion()).contains("占位符或下划线留白");
        assertThat(clarResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(clarResult.conclusion()).contains("占位符或下划线留白");
    }

    @Test
    @DisplayName("评分响应与澄清响应集成：真实确认函与无澄清项目直通 PASS，无硬伤技术方案走 LLM 深度评分")
    void testEvaluateAll_WhenCompliant_ClarificationPassesAndScoringEvaluatedByLlm() {
        String bidText = """
                第三章 评分标准逐项对应表
                序号 | 评分项 | 招标要求 | 投标响应描述 | 对应正文页码
                1    | 技术方案 | 微服务高可用 | 采用Spring Boot + K8s分布式架构 | 第 20 页
                
                第四章 技术方案正文
                系统采用微服务高可用架构，支持动态弹性扩缩容。
                
                第五章 澄清答疑确认函
                关于招标文件第 1 号澄清答疑文件的确认函
                致采购人：我方已收悉第1号澄清文件，相关答疑要求已全部吸收进投标响应中。
                投标人：西域数字科技（盖章）
                日期：2026年9月6日
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid2", bidText);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender2", "招标文件包含技术评分标准与第1号澄清答疑文件");
        TenderRequirements req = new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("技术方案", 40.0, "微服务架构")),
                List.of(), List.of(), List.of(), List.of(), false);

        AtomicInteger scoringLlmCalls = new AtomicInteger();
        AtomicInteger clarLlmCalls = new AtomicInteger();

        LlmAdjudicator llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.SCORING_RESPONSE.getTitle() + "」")) {
                scoringLlmCalls.incrementAndGet();
                assertThat(prompt).contains("16. 若检查项为【评分标准逐项对应与支撑材料】");
            }
            if (prompt.contains("裁决检查项「" + BidQualityCheckItem.CLARIFICATION_RESPONSE.getTitle() + "」")) {
                clarLlmCalls.incrementAndGet();
            }
            return LlmVerdict.success(new LlmVerdict("PASS", "", "各项指标全面响应", ""));
        });

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                bidDoc, tenderDoc, Collections.emptyList(), req, Set.of(), llm, (p, s) -> {});

        QualityCheckItemResult scoringResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.SCORING_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();
        QualityCheckItemResult clarResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.CLARIFICATION_RESPONSE.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(clarLlmCalls.get())
                .as("真实完备确认函直接在第一阶段 PASS，不调用大模型")
                .isEqualTo(0);
        assertThat(clarResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(clarResult.conclusion()).contains("实质性确认并响应答疑事项");

        assertThat(scoringLlmCalls.get())
                .as("无占位留白的规范技术方案移交 LLM 并携带准则 16 进行深度语义评分")
                .isEqualTo(1);
        assertThat(scoringResult.status()).isEqualTo(QualityCheckStatus.PASS);
    }
}
