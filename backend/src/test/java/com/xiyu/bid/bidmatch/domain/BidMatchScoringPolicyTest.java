package com.xiyu.bid.bidmatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BidMatchScoringPolicyTest {

    private final BidMatchScoringPolicy policy = new BidMatchScoringPolicy();

    @Test
    @DisplayName("模型校验要求启用维度权重合计为100，且启用规则权重合计为100")
    void validate_ShouldRejectInvalidEnabledWeights() {
        BidMatchScoringModel model = new BidMatchScoringModel(
                null,
                "默认标讯匹配模型",
                "用于标讯匹配的首版评分模型",
                List.of(
                        BidMatchDimension.enabled("tender", "标讯文本", 80, List.of(
                                BidMatchRule.keywordAny("keyword", "关键词命中", "tender.searchText", List.of("智慧园区"), 70)
                        )),
                        BidMatchDimension.enabled("case", "案例经验", 10, List.of(
                                BidMatchRule.quantityAtLeast("wonCases", "中标案例数量", "case.wonCount", new BigDecimal("1"), 100)
                        ))
                ),
                1
        );

        ValidationResult result = policy.validate(model);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .contains("启用维度权重合计必须为100")
                .contains("维度 tender 的启用规则权重合计必须为100");
    }

    @Test
    @DisplayName("禁用维度不参与权重校验，也不参与最终得分")
    void evaluate_ShouldIgnoreDisabledDimensions() {
        BidMatchScoringModel model = new BidMatchScoringModel(
                3L,
                "默认标讯匹配模型",
                "禁用维度不计分",
                List.of(
                        BidMatchDimension.enabled("tender", "标讯文本", 100, List.of(
                                BidMatchRule.keywordAny("keyword", "关键词命中", "tender.searchText", List.of("智慧园区"), 100)
                        )),
                        BidMatchDimension.disabled("qualification", "企业资质", 90, List.of(
                                BidMatchRule.exists("qualificationExists", "存在有效资质", "qualification.active", 100)
                        ))
                ),
                1
        );
        BidMatchModelVersionSnapshot snapshot = policy.createSnapshot(model, 3L, 1).snapshot().orElseThrow();

        BidMatchScoreEvaluation evaluation = policy.evaluate(snapshot, evidence(
                Map.of("tender.searchText", "智慧园区运营平台"),
                Map.of(),
                Set.of()
        ));

        assertThat(policy.validate(model).valid()).isTrue();
        assertThat(evaluation.totalScore()).isEqualByComparingTo("100.00");
        assertThat(evaluation.dimensionScores()).extracting(BidMatchDimensionScore::code)
                .containsExactly("tender");
    }

    @Test
    @DisplayName("缺少证据时规则得0分，并保留缺证据状态")
    void evaluate_ShouldRecordMissingEvidence() {
        BidMatchModelVersionSnapshot snapshot = policy.createSnapshot(singleDimensionModel(
                BidMatchRule.exists("qualificationExists", "存在有效资质", "qualification.active", 100)
        ), 1L, 1).snapshot().orElseThrow();

        BidMatchScoreEvaluation evaluation = policy.evaluate(snapshot, evidence(
                Map.of(),
                Map.of(),
                Set.of()
        ));

        BidMatchRuleScore ruleScore = evaluation.dimensionScores().getFirst().ruleScores().getFirst();
        assertThat(evaluation.totalScore()).isEqualByComparingTo("0.00");
        assertThat(ruleScore.status()).isEqualTo(MatchRuleEvaluationStatus.MISSING);
        assertThat(ruleScore.matched()).isFalse();
    }

    @Test
    @DisplayName("关键词、存在、数量、区间规则分别按证据计算命中")
    void evaluate_ShouldSupportKeywordExistsQuantityAndRangeRules() {
        BidMatchScoringModel model = new BidMatchScoringModel(
                9L,
                "默认标讯匹配模型",
                "覆盖四类规则",
                List.of(
                        BidMatchDimension.enabled("tender", "标讯匹配", 100, List.of(
                                BidMatchRule.keywordAny("keyword", "关键词命中", "tender.searchText", List.of("智慧园区", "数字化"), 25),
                                BidMatchRule.exists("budgetExists", "存在预算", "tender.budget", 25),
                                BidMatchRule.quantityAtLeast("wonCases", "中标案例数量", "case.wonCount", new BigDecimal("2"), 25),
                                BidMatchRule.range("budgetRange", "预算区间", "tender.budget", new BigDecimal("500000"), new BigDecimal("1500000"), 25)
                        ))
                ),
                1
        );
        BidMatchModelVersionSnapshot snapshot = policy.createSnapshot(model, 9L, 1).snapshot().orElseThrow();

        BidMatchScoreEvaluation evaluation = policy.evaluate(snapshot, evidence(
                Map.of("tender.searchText", "智慧园区数字化运维平台建设"),
                Map.of(
                        "case.wonCount", new BigDecimal("3"),
                        "tender.budget", new BigDecimal("980000")
                ),
                Set.of("tender.budget")
        ));

        assertThat(evaluation.totalScore()).isEqualByComparingTo("100.00");
        assertThat(evaluation.dimensionScores().getFirst().ruleScores())
                .extracting(BidMatchRuleScore::status)
                .containsExactly(
                        MatchRuleEvaluationStatus.MATCHED,
                        MatchRuleEvaluationStatus.MATCHED,
                        MatchRuleEvaluationStatus.MATCHED,
                        MatchRuleEvaluationStatus.MATCHED
                );
    }

    @Test
    @DisplayName("维度分保持0到100，总分按维度权重汇总")
    void evaluate_ShouldKeepDimensionScoreIndependentFromDimensionWeight() {
        BidMatchScoringModel model = new BidMatchScoringModel(
                10L,
                "自定义投标匹配模型",
                "两个自定义维度",
                List.of(
                        BidMatchDimension.enabled("budget", "预算匹配", 60, List.of(
                                BidMatchRule.exists("budgetExists", "预算存在", "tender.budget", 100)
                        )),
                        BidMatchDimension.enabled("case", "案例匹配", 40, List.of(
                                BidMatchRule.exists("caseExists", "案例存在", "case.searchText", 100)
                        ))
                ),
                1
        );
        BidMatchModelVersionSnapshot snapshot = policy.createSnapshot(model, 10L, 1).snapshot().orElseThrow();

        BidMatchScoreEvaluation evaluation = policy.evaluate(snapshot, evidence(
                Map.of(),
                Map.of("tender.budget", new BigDecimal("500000")),
                Set.of("tender.budget")
        ));

        assertThat(evaluation.totalScore()).isEqualByComparingTo("60.00");
        assertThat(evaluation.dimensionScores()).extracting(BidMatchDimensionScore::score)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("历史版本快照独立于草稿后续修改")
    void createSnapshot_ShouldKeepHistoricalVersionStable() {
        BidMatchScoringModel draft = singleDimensionModel(
                BidMatchRule.keywordAny("keyword", "关键词命中", "tender.searchText", List.of("智慧园区"), 100)
        );

        BidMatchModelVersionSnapshot snapshot = policy.createSnapshot(draft, 1L, 1).snapshot().orElseThrow();
        BidMatchScoringModel changedDraft = new BidMatchScoringModel(
                draft.id(),
                draft.name(),
                draft.description(),
                List.of(BidMatchDimension.enabled("tender", "标讯文本", 100, List.of(
                        BidMatchRule.keywordAny("keyword", "关键词命中", "tender.searchText", List.of("低空经济"), 100)
                ))),
                draft.draftRevision() + 1
        );

        BidMatchScoreEvaluation historicalEvaluation = policy.evaluate(snapshot, evidence(
                Map.of("tender.searchText", "智慧园区运营平台"),
                Map.of(),
                Set.of()
        ));
        BidMatchScoreEvaluation currentDraftEvaluation = policy.evaluate(
                policy.createSnapshot(changedDraft, 1L, 2).snapshot().orElseThrow(),
                evidence(Map.of("tender.searchText", "智慧园区运营平台"), Map.of(), Set.of())
        );

        assertThat(historicalEvaluation.totalScore()).isEqualByComparingTo("100.00");
        assertThat(currentDraftEvaluation.totalScore()).isEqualByComparingTo("0.00");
        assertThat(snapshot.versionNo()).isEqualTo(1);
        assertThat(snapshot.model().dimensions().getFirst().rules().getFirst().keywords())
                .containsExactly("智慧园区");
    }

    private BidMatchScoringModel singleDimensionModel(BidMatchRule rule) {
        return new BidMatchScoringModel(
                1L,
                "默认标讯匹配模型",
                "单维度模型",
                List.of(BidMatchDimension.enabled("tender", "标讯文本", 100, List.of(rule))),
                1
        );
    }

    private MatchEvidence evidence(
            Map<String, String> texts,
            Map<String, BigDecimal> numbers,
            Set<String> presentKeys
    ) {
        return new MatchEvidence("evidence-v1", texts, numbers, presentKeys);
    }
}
