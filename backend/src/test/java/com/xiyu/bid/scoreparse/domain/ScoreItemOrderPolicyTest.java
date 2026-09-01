// Input: ScoreItemOrderPolicy（sort 方法）
// Output: 编号数值序排序行为验证（spec 046 FR-003 / FR-004）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemOrderPolicyTest {

    private final ScoreItemOrderPolicy policy = new ScoreItemOrderPolicy();

    @Test
    void sort_codesOrderedNumerically() {
        // project 212 实测编号乱序：6,7,1,2,3,4-1,4-2,5 → 1,2,3,4-1,4-2,5,6,7
        List<ScoreCandidate> result =
                policy.sort(candidates("6", "7", "1", "2", "3", "4-1", "4-2", "5"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("1", "2", "3", "4-1", "4-2", "5", "6", "7");
    }

    @Test
    void sort_unparsableCodes_stableRelativeOrderAtEnd() {
        // "A1"/"B2" 不可解析：稳定保持相对序，排在可解析之后
        List<ScoreCandidate> result = policy.sort(candidates("A1", "1", "B2", "2"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("1", "2", "A1", "B2");
    }

    @Test
    void sort_subSegmentNumericOrder_noMinorFirst() {
        // "4" < "4-1" < "4-2"：同 major 无 minor 排前；分隔符 -/—/. 等价；同键稳定保持相对序
        List<ScoreCandidate> result = policy.sort(candidates("4-2", "4", "4—1", "4.2", "2"));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("2", "4", "4—1", "4-2", "4.2");
    }

    @Test
    void sort_nullOrBlankCode_unparsedGroup() {
        List<ScoreCandidate> result = policy.sort(candidates(null, "2", "  "));

        assertThat(result).extracting(ScoreCandidate::code)
                .containsExactly("2", null, "  ");
    }

    @Test
    void sort_inputNotModified() {
        List<ScoreCandidate> input = new ArrayList<>(candidates("6", "1", "A1"));
        List<ScoreCandidate> snapshot = List.copyOf(input);

        policy.sort(input);

        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    @Test
    void sort_nullInput_returnsEmpty() {
        assertThat(policy.sort(null)).isEmpty();
    }

    @Test
    void sort_multiCategory_localNumberingNotScrambled() {
        // Project 21 / 1371 真实场景：商务、技术、报价各包含局部编号 1、2，不可跨大类全局杂糅
        List<ScoreCandidate> input = List.of(
                candidate("3.2.3", "商务评分 / 供应商业绩", "供应商业绩 7分", "7"),
                candidate("1", "商务评分 / 财务状况 / 现金流量净额", "现金流量净额 1.5分", "1.5"),
                candidate("2", "商务评分 / 财务状况 / 净利润", "净利润 1.5分", "1.5"),
                candidate("1", "商务评分 / 服务网点及仓储 / 服务网点", "服务网点 1.5分", "1.5"),
                candidate("2", "商务评分 / 服务网点及仓储 / 仓储地点", "仓储地点 1.5分", "1.5"),
                candidate(null, "商务评分 / 电子商城运营时间", "电子商城 1分", "1"),
                candidate(null, "商务评分 / 授信额度", "授信额度 1分", "1"),
                candidate(null, "技术评分 / 综合服务方案", "综合服务方案 10分", "10"),
                candidate(null, "技术评分 / 产品质量及价格", "产品质量及价格 10分", "10"),
                candidate(null, "技术评分 / 物流仓储方案", "物流仓储方案 8分", "8"),
                candidate(null, "技术评分 / 服务团队方案", "服务团队方案 10分", "10"),
                candidate(null, "技术评分 / 平台系统对接方案", "平台系统对接方案 3分", "3"),
                candidate(null, "技术评分 / 增值服务方案", "增值服务方案 4分", "4"),
                candidate("1", "报价评分 / 上架折扣率（京东工业品）价格分", "京东折扣 28分", "28"),
                candidate("2", "报价评分 / 上架折扣率（自有商城）价格分", "自有商城折扣 12分", "12")
        );

        List<ScoreCandidate> result = policy.sort(input);

        // 验证大类顺序严格保持：商务(7项) -> 技术(6项) -> 报价(2项)
        assertThat(result).hasSize(15);
        assertThat(result.subList(0, 7)).allMatch(c -> c.dim().contains("商务"));
        assertThat(result.subList(7, 13)).allMatch(c -> c.dim().contains("技术"));
        assertThat(result.subList(13, 15)).allMatch(c -> c.dim().contains("报价"));

        // 验证各维度内部的局部编号顺序
        assertThat(result.get(0).dim()).contains("供应商业绩");
        assertThat(result.get(1).dim()).contains("现金流量净额");
        assertThat(result.get(2).dim()).contains("净利润");
        assertThat(result.get(3).dim()).contains("服务网点");
        assertThat(result.get(4).dim()).contains("仓储地点");

        assertThat(result.get(13).dim()).contains("京东工业品");
        assertThat(result.get(14).dim()).contains("自有商城");
    }

    @Test
    void sort_sameParentDim_localReorder() {
        // 同一父维度内子项编号出现倒序（如 2 出现在 1 之前）时，自动纠正为 1、2
        List<ScoreCandidate> input = List.of(
                candidate("2", "商务评分 / 财务状况 / 净利润", "净利润 1.5分", "1.5"),
                candidate("1", "商务评分 / 财务状况 / 现金流量净额", "现金流量净额 1.5分", "1.5")
        );

        List<ScoreCandidate> result = policy.sort(input);

        assertThat(result).extracting(ScoreCandidate::code).containsExactly("1", "2");
        assertThat(result.get(0).detail()).contains("现金流量净额");
        assertThat(result.get(1).detail()).contains("净利润");
    }

    private List<ScoreCandidate> candidates(String... codes) {
        List<ScoreCandidate> list = new ArrayList<>();
        for (String code : codes) {
            list.add(candidate(code, "维度", "要素", "10"));
        }
        return list;
    }

    private ScoreCandidate candidate(String code, String dim, String detail, String weight) {
        return new ScoreCandidate(code, dim, detail, new BigDecimal(weight),
                null, null, detail, "P1", null);
    }
}
