// Input: ScoreTableRecallLane 表格召回门面
// Output: 启发式、自定义 SchemaMapper 及降级行为单测
// Pos: Test/scoreparse/domain
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableRecallLaneTest {

    private static final String TABLE_MARKDOWN = """
            | 序号 | 评分项目 | 评分标准 | 分值 |
            | --- | --- | --- | --- |
            | 1 | 商务部分 | 1. 财务状况良好得10分 | 10 |
            | 2 | 技术方案 | 2. 方案详细可行得20分 | 20 |
            """;

    @Test
    void recall_withNullOrBlank_returnsEmpty() {
        assertThat(ScoreTableRecallLane.recall(null)).isEmpty();
        assertThat(ScoreTableRecallLane.recall("")).isEmpty();
        assertThat(ScoreTableRecallLane.recall("   ")).isEmpty();
    }

    @Test
    void recall_withDefaultHeuristic_extractsCandidates() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(candidates.get(1).weight()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void recall_withCustomSchemaMapper_usesProvidedMapping() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid ->
                new TableColumnMapping(0, -1, 1, 2, 3, 1, false));

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).code()).isEqualTo("1");
        assertThat(candidates.get(0).dim()).isEqualTo("商务部分");
    }

    @Test
    void recall_whenCustomMapperReturnsNull_fallsBackToHeuristic() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid -> null);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void recall_whenCustomMapperThrows_fallsBackToHeuristic() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid -> {
            throw new RuntimeException("LLM error");
        });
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void recall_raggedScoreTables_allExtractedWithCorrectWeights() {
        String md = """
                | 项目 | 评审要素 | 评审内容 | 最低分 | 最高分 |
                | --- | --- | --- | --- | --- |
                | 一、诚信评价 | 公共信用信息报告（0分） | 不存在失信行为【不扣分】/存在失信行为【扣5分】 | -10 | 0 |
                | 负面事件影响评价（0分） | 重大负面事件扣5-10分，无上述情形不扣分 | -10 | 0 |
                | 二、财务状况（25分） | 财务综合评价（25） | 优：25分 | 15 | 25 |
                | 四、综合评价（65分） | 库存评价（10分） | 承诺库存充足得5分 | 0 | 5 |
                | 供货保障解决方案进行评价。（5分）  无条件保障供货得5分 | 0 | 5 |
                | 紧急需求应急响应（15分） | 承诺得15分 | 0 | 15 |
                | 报价质量  （15分） | 优15分 | 9 | 15 |

                | 评审项目 | 评审内容 | 最低分 | 最高分 |
                | --- | --- | --- | --- |
                | 应答人交易平台与履约交易平台对接方案（6分） | 满足要求得2分，最高得6分 | 2 | 6 |
                | 供货业绩（22分） | 类似供货服务（7分） 优秀得7分 | 3 | 7 |
                | 总体电商交易规模情况进行评价。（6分） 优秀得6分 | 1 | 6 |
                | 品类覆盖率（8分） | 覆盖率70%以上得8分 | 2 | 8 |
                | 配送能力（16分） | 发货时间（2分） | 1 | 2 |
                | 自有或租赁仓库数量排序情况进行评分  自有或租赁仓库数量（2分） 优秀得2分 | 1 | 2 |
                """;

        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(md);

        // 强断言：精准锁定 13 项提取结果与 96 分权重总和（消灭多提/少提/错位盲区）
        assertThat(candidates).hasSize(13);
        BigDecimal totalWeight = candidates.stream()
                .map(ScoreCandidate::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeight).isEqualByComparingTo(new BigDecimal("96"));

        // 验证诚信评价 0 分扣分项被提取
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("公共信用") && c.weight() != null && c.weight().compareTo(BigDecimal.ZERO) == 0);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("负面事件") && c.weight() != null && c.weight().compareTo(BigDecimal.ZERO) == 0);

        // 验证报价质量 15 分被提取
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("报价质量") && c.weight() != null && c.weight().compareTo(new BigDecimal("15")) == 0);

        // 验证品类覆盖率 8 分被提取
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("品类覆盖率") && c.weight() != null && c.weight().compareTo(new BigDecimal("8")) == 0);

        // 验证仓库数量 2 分被提取
        assertThat(candidates).anyMatch(c -> c.detail() != null && c.detail().contains("仓库数量") && c.weight() != null && c.weight().compareTo(new BigDecimal("2")) == 0);
    }

    @Test
    void recall_real1359Document_extractsBothTablesCleanly() throws Exception {
        try (var is = getClass().getResourceAsStream("/scoreparse-golden/1359_sidecar.md")) {
            assertThat(is).as("1359 sidecar fixture must exist in test resources").isNotNull();
            String md = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<ScoreCandidate> list = ScoreTableRecallLane.recall(md);

            // 强断言：真实 1359 完整两张表（商务表 13项 100分 + 技术表 25项 100分）提取 38 项且权重和精准为 200
            assertThat(list).hasSize(38);
            BigDecimal totalWeight = list.stream()
                    .map(ScoreCandidate::weight)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalWeight).isEqualByComparingTo(new BigDecimal("200"));

            // 验证真实 1359 标书提取：报价质量 15分 与 品类覆盖率 8分 必须成功召回
            assertThat(list).anyMatch(c -> ((c.dim() != null && c.dim().contains("报价质量")) || (c.detail() != null && c.detail().contains("报价质量")))
                    && c.weight() != null && c.weight().compareTo(new BigDecimal("15")) == 0);
            assertThat(list).anyMatch(c -> ((c.dim() != null && c.dim().contains("品类覆盖率")) || (c.detail() != null && c.detail().contains("品类覆盖率")))
                    && c.weight() != null && c.weight().compareTo(new BigDecimal("8")) == 0);
        }
    }
}
