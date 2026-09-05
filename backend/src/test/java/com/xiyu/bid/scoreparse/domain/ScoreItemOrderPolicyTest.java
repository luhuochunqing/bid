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
                .containsExactly("2", "", "");
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
    void sort_duplicateLocalCodesSameCategory_keepsDocumentOrder() {
        // 1371：商务大类下财务/网点各自复用 1、2。禁止按编号全局插队成 1,1,2,2
        List<ScoreCandidate> input = List.of(
                candidate("1", "商务评分", "财务现金流 1.5分", "1.5"),
                candidate("2", "商务评分", "财务净利润 1.5分", "1.5"),
                candidate("1", "商务评分", "服务网点 1.5分", "1.5"),
                candidate("2", "商务评分", "仓储地点 1.5分", "1.5")
        );

        List<ScoreCandidate> result = policy.sort(input);

        assertThat(result).extracting(ScoreCandidate::detail)
                .containsExactly("财务现金流 1.5分", "财务净利润 1.5分", "服务网点 1.5分", "仓储地点 1.5分");
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

    @Test
    void sort_businessAndTechnicalOrderNotScrambledByLeafNames() {
        // Table 2 items and Table 3 items: leaf containing "方案" must not be grouped into Table 3
        List<ScoreCandidate> input = List.of(
                candidate("1", "四、综合评价 / 库存评价", "库存评价 5分", "5"),
                candidate("2", "四、综合评价 / 供货保障解决方案", "供货保障解决方案 5分", "5"),
                candidate("3", "四、综合评价 / 紧急需求应急响应", "紧急需求应急响应 15分", "15"),
                candidate("4", "应答人交易平台与履约交易平台对接方案", "对接方案 6分", "6")
        );

        List<ScoreCandidate> result = policy.sort(input);
        assertThat(result.get(0).dim()).contains("库存评价");
        assertThat(result.get(1).dim()).contains("供货保障解决方案");
        assertThat(result.get(2).dim()).contains("紧急需求应急响应");
        assertThat(result.get(3).dim()).contains("对接方案");
    }

    @Test
    void sort_project1359Items_shuffledInputRestoredToNaturalDocumentAndNumericOrder() {
        // 真实 1359 场景：故意打乱中文大类和综合评价子点顺序（第四点综合评价小点乱序传入）
        // 排序策略必须通过中文章节序号（一、二、三、四）和局部数值序（1..6）将乱序恢复为规范顺序
        ScoreCandidate item1 = candidate("1", "一、诚信评价 / 公共信用信息报告", "公共信用 0分", "0");
        ScoreCandidate item2 = candidate("1", "二、财务状况 / 财务综合评价", "财务状况 25分", "25");
        ScoreCandidate item3 = candidate("1", "三、绿色数智评价 / 绿色发展规划", "绿色规划 4分", "4");
        ScoreCandidate item4_1 = candidate("1", "四、综合评价 / 库存充足性", "库存充足性 5分", "5");
        ScoreCandidate item4_2 = candidate("2", "四、综合评价 / 供货保障解决方案", "供货保障解决方案 5分", "5");
        ScoreCandidate item4_3 = candidate("3", "四、综合评价 / 紧急需求应急响应", "紧急需求应急响应 15分", "15");
        ScoreCandidate item4_4 = candidate("4", "四、综合评价 / 应急保障能力", "应急保障能力 10分", "10");
        ScoreCandidate item4_5 = candidate("5", "四、综合评价 / 应答响应", "应答响应 15分", "15");
        ScoreCandidate item4_6 = candidate("6", "四、综合评价 / 报价质量", "报价质量 15分", "15");
        ScoreCandidate itemTable3_1 = candidate(null, "应答人交易平台与履约交易平台对接方案", "对接方案 6分", "6");
        ScoreCandidate itemTable3_2 = candidate(null, "供货业绩", "供货业绩 7分", "7");

        // 故意深度打乱输入顺序：四(乱序) -> 二 -> 一 -> 三 -> 外部项
        List<ScoreCandidate> shuffled = List.of(
                item4_3, item4_1, item2, item4_6, item1, item4_2, item3, item4_5, item4_4,
                itemTable3_1, itemTable3_2
        );

        List<ScoreCandidate> result = policy.sort(shuffled);

        // 验证彻底恢复：一、诚信评价 -> 二、财务状况 -> 三、绿色数智评价 -> 四、综合评价 (1,2,3,4,5,6) -> 后续无章节项
        assertThat(result).extracting(ScoreCandidate::detail).containsExactly(
                "公共信用 0分",
                "财务状况 25分",
                "绿色规划 4分",
                "库存充足性 5分",
                "供货保障解决方案 5分",
                "紧急需求应急响应 15分",
                "应急保障能力 10分",
                "应答响应 15分",
                "报价质量 15分",
                "对接方案 6分",
                "供货业绩 7分"
        );
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
