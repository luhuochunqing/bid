// Input: SidecarTableExtraction（结构化表格数据）
// Output: List<ScoreCandidate> 解析行为验证（Phase 1 结构化表格提取通道）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("财务状况"), cell("5"))
        ), false);
        assertThat(StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()))).isEmpty();
    }

    @Test
    void extract_bracketWeightExtracted() {
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.CellPayload mergeStart = cell("技术方案");
        SidecarTableExtraction.CellPayload mergeContinue = new SidecarTableExtraction.CellPayload("", 1, 1, true);

        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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
        SidecarTableExtraction.TablePayload table1 = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("商务（5分）"), cell("5"))
        ), true);
        SidecarTableExtraction.TablePayload table2 = new SidecarTableExtraction.TablePayload(1, List.of(
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
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
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

    @Test
    void extract_parentDimFromMergedContinueText() {
        // Simulates RCA scenario: column 1 "商务评分标准（56分）" merged across rows
        SidecarTableExtraction.CellPayload mergeStart =
                new SidecarTableExtraction.CellPayload("商务评分标准（56分）", 3, 1, false);
        SidecarTableExtraction.CellPayload mergeContinue1 =
                new SidecarTableExtraction.CellPayload("商务评分标准（56分）", 1, 1, true);
        SidecarTableExtraction.CellPayload mergeContinue2 =
                new SidecarTableExtraction.CellPayload("商务评分标准（56分）", 1, 1, true);

        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("编号"), cell("评审因素"), cell("评审标准"), cell("分值")),
                List.of(cell("3.3(1)"), mergeStart, cell("财务状况（10分）"), cell("提供审计报告")),
                List.of(cell("3.3(1)"), mergeContinue1, cell("运营能力（20分）"), cell("近三年业绩")),
                List.of(cell("3.3(1)"), mergeContinue2, cell("服务案例（20分）"), cell("同类项目案例"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(3);
        // First row: mergeStart is not a continue, so parentDim comes from rowspan tracking
        assertThat(result.get(0).parentDim()).isEqualTo("商务评分标准");
        assertThat(result.get(0).dimSource()).isEqualTo("CELL");
        // Subsequent rows: merged-continue text provides parentDim
        assertThat(result.get(1).parentDim()).isEqualTo("商务评分标准");
        assertThat(result.get(1).dimSource()).isEqualTo("CELL");
        assertThat(result.get(2).parentDim()).isEqualTo("商务评分标准");
        assertThat(result.get(2).dim()).isEqualTo("服务案例");
        assertThat(result.get(2).weight()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void extract_parentDimFromRowspanTrackingWhenMergedContinueEmpty() {
        // Fallback: merged-continue cells have empty text, rowspan tracking provides parent
        SidecarTableExtraction.CellPayload mergeStart =
                new SidecarTableExtraction.CellPayload("技术部分（30分）", 3, 1, false);
        SidecarTableExtraction.CellPayload emptyContinue =
                new SidecarTableExtraction.CellPayload("", 1, 1, true);

        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("评分标准"), cell("分值")),
                List.of(cell("1"), mergeStart, cell("方案完整性（8分）"), cell("方案描述")),
                List.of(cell("2"), emptyContinue, cell("团队资质（7分）"), cell("资质证明")),
                List.of(cell("3"), emptyContinue, cell("售后服务（5分）"), cell("服务承诺"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).parentDim()).isEqualTo("技术部分");
        assertThat(result.get(1).parentDim()).isEqualTo("技术部分");
        assertThat(result.get(2).parentDim()).isEqualTo("技术部分");
        assertThat(result).allSatisfy(c -> assertThat(c.dimSource()).isEqualTo("CELL"));
    }

    @Test
    void extract_noParentDimWhenNoMerge() {
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("序号"), cell("评分因素"), cell("分值")),
                List.of(cell("1"), cell("财务状况（10分）"), cell("10")),
                List.of(cell("2"), cell("技术方案（15分）"), cell("15"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(c -> {
            assertThat(c.parentDim()).isNull();
            assertThat(c.dimSource()).isEqualTo("UNKNOWN");
        });
    }

    /**
     * Phase 1 项目 1358 金标验证：合并单元格（rowspan）结构化表格端到端提取。
     * <p>模拟 1358 评审办法表：商务评分标准(rowspan=4) + 价格评分(单行) + 技术评分标准(rowspan=6)，
     * 验证 11 项评分细则全部提取、parentDim 正确填充、STRUCTURED_TABLE 标记、总分闭环 100。
     * <p>列布局与 {@link #extract_parentDimFromMergedContinueText} 对齐：
     * [编号, 评分类别(merged), 评分因素（N分）, 评分标准]，权重由括号解析。
     */
    @Test
    void extract_project1358GoldenMergedCellStructure() {
        SidecarTableExtraction.CellPayload bizMergeStart =
                new SidecarTableExtraction.CellPayload("商务评分标准", 4, 1, false);
        SidecarTableExtraction.CellPayload bizContinue =
                new SidecarTableExtraction.CellPayload("商务评分标准", 1, 1, true);
        SidecarTableExtraction.CellPayload techMergeStart =
                new SidecarTableExtraction.CellPayload("技术评分标准", 6, 1, false);
        SidecarTableExtraction.CellPayload techContinue =
                new SidecarTableExtraction.CellPayload("技术评分标准", 1, 1, true);

        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("编号"), cell("评分类别"), cell("评分因素"), cell("评分标准")),
                // 商务评分标准 rowspan=4
                List.of(cell("2.2.4(1)"), bizMergeStart, cell("响应文件的完整性（4分）"),
                        cell("响应文件中若存在漏项每处扣10分，本项满分100分")),
                List.of(cell("2.2.4(1)"), bizContinue, cell("信用评价（5分）"),
                        cell("无不良记录得100分，有处罚记录无罚款得90分")),
                List.of(cell("2.2.4(1)"), bizContinue, cell("供应商业绩（5分）"),
                        cell("满足资格条件业绩得基础分60分，每增加一个加20分")),
                List.of(cell("2.2.4(1)"), bizContinue, cell("财务状况（6分）"),
                        cell("速动比率≥1得50分，营业收现比率≥1得50分")),
                // 价格评分 单行
                List.of(cell("2.2.4(2)"), cell("价格评分"), cell("价格折扣得分（50分）"),
                        cell("最低报价得满分100分，最高报价得60分，线性插入法")),
                // 技术评分标准 rowspan=6
                List.of(cell("2.2.4(3)"), techMergeStart, cell("商城系统对接能力（5分）"),
                        cell("按A、B、C、D分档打分")),
                List.of(cell("2.2.4(3)"), techContinue, cell("运营服务能力（5分）"),
                        cell("按A、B、C、D分档打分")),
                List.of(cell("2.2.4(3)"), techContinue, cell("商品管理能力（5分）"),
                        cell("按A、B、C、D分档打分")),
                List.of(cell("2.2.4(3)"), techContinue, cell("供应商管理能力（5分）"),
                        cell("按A、B、C、D分档打分")),
                List.of(cell("2.2.4(3)"), techContinue, cell("物流配送及仓储能力（5分）"),
                        cell("按A、B、C、D分档打分")),
                List.of(cell("2.2.4(3)"), techContinue, cell("质量监管能力（5分）"),
                        cell("按A、B、C、D分档打分"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(11);
        assertThat(result).allSatisfy(c -> assertThat(c.semanticPattern()).isEqualTo("STRUCTURED_TABLE"));

        // parentDim 验证：商务 4 项 + 技术 6 项有父级，价格 1 项无合并父级
        assertThat(result.subList(0, 4)).allSatisfy(c -> assertThat(c.parentDim()).isEqualTo("商务评分标准"));
        assertThat(result.get(4).parentDim()).isNull();
        assertThat(result.subList(5, 11)).allSatisfy(c -> assertThat(c.parentDim()).isEqualTo("技术评分标准"));

        // 关键评分项 dim + weight 验证
        assertThat(result).anyMatch(c -> "信用评价".equals(c.dim()) && c.weight().compareTo(new BigDecimal("5")) == 0);
        assertThat(result).anyMatch(c -> "财务状况".equals(c.dim()) && c.weight().compareTo(new BigDecimal("6")) == 0);
        assertThat(result).anyMatch(c -> "价格折扣得分".equals(c.dim()) && c.weight().compareTo(new BigDecimal("50")) == 0);
        assertThat(result).anyMatch(c -> "运营服务能力".equals(c.dim()) && c.weight().compareTo(new BigDecimal("5")) == 0);

        // 总分闭环 100
        BigDecimal total = result.stream()
                .map(ScoreCandidate::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100");
    }

    @Test
    void extract_real1359TablesJson_extractsAllCandidatesCleanly() throws Exception {
        SidecarTableExtraction extraction = loadExtractionFromResource("/scoreparse-golden/1359_tables_v1.json");
        List<ScoreCandidate> candidates = StructuredTableCandidateExtractor.extract(extraction);

        // 验证全量 39 项保全（13 商务 + 25 技术 + 1 报价），零行丢弃
        assertThat(candidates).hasSize(39);

        // 验证商务 13 项均继承商务父维度
        assertThat(candidates.subList(0, 13)).allSatisfy(c ->
                assertThat(c.parentDim()).isNotNull().startsWith("商务"));

        // 验证技术 25 项均继承技术父维度
        assertThat(candidates.subList(13, 38)).allSatisfy(c ->
                assertThat(c.parentDim()).isNotNull().startsWith("技术"));

        // 验证报价 1 项继承报价父维度，完整保留公式，分值兜底为 0
        ScoreCandidate priceItem = candidates.get(38);
        assertThat(priceItem.dim()).isEqualTo("折扣率评价");
        assertThat(priceItem.parentDim()).isEqualTo("报价");
        assertThat(priceItem.detail()).contains("算术平均值法：评审价格得分=100");
        assertThat(priceItem.weight()).isEqualByComparingTo(BigDecimal.ZERO);

        // 验证无合计汇总行泄漏
        assertThat(candidates).noneMatch(c -> "合计".equals(c.dim())
                || (c.detail() != null && c.detail().startsWith("合计")));

        // 验证 parentDim 绝无分值列/最高分等污染
        assertThat(candidates).noneMatch(c -> c.parentDim() != null && (c.parentDim().contains("最高分") || c.parentDim().contains("最低分")));
        // 验证真实 1359 提取出供货业绩
        assertThat(candidates).anyMatch(c -> (c.dim() != null && c.dim().contains("供货业绩")) || (c.detail() != null && c.detail().contains("供货业绩")));
    }

    @Test
    void extract_real1371TablesJson_extractsClean100Points() throws Exception {
        SidecarTableExtraction extraction = loadExtractionFromResource("/scoreparse-golden/1371_tables_v1.json");
        List<ScoreCandidate> candidates = StructuredTableCandidateExtractor.extract(extraction);

        // 验证提取出 16 项
        assertThat(candidates).hasSize(16);

        // 验证无 3.3(1) 或 3.3(2) 编号泄漏到 parentDim
        assertThat(candidates).noneMatch(c -> c.parentDim() != null && c.parentDim().contains("3.3"));

        // 验证技术标核心项（品牌及货源渠道管控及佐证材料 20分）存在
        assertThat(candidates).anyMatch(c -> "品牌及货源渠道管控及佐证材料".equals(c.dim())
                && c.weight().compareTo(new BigDecimal("20")) == 0
                && "技术评分标准".equals(c.parentDim()));

        // 验证资源实力 3 个子细则全部提取，各 2 分，父级为技术评分标准
        List<ScoreCandidate> resourceItems = candidates.stream()
                .filter(c -> "资源实力".equals(c.dim()))
                .toList();
        assertThat(resourceItems).hasSize(3);
        assertThat(resourceItems).allSatisfy(c -> {
            assertThat(c.parentDim()).isEqualTo("技术评分标准");
            assertThat(c.weight()).isEqualByComparingTo(new BigDecimal("2"));
        });

        // 验证商务标 56 分闭环
        BigDecimal bizSum = candidates.stream()
                .filter(c -> "商务评分标准".equals(c.parentDim()))
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(bizSum).isEqualByComparingTo(new BigDecimal("56"));

        // 验证技术标 44 分闭环（消除 6+2+2=10 的 4分膨胀）
        BigDecimal techSum = candidates.stream()
                .filter(c -> "技术评分标准".equals(c.parentDim()))
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(techSum).isEqualByComparingTo(new BigDecimal("44"));

        // 验证全表总分严格 100.0 分
        BigDecimal total = candidates.stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(new BigDecimal("100"));
    }

    private SidecarTableExtraction loadExtractionFromResource(String resourcePath) throws Exception {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            assertThat(is).as(resourcePath + " fixture must exist in test resources").isNotNull();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(is);
            com.fasterxml.jackson.databind.JsonNode tablesNode = root.get("tables");
            List<SidecarTableExtraction.TablePayload> tables = new java.util.ArrayList<>();
            if (tablesNode != null && tablesNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode t : tablesNode) {
                    int tableIndex = t.get("tableIndex").asInt();
                    boolean isScore = t.has("isScoreTable") && t.get("isScoreTable").asBoolean();
                    List<List<SidecarTableExtraction.CellPayload>> rows = new java.util.ArrayList<>();
                    if (t.has("rows") && t.get("rows").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode r : t.get("rows")) {
                            List<SidecarTableExtraction.CellPayload> row = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode c : r) {
                                String text = c.has("text") ? c.get("text").asText() : "";
                                int rowspan = c.has("rowspan") ? c.get("rowspan").asInt() : 1;
                                int colspan = c.has("colspan") ? c.get("colspan").asInt() : 1;
                                boolean isMergedContinue = c.has("isMergedContinue") && c.get("isMergedContinue").asBoolean();
                                row.add(new SidecarTableExtraction.CellPayload(text, rowspan, colspan, isMergedContinue));
                            }
                            rows.add(row);
                        }
                    }
                    tables.add(new SidecarTableExtraction.TablePayload(tableIndex, rows, isScore));
                }
            }
            return new SidecarTableExtraction(tables, List.of());
        }
    }

    @Test
    void extract_raggedRowsWithRowspan_decrementsCorrectlyWithoutLeaking() {
        // 第0行: 表头
        // 第1行: 大类(rowspan=2), 项目A, 细则A(10分)
        // 第2行(参差行，仅2格): 细则A2(5分) - 仅2格，比上一行短
        // 第3行: 独立项, 独立细则(10分) - rowspan已结束，不应继承大类
        List<List<SidecarTableExtraction.CellPayload>> rows = List.of(
                List.of(new SidecarTableExtraction.CellPayload("大类", 1, 1, false),
                        new SidecarTableExtraction.CellPayload("项目", 1, 1, false),
                        new SidecarTableExtraction.CellPayload("细则", 1, 1, false)),
                List.of(new SidecarTableExtraction.CellPayload("商务大类", 2, 1, false),
                        new SidecarTableExtraction.CellPayload("项目A", 1, 1, false),
                        new SidecarTableExtraction.CellPayload("细则A（10分）", 1, 1, false)),
                List.of(new SidecarTableExtraction.CellPayload("商务大类", 1, 1, true),
                        new SidecarTableExtraction.CellPayload("细则A2（5分）", 1, 1, false)),
                List.of(new SidecarTableExtraction.CellPayload("独立项", 1, 1, false),
                        new SidecarTableExtraction.CellPayload("独立细则（10分）", 1, 1, false))
        );
        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(1, rows, true);
        List<ScoreCandidate> candidates = StructuredTableCandidateExtractor.extract(new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(candidates).hasSize(3);
        // 第1行有 parentDim 商务大类
        assertThat(candidates.get(0).parentDim()).isEqualTo("商务大类");
        // 第2行有 parentDim 商务大类 (rowspan=2延续)
        assertThat(candidates.get(1).parentDim()).isEqualTo("商务大类");
        // 第3行是独立项，绝不能泄漏前一行的大类
        assertThat(candidates.get(2).parentDim()).isNull();
    }

    @Test
    void extract_rowspanParentWithChildGradeScores_deduplicatesParentWeight() {
        SidecarTableExtraction.CellPayload techMergeStart =
                new SidecarTableExtraction.CellPayload("技术评分标准", 4, 1, false);
        SidecarTableExtraction.CellPayload techContinue1 =
                new SidecarTableExtraction.CellPayload("技术评分标准", 1, 1, true);
        SidecarTableExtraction.CellPayload techContinue2 =
                new SidecarTableExtraction.CellPayload("技术评分标准", 1, 1, true);
        SidecarTableExtraction.CellPayload techContinue3 =
                new SidecarTableExtraction.CellPayload("技术评分标准", 1, 1, true);

        SidecarTableExtraction.CellPayload resourceMergeStart =
                new SidecarTableExtraction.CellPayload("资源实力（6分）", 3, 1, false);
        SidecarTableExtraction.CellPayload resourceContinue1 =
                new SidecarTableExtraction.CellPayload("资源实力（6分）", 1, 1, true);
        SidecarTableExtraction.CellPayload resourceContinue2 =
                new SidecarTableExtraction.CellPayload("资源实力（6分）", 1, 1, true);

        SidecarTableExtraction.TablePayload table = new SidecarTableExtraction.TablePayload(0, List.of(
                List.of(cell("编号"), cell("评分类别"), cell("评分因素"), cell("评审内容"), cell("评分标准")),
                List.of(cell("3.3(2)"), techMergeStart, resourceMergeStart,
                        cell("高新技术企业证书要求"), cell("具有国家高新技术企业证书，得2分，没有不得分。")),
                List.of(cell("3.3(2)"), techContinue1, resourceContinue1,
                        cell("行业标准制定要求"), cell("企业参与制定行业及以上标准1项，得2分；企标得1分。")),
                List.of(cell("3.3(2)"), techContinue2, resourceContinue2,
                        cell("科技奖励要求"), cell("省部级及以上科技奖励，得2分；其他得1分。")),
                List.of(cell("3.3(2)"), techContinue3, cell("服务能力（2分）"),
                        cell("项目团队配置要求"), cell("提供社保和工作证明得2分。"))
        ), true);

        List<ScoreCandidate> result = StructuredTableCandidateExtractor.extract(
                new SidecarTableExtraction(List.of(table), List.of()));

        assertThat(result).hasSize(4);
        // 验证 3 个资源实力子项各得 2 分，不膨胀为 6 + 2 + 2 = 10 分
        List<ScoreCandidate> resourceItems = result.subList(0, 3);
        assertThat(resourceItems).allSatisfy(c -> {
            assertThat(c.dim()).isEqualTo("资源实力");
            assertThat(c.parentDim()).isEqualTo("技术评分标准");
            assertThat(c.weight()).isEqualByComparingTo(new BigDecimal("2"));
        });

        // 验证服务能力独立项
        assertThat(result.get(3).dim()).isEqualTo("服务能力");
        assertThat(result.get(3).weight()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(result.get(3).parentDim()).isEqualTo("技术评分标准");

        // 总分闭环：2 + 2 + 2 + 2 = 8
        BigDecimal total = result.stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("8");
    }

    private static SidecarTableExtraction.CellPayload cell(String text) {
        return new SidecarTableExtraction.CellPayload(text, 1, 1, false);
    }
}
