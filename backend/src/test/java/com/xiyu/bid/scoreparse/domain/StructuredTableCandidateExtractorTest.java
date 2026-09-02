// Input: SidecarTableExtraction（结构化表格数据）
// Output: List<ScoreCandidate> 解析行为验证（Phase 1 结构化表格提取通道）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.application.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.application.SidecarTableExtraction.CellPayload;
import com.xiyu.bid.scoreparse.application.SidecarTableExtraction.TablePayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredTableCandidateExtractorTest {

    @Test
    void extract_nullExtraction_returnsEmpty() {
        assertThat(StructuredTableCandidateExtractor.extract(null)).isEmpty();
    }

    @Test
    void extract_emptyTables_returnsEmpty() {
        assertThat(StructuredTableCandidateExtractor.extract(SidecarTableExtraction.empty())).isEmpty();
    }

    @Test
    void extract_noScoreTablesAndTooFewRows_returnsEmpty() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("财务状况"), cell("5"))
        ), false);
        assertThat(StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()))).isEmpty();
    }

    @Test
    void extract_bracketWeightExtracted() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分标准"), cell("分值")),
                List.of(cell("1"), cell("财务状况（10分）"), cell("提供财务报表"), cell("10")),
                List.of(cell("2"), cell("技术方案（15分）"), cell("方案完整可行"), cell("15"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.get(0).dim()).isEqualTo("财务状况");
        assertThat(result.get(1).weight()).isEqualByComparingTo(new BigDecimal("15"));
        assertThat(result.get(1).dim()).isEqualTo("技术方案");
    }

    @Test
    void extract_gradeScoreFallback() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分标准")),
                List.of(cell("1"), cell("价格方案"),
                        cell("优秀得4分，良好得2-3分，一般得1分"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weight()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(result.get(0).detail()).contains("优秀得4分");
    }

    @Test
    void extract_headerRowSkipped() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("评分因素"), cell("评审内容"), cell("分值")),
                List.of(cell("1"), cell("公司业绩"), cell("5")),
                List.of(cell("评审内容"), cell("评分标准"), cell("分值")),
                List.of(cell("2"), cell("技术方案"), cell("10"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).detail()).contains("公司业绩");
        assertThat(result.get(1).detail()).contains("技术方案");
    }

    @Test
    void extract_mergedContinueCellsSkipped() {
        CellPayload mergeStart = cell("技术方案");
        CellPayload mergeContinue = new CellPayload("", 1, 1, true);

        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分标准"), cell("分值")),
                List.of(cell("1"), mergeStart, cell("方案完整得5分"), cell("5")),
                List.of(cell("2"), mergeContinue, cell("团队资质得3分"), cell("3"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(1).detail()).contains("团队资质");
    }

    @Test
    void extract_codeExtractedFromFirstColumn() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分标准"), cell("分值")),
                List.of(cell("2.2.1"), cell("自营商城"), cell("横向比较"), cell("6")),
                List.of(cell("A1"), cell("公司业绩"), cell("近三年业绩"), cell("5"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("2.2.1");
        assertThat(result.get(1).code()).isEqualTo("A1");
    }

    @Test
    void extract_fiveColumnTable_detailConcatenatesCol4AndCol5() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分项名称"), cell("评分要求"), cell("评分细则")),
                List.of(cell("1"), cell("技术方案"), cell("方案完整性（8分）"),
                        cell("方案完整可行"), cell("每缺一项扣2分"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).detail()).contains("方案完整可行").contains("每缺一项扣2分");
        assertThat(result.get(0).weight()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void extract_semanticPatternIsStructuredTable() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("财务状况"), cell("5"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).allSatisfy(c ->
                assertThat(c.semanticPattern()).isEqualTo("STRUCTURED_TABLE"));
    }

    @Test
    void extract_fallbackToUnmarkedTablesWithEnoughRows() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("项目"), cell("说明")),
                List.of(cell("1"), cell("财务状况（10分）"), cell("提供报表")),
                List.of(cell("2"), cell("技术方案（8分）"), cell("方案描述"))
        ), false);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.get(1).weight()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void extract_multipleScoreTables_allExtracted() {
        TablePayload table1 = new TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("商务（5分）"), cell("5"))
        ), true);
        TablePayload table2 = new TablePayload(1, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("技术（10分）"), cell("10"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table1, table2), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).dim()).isEqualTo("商务");
        assertThat(result.get(1).dim()).isEqualTo("技术");
    }

    @Test
    void extract_shanJiaoMaterialScenario() {
        TablePayload table = new TablePayload(0, List.of(
                List.of(cell("编号"), cell("评审因素"), cell("评审标准"), cell("分值")),
                List.of(cell("1"), cell("投标报价（30分）"), cell("最低价得满分"), cell("30")),
                List.of(cell("2"), cell("公司业绩（10分）"), cell("近三年同类项目业绩"), cell("10")),
                List.of(cell("3"), cell("技术方案（25分）"), cell("方案完整合理"), cell("25")),
                List.of(cell("4"), cell("服务承诺（5分）"), cell("售后服务方案"), cell("5"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(4);
        assertThat(result.get(0)).satisfies(c -> {
            assertThat(c.code()).isEqualTo("1");
            assertThat(c.dim()).isEqualTo("投标报价");
            assertThat(c.weight()).isEqualByComparingTo(new BigDecimal("30"));
        });
        assertThat(result.get(2)).satisfies(c -> {
            assertThat(c.code()).isEqualTo("3");
            assertThat(c.dim()).isEqualTo("技术方案");
            assertThat(c.weight()).isEqualByComparingTo(new BigDecimal("25"));
        });
    }

    private static CellPayload cell(String text) {
        return new CellPayload(text, 1, 1, false);
    }
}
