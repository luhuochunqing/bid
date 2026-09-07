// Input: 合成表格行 / 关键词文本
// Output: ScoreTableSectionPolicy 纯函数行为验证
// Pos: Test/scoreparse/domain — PR !2589 后续：词表单一真相源锚定测试
// 维护声明: 改 ScoreTableSectionPolicy 任一词表必须同步更新本文件；
//      行文本侧与候选字段侧判定互为备份，单边加词必须双边验证
//      （StructuredTableCandidateExtractorTest + ScoreCandidatePostProcessorTest）。
package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableSectionPolicyTest {

    @Test
    void containsAny_nullSafe() {
        assertThat(ScoreTableSectionPolicy.containsAny(null, ScoreTableSectionPolicy.PRICE_DIM)).isFalse();
        assertThat(ScoreTableSectionPolicy.containsAny("", ScoreTableSectionPolicy.PRICE_DIM)).isFalse();
        assertThat(ScoreTableSectionPolicy.containsAny("折扣率评价", null)).isFalse();
        assertThat(ScoreTableSectionPolicy.containsAny("折扣率评价", ScoreTableSectionPolicy.PRICE_DIM)).isTrue();
    }

    @Test
    void containsAll_pairSemantics() {
        assertThat(ScoreTableSectionPolicy.containsAll("财务状况与信用评价良好",
                ScoreTableSectionPolicy.BIZ_SECTION_PAIR_1359)).isTrue();
        assertThat(ScoreTableSectionPolicy.containsAll("仅财务状况",
                ScoreTableSectionPolicy.BIZ_SECTION_PAIR_1359)).isFalse();
        assertThat(ScoreTableSectionPolicy.containsAll(null,
                ScoreTableSectionPolicy.BIZ_SECTION_PAIR_1359)).isFalse();
    }

    @Test
    void inferSectionDimension_nullAndEmpty_returnsNull() {
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(null)).isNull();
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(List.of())).isNull();
    }

    @Test
    void inferSectionDimension_bizOnly_returnsBiz() {
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("商务评分标准", "分值"),
                List.of("财务状况", "5")))).isEqualTo("商务");
    }

    @Test
    void inferSectionDimension_techOnly_returnsTech() {
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("技术评审标准", "分值"),
                List.of("对接方案", "10")))).isEqualTo("技术");
    }

    @Test
    void inferSectionDimension_priceOnly_returnsPrice() {
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("价格评审", "计算公式"),
                List.of("折扣率评价", "算术平均值法")))).isEqualTo("报价");
    }

    @Test
    void inferSectionDimension_mixedBizTech_returnsNullWithoutPollution() {
        // 混合单表（如 1358/1371）同时含商务与技术特征时必须返回 null，不污染维度
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("商务评分标准", "技术评分标准", "分值"),
                List.of("财务状况", "对接方案", "5")))).isNull();
    }

    @Test
    void inferSectionDimension_unknownVocab_returnsNullForMissReport() {
        // 未知措辞返回 null：多表场景由 extractWithSectionReport 上报 miss，单表场景属设计行为
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("序号", "评价要素", "分值"),
                List.of("1", "Alpha服务能力", "5")))).isNull();
    }

    @Test
    void inferSectionDimension_pair1359Fallback_returnsBiz() {
        // 1359 商务表无"商务评审"字样时的组合特征回退，泛化前保留
        assertThat(ScoreTableSectionPolicy.inferSectionDimension(rows(
                List.of("评审要素", "分值"),
                List.of("财务状况", "信用评价", "5")))).isEqualTo("商务");
    }

    @Test
    void isPriceFormula_formulaRow_returnsTrue() {
        assertThat(ScoreTableSectionPolicy.isPriceFormula(
                List.of("折扣率评价", "算术平均值法：评审价格得分=100"))).isTrue();
    }

    @Test
    void isPriceFormula_subjectWithoutHint_returnsFalse() {
        // 仅主体词无公式特征（如普通折扣率说明行）不得兜底，防止资格审查行膨胀
        assertThat(ScoreTableSectionPolicy.isPriceFormula(
                List.of("折扣率要求", "投标人承诺"))).isFalse();
    }

    @Test
    void isPriceFormula_nullOrEmpty_returnsFalse() {
        assertThat(ScoreTableSectionPolicy.isPriceFormula(null)).isFalse();
        assertThat(ScoreTableSectionPolicy.isPriceFormula(List.of())).isFalse();
    }

    private static List<List<SidecarTableExtraction.CellPayload>> rows(List<String>... rowTexts) {
        return java.util.Arrays.stream(rowTexts)
                .map(cells -> cells.stream()
                        .map(t -> new SidecarTableExtraction.CellPayload(t, 1, 1, false))
                        .toList())
                .toList();
    }
}
