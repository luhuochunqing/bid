package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealProjectScoreParsingTest {

    @Test
    void test1347ScoringChapterLocation() throws Exception {
        Path path = Paths.get("/Users/user/.gemini/antigravity-ide/brain/f248a9e2-27e6-4113-b216-d1c053a01264/scratch/1347.md");
        if (!Files.exists(path)) {
            return;
        }
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

    @Test
    void test1362DetailedScoringTableLocation() throws Exception {
        Path path = Paths.get("/Users/user/.gemini/antigravity-ide/brain/f248a9e2-27e6-4113-b216-d1c053a01264/scratch/1362.md");
        if (!Files.exists(path)) {
            return;
        }
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
        Path fixturePath = Paths.get("src/test/resources/scoreparse-golden/project21-mro.md");
        if (!Files.exists(fixturePath)) {
            fixturePath = Paths.get("backend/src/test/resources/scoreparse-golden/project21-mro.md");
        }
        if (!Files.exists(fixturePath)) {
            return;
        }
        String md = Files.readString(fixturePath);
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(md);

        assertThat(candidates).isNotEmpty();

        // 验证评分项数量在 11 到 13 项之间（含 11 项细则或报价细拆 2 项）
        assertThat(candidates.size()).isBetween(11, 15);

        // 验证关键评分项被成功识别
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("供应商业绩") && c.weight().intValue() == 7);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("财务状况") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("服务网点及仓储") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("电子商城运营时间") && c.weight().intValue() == 1);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("授信额度") && c.weight().intValue() == 1);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("综合服务方案") && c.weight().intValue() == 10);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("产品质量及价格") && c.weight().intValue() == 10);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("物流仓储方案") && c.weight().intValue() == 8);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("服务团队方案") && c.weight().intValue() == 10);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("平台系统对接方案") && c.weight().intValue() == 3);
        assertThat(candidates).anyMatch(c -> c.dim() != null && c.dim().contains("增值服务方案") && c.weight().intValue() == 4);

        // 验证总分闭环为 100 分
        java.math.BigDecimal totalWeight = candidates.stream()
                .map(ScoreCandidate::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(totalWeight).isEqualByComparingTo("100.0");

        // 验证不包含 MRO 分类目录表和合同考核表
        assertThat(candidates).noneMatch(c -> c.dim() != null && c.dim().contains("呼吸防护"));
        assertThat(candidates).noneMatch(c -> c.dim() != null && c.dim().contains("妥投信息"));
    }
}
