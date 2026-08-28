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
}
