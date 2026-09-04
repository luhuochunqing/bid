package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project 1371 / Project 21 MRO：用户可见四列契约（权重 / 分类 / 顺序）。
 * <p>走真实金标原文 → 表格召回 → 排序策略，禁止用手写 dim 路径自证。
 */
class Project1371ColumnInvariantsTest {

    private final KnowledgeCategoryPolicy categoryPolicy = new KnowledgeCategoryPolicy();
    private final ScoreItemOrderPolicy orderPolicy = new ScoreItemOrderPolicy();

    @Test
    @DisplayName("1371 权重是标书格子里的绝对分：财务 3、仓储 3、合计 100，禁止档位分冒充权重")
    void weightsAreAbsoluteScoresPrintedInTender() throws Exception {
        List<ScoreCandidate> items = recallAndSort();

        assertThat(find(items, "财务状况").weight()).isEqualByComparingTo("3");
        assertThat(find(items, "服务网点及仓储").weight()).isEqualByComparingTo("3");
        assertThat(find(items, "供应商业绩").weight()).isEqualByComparingTo("7");
        assertThat(items.stream().map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("1371 分类看因素名：财务≠人员，综合服务方案≠品牌，仓储走仓库")
    void categoryUsesFactorNameNotDetailLeak() throws Exception {
        List<ScoreCandidate> items = recallAndSort();

        ScoreCandidate finance = find(items, "财务状况");
        assertThat(categoryPolicy.categorize(finance.dim(), finance.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);

        ScoreCandidate warehouse = find(items, "服务网点及仓储");
        assertThat(categoryPolicy.categorize(warehouse.dim(), warehouse.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE);

        ScoreCandidate plan = find(items, "综合服务方案");
        assertThat(categoryPolicy.categorize(plan.dim(), plan.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);

        ScoreCandidate quality = find(items, "产品质量及价格");
        assertThat(categoryPolicy.categorize(quality.dim(), quality.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);

        ScoreCandidate logistics = find(items, "物流仓储方案");
        assertThat(categoryPolicy.categorize(logistics.dim(), logistics.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);

        ScoreCandidate team = find(items, "服务团队方案");
        assertThat(categoryPolicy.categorize(team.dim(), team.detail()))
                .isEqualTo(KnowledgeCategoryPolicy.CATEGORY_OTHER);
    }

    @Test
    @DisplayName("1371 顺序按表格物理行：商务因素在技术之前，技术在报价之前，局部 1/2 不跨组插队")
    void orderFollowsTablePhysicalRows() throws Exception {
        List<ScoreCandidate> items = recallAndSort();
        List<String> dims = items.stream().map(ScoreCandidate::dim).toList();

        int performance = indexContaining(dims, "供应商业绩");
        int finance = indexContaining(dims, "财务状况");
        int warehouse = indexContaining(dims, "服务网点及仓储");
        int servicePlan = indexContaining(dims, "综合服务方案");
        int jingdong = indexContaining(dims, "京东工业品");
        int ownMall = indexContaining(dims, "自有商城");

        assertThat(performance).isLessThan(finance);
        assertThat(finance).isLessThan(warehouse);
        assertThat(warehouse).isLessThan(servicePlan);
        assertThat(servicePlan).isLessThan(jingdong);
        assertThat(jingdong).isLessThan(ownMall);
    }

    private List<ScoreCandidate> recallAndSort() throws Exception {
        String md;
        try (var in = getClass().getResourceAsStream("/scoreparse-golden/project21-mro.md")) {
            assertThat(in).as("金标夹具 project21-mro.md 必须存在").isNotNull();
            md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return orderPolicy.sort(ScoreTableRecallLane.recall(md));
    }

    private static ScoreCandidate find(List<ScoreCandidate> items, String dimFragment) {
        return items.stream()
                .filter(c -> c.dim() != null && c.dim().contains(dimFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到评分项: " + dimFragment));
    }

    private static int indexContaining(List<String> dims, String fragment) {
        for (int i = 0; i < dims.size(); i++) {
            if (dims.get(i) != null && dims.get(i).contains(fragment)) {
                return i;
            }
        }
        throw new AssertionError("顺序断言找不到: " + fragment);
    }
}
