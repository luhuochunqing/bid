package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealProjectScoreParsingTest {

    @Disabled("依赖本地绝对路径 fixture，在 CI 上无此文件，需手动在本地运行")
    @Test
    void test1347ScoringChapterLocation() throws Exception {
        Path path = Paths.get("/Users/user/.gemini/antigravity-ide/brain/f248a9e2-27e6-4113-b216-d1c053a01264/scratch/1347.md");
        String md = Files.readString(path);
        List<MarkdownScoreSectionLocator.ScoreSection> sections = MarkdownScoreSectionLocator.locate(md);
        
        assertThat(sections).isNotEmpty();
        // Section 0 必须捕获综合评分法全部 11 项细则正文
        String s0 = sections.get(0).content();
        assertThat(s0).contains("综合评分法--适用于标包一");
        assertThat(s0).contains("响应文件的完整性");
        assertThat(s0).contains("信用评价");
        assertThat(s0).contains("供应商业绩");
        assertThat(s0).contains("财务状况");
        assertThat(s0).contains("价格折扣得分");
        assertThat(s0).contains("商城系统对接能力");
        assertThat(s0).contains("运营服务能力");
        assertThat(s0).contains("商品管理能力");
        assertThat(s0).contains("供应商管理能力");
        assertThat(s0).contains("物流配送及仓储能力");
        assertThat(s0).contains("质量监管能力");
    }

    @Disabled("依赖本地绝对路径 fixture，在 CI 上无此文件，需手动在本地运行")
    @Test
    void test1362DetailedScoringTableLocation() throws Exception {
        Path path = Paths.get("/Users/user/.gemini/antigravity-ide/brain/f248a9e2-27e6-4113-b216-d1c053a01264/scratch/1362.md");
        String md = Files.readString(path);
        List<MarkdownScoreSectionLocator.ScoreSection> sections = MarkdownScoreSectionLocator.locate(md);
        
        assertThat(sections).isNotEmpty();
        String s0 = sections.get(0).content();
        assertThat(s0).contains("详细评审表");
        assertThat(s0).contains("公司财务状况");
        assertThat(s0).contains("注册资本金");
        assertThat(s0).contains("营业收入");
        assertThat(s0).contains("现金流");
        assertThat(s0).contains("资产负债率");
        assertThat(s0).contains("体系认证");
        assertThat(s0).contains("商品品目和商品品种");
        assertThat(s0).contains("仓储、物流配送及服务");
    }

    @Test
    void testProject36ZbjDeclarationAndContractTableExclusion() {
        String md = """
                # 第三章 评审办法
                2. 评审标准
                2.1 初步评审标准
                2.2 详细评审标准
                2.2.1 分值构成
                详见ZBJ格式采购文件“评审项目”页签。
                2.2.2 评审基准价计算
                评审基准价计算方法：详见ZBJ格式采购文件“评审项目”页签。
                2.2.3 评分标准
                （1）客观分部分评分标准：详见ZBJ格式采购文件“评审项目”页签；
                （2）主观分部分评分标准：详见ZBJ格式采购文件“评审项目”页签；
                （3）价格部分评分标准：详见ZBJ格式采购文件“评审项目”页签。

                # 合同附件
                | 序号 | 考核项目 | 考核内容 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 周期保证 | 产品或服务满足项目周期要求 | 25 |
                | 2 | 质量保证 | 产品或服务满足项目技术要求 | 25 |
                | 3 | 价格水平 | 价格合理 | 15 |
                | 4 | 问题解决 | 快速解决问题 | 5 |
                | 5 | 安全环保 | 符合安全环保要求 | 10 |
                """;

        assertThat(MarkdownScoreSectionLocator.hasExternalSoftwareScoreDeclaration(md)).isTrue();

        List<ScoreCandidate> tableCandidates = ScoreTableRecallLane.recall(md);
        assertThat(tableCandidates).isEmpty();
    }

    @Test
    void testProject21ScoreParsing() throws Exception {
        // 金标防回退：classpath 加载（不依赖工作目录），夹具缺失必须硬失败——
        // 静默 return 会让金标在路径失效时无声失效，防线归零（xcode-review：silent-return 反模式）
        String md;
        try (java.io.InputStream in = getClass().getResourceAsStream("/scoreparse-golden/project21-mro.md")) {
            org.assertj.core.api.Assertions.assertThat(in)
                    .as("金标夹具 scoreparse-golden/project21-mro.md 必须存在于 test resources")
                    .isNotNull();
            md = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(md);

        assertThat(candidates).isNotEmpty();

        // 精确数量：11 项细则 + 报价细拆 2 子项（京东 28 / 自有商城 12）= 13
        assertThat(candidates).hasSize(13);

        // 验证关键评分项被成功识别并具备完整子细则（100% 逐字保留 1/2/3 全部子要点）
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("供应商业绩") && c.weight().intValue() == 7);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("财务状况") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("服务网点及仓储") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("电子商城运营时间") && c.weight().intValue() == 1);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("授信额度") && c.weight().intValue() == 1);
        
        ScoreCandidate servicePlan = candidates.stream()
                .filter(c -> c.dim() != null && c.dim().contains("综合服务方案"))
                .findFirst()
                .orElseThrow();
        assertThat(servicePlan.weight().intValue()).isEqualTo(10);
        assertThat(servicePlan.detail())
                .contains("1.综合考察供应商售前服务能力")
                .contains("2.综合考察供应商售中服务能力")
                .contains("3.综合考察供应商售后服务能力")
                .contains("出库配送")
                .contains("退换货响应、三包服务")
                .contains("售后问题处理周期");

        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("产品质量及价格") && c.weight().intValue() == 10);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("物流仓储方案") && c.weight().intValue() == 8);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("服务团队方案") && c.weight().intValue() == 10);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("平台系统对接方案") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("增值服务方案") && c.weight().intValue() == 4);

        // 验证 Guard 层能够保护完整细则不被 LLM 截断输出覆盖
        ScoreCandidate truncatedMerged = new ScoreCandidate(
                servicePlan.code(), servicePlan.dim(),
                "1.综合考察供应商售前服务能力，须提供售前服务方案，包括但不限于商品咨询解答...出库配",
                servicePlan.weight(), "SUBJECTIVE", null, "原文", "P30", "GLOBAL_MERGE");
        ScoreMergeResultGuard.Outcome guardedOutcome = ScoreMergeResultGuard.guard(
                candidates, List.of(truncatedMerged), md);
        assertThat(guardedOutcome.accepted()).isNotEmpty();
        ScoreCandidate guardedServicePlan = guardedOutcome.accepted().stream()
                .filter(c -> c.dim() != null && c.dim().contains("综合服务方案"))
                .findFirst()
                .orElseThrow();
        assertThat(guardedServicePlan.detail()).contains("3.综合考察供应商售后服务能力");

        // 复合价格分细拆：加法型（40 = 28 + 12）才允许拆分
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("京东工业品") && c.weight().intValue() == 28);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("自有商城") && c.weight().intValue() == 12);

        // 验证总分闭环为 100 分
        java.math.BigDecimal totalWeight = candidates.stream()
                .map(ScoreCandidate::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(totalWeight).isEqualByComparingTo("100.0");

        // 验证经过 ScoreItemOrderPolicy 排序后大类结构严谨有序
        ScoreItemOrderPolicy orderPolicy = new ScoreItemOrderPolicy();
        List<ScoreCandidate> sorted = orderPolicy.sort(candidates);
        assertThat(sorted).hasSize(13);
        assertThat(sorted.get(0).dim()).contains("供应商业绩");
        assertThat(sorted.get(sorted.size() - 2).dim()).contains("京东工业品");
        assertThat(sorted.get(sorted.size() - 1).dim()).contains("自有商城");
    }

    @Test
    void testProject21FullPipelineOrderingWithSubItemSplits() {
        ScoreItemOrderPolicy orderPolicy = new ScoreItemOrderPolicy();
        List<ScoreCandidate> splitCandidates = Arrays.asList(
                new ScoreCandidate("3.2.3", "商务评分 / 供应商业绩", "供应商业绩 7分", new BigDecimal("7"), null, null, null, null, null),
                new ScoreCandidate("1", "商务评分 / 财务状况 / 现金流量净额", "现金流量净额 1.5分", new BigDecimal("1.5"), null, null, null, null, null),
                new ScoreCandidate("2", "商务评分 / 财务状况 / 净利润", "净利润 1.5分", new BigDecimal("1.5"), null, null, null, null, null),
                new ScoreCandidate("1", "商务评分 / 服务网点及仓储 / 服务网点", "服务网点 1.5分", new BigDecimal("1.5"), null, null, null, null, null),
                new ScoreCandidate("2", "商务评分 / 服务网点及仓储 / 仓储地点", "仓储地点 1.5分", new BigDecimal("1.5"), null, null, null, null, null),
                new ScoreCandidate(null, "商务评分 / 电子商城运营时间", "电子商城 1分", new BigDecimal("1"), null, null, null, null, null),
                new ScoreCandidate(null, "商务评分 / 授信额度", "授信额度 1分", new BigDecimal("1"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 综合服务方案", "综合服务方案 10分", new BigDecimal("10"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 产品质量及价格", "产品质量及价格 10分", new BigDecimal("10"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 物流仓储方案", "物流仓储方案 8分", new BigDecimal("8"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 服务团队方案", "服务团队方案 10分", new BigDecimal("10"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 平台系统对接方案", "平台系统对接方案 3分", new BigDecimal("3"), null, null, null, null, null),
                new ScoreCandidate(null, "技术评分 / 增值服务方案", "增值服务方案 4分", new BigDecimal("4"), null, null, null, null, null),
                new ScoreCandidate("1", "报价评分 / 上架折扣率（京东工业品）价格分", "京东折扣 28分", new BigDecimal("28"), null, null, null, null, null),
                new ScoreCandidate("2", "报价评分 / 上架折扣率（自有商城）价格分", "自有商城折扣 12分", new BigDecimal("12"), null, null, null, null, null)
        );

        List<ScoreCandidate> result = orderPolicy.sort(splitCandidates);

        // 验证大类顺序：商务(7项) -> 技术(6项) -> 报价(2项)
        assertThat(result).hasSize(15);
        assertThat(result.subList(0, 7)).allMatch(c -> c.dim().contains("商务"));
        assertThat(result.subList(7, 13)).allMatch(c -> c.dim().contains("技术"));
        assertThat(result.subList(13, 15)).allMatch(c -> c.dim().contains("报价"));

        // 验证细分顺序不会因局部编号 1、2 被打乱
        assertThat(result.get(0).dim()).contains("供应商业绩");
        assertThat(result.get(1).dim()).contains("现金流量净额");
        assertThat(result.get(2).dim()).contains("净利润");
        assertThat(result.get(3).dim()).contains("服务网点");
        assertThat(result.get(4).dim()).contains("仓储地点");
        assertThat(result.get(13).dim()).contains("京东工业品");
        assertThat(result.get(14).dim()).contains("自有商城");

        // 验证总分严格等于 100.0
        BigDecimal sum = result.stream().map(ScoreCandidate::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.0");
    }
}
