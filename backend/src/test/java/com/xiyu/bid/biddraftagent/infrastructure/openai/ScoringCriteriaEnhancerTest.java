package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScoringCriteriaEnhancerTest {

    private TenderRequirementProfile baseProfile(List<ScoringCriterion> items) {
        return new TenderRequirementProfile(
                "测试项目", "测试标讯", "测试范围", "测试招标人",
                null, null, null, null, null,
                List.of(), List.of(), List.of(),
                List.of("原文评分标准"),
                items,
                null, List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void enhance_shouldUseRegexResult_whenRegexExtractedItems() {
        // AI 返回空，正则能提取到
        TenderRequirementProfile profile = baseProfile(List.of());
        String fullText = """
                第一章 招标公告

                第二章 评标办法

                1  价格  30分
                2  技术方案  50分
                3  商务方案  20分

                第三章 合同
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        assertEquals(3, result.scoringCriteriaItems().size());
        assertEquals("价格", result.scoringCriteriaItems().get(0).dimension());
        assertEquals(new BigDecimal("30"), result.scoringCriteriaItems().get(0).weight());
    }

    @Test
    void enhance_shouldKeepAiResult_whenAiAlreadyHasItems() {
        // AI 已经提取到 3 条，正则也能提取到 -> 正则优先覆盖
        List<ScoringCriterion> aiItems = List.of(
                new ScoringCriterion("1", "价格", "投标报价", new BigDecimal("30"), null),
                new ScoringCriterion("2", "技术", "方案", new BigDecimal("70"), null)
        );
        TenderRequirementProfile profile = baseProfile(aiItems);
        String fullText = """
                评标办法

                1  价格  30分
                2  技术  50分
                3  商务  20分
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        // 正则优先，覆盖 AI 结果
        assertEquals(3, result.scoringCriteriaItems().size());
        assertEquals("商务", result.scoringCriteriaItems().get(2).dimension());
    }

    @Test
    void enhance_shouldKeepOriginal_whenNoSectionLocated() {
        // 全文中找不到评分标准章节
        List<ScoringCriterion> aiItems = List.of(
                new ScoringCriterion("1", "价格", "投标报价", new BigDecimal("100"), null)
        );
        TenderRequirementProfile profile = baseProfile(aiItems);
        String fullText = """
                第一章 招标公告
                本项目采用公开招标方式。
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        // 保持原样
        assertEquals(1, result.scoringCriteriaItems().size());
        assertEquals("价格", result.scoringCriteriaItems().get(0).dimension());
    }

    @Test
    void enhance_shouldKeepOriginal_whenSectionLocatedButRegexEmpty() {
        // 定位到章节，但正则没提取到（纯文本描述，无结构化表格）
        List<ScoringCriterion> aiItems = List.of(
                new ScoringCriterion("1", "综合", "综合评分", new BigDecimal("100"), null)
        );
        TenderRequirementProfile profile = baseProfile(aiItems);
        String fullText = """
                评标办法

                本项目采用综合评分法进行评审。
                评审委员会按照招标文件规定的方法和标准进行评审。
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        // 正则没提取到，保持 AI 原结果
        assertEquals(1, result.scoringCriteriaItems().size());
        assertEquals("综合", result.scoringCriteriaItems().get(0).dimension());
    }

    @Test
    void enhance_shouldHandleNullFullText() {
        TenderRequirementProfile profile = baseProfile(List.of());
        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, null);
        assertEquals(0, result.scoringCriteriaItems().size());
    }

    @Test
    void enhance_shouldHandleBlankFullText() {
        TenderRequirementProfile profile = baseProfile(List.of());
        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, "  ");
        assertEquals(0, result.scoringCriteriaItems().size());
    }

    @Test
    void enhance_shouldHandleNullProfile() {
        assertThrows(NullPointerException.class,
                () -> ScoringCriteriaEnhancer.enhance(null, "some text"));
    }

    @Test
    void enhance_shouldPreserveOtherFields() {
        // 增强 scoringCriteriaItems 时不能丢失其他字段
        TenderRequirementProfile profile = new TenderRequirementProfile(
                "项目名", "标讯标题", "范围", "招标人",
                new BigDecimal("1000000"), "北京市", "IT",
                null, null,
                List.of("资质1"), List.of("技术1"), List.of("商务1"),
                List.of("评分原文"),
                List.of(),
                "截止说明",
                List.of("材料1"), List.of("风险1"), List.of("标签1"),
                List.of(new TenderRequirementItemSnapshot("category", "title", "content", false, "excerpt", 80))
        );
        String fullText = """
                评标办法

                1  价格  30分
                2  技术  70分
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        // 评分标准被增强
        assertEquals(2, result.scoringCriteriaItems().size());
        // 其他字段保持不变
        assertEquals("项目名", result.projectName());
        assertEquals("标讯标题", result.tenderTitle());
        assertEquals("招标人", result.purchaserName());
        assertEquals(new BigDecimal("1000000"), result.budget());
        assertEquals("北京市", result.region());
        assertEquals(1, result.qualificationRequirements().size());
        assertEquals(1, result.technicalRequirements().size());
        assertEquals(1, result.commercialRequirements().size());
        assertEquals(1, result.requiredMaterials().size());
        assertEquals(1, result.riskPoints().size());
        assertEquals(1, result.tags().size());
        assertEquals(1, result.items().size());
    }

    @Test
    void enhance_shouldAlsoUpdateScoringCriteriaText() {
        // 增强 items 时也应更新 scoringCriteria 原文列表
        TenderRequirementProfile profile = baseProfile(List.of());
        String fullText = """
                评标办法

                1  价格  30分
                2  技术  70分
                """;

        TenderRequirementProfile result = ScoringCriteriaEnhancer.enhance(profile, fullText);

        assertEquals(2, result.scoringCriteriaItems().size());
        // scoringCriteria 原文列表应反映正则提取结果
        assertFalse(result.scoringCriteria().isEmpty());
        assertTrue(result.scoringCriteria().stream().anyMatch(s -> s.contains("价格")));
    }
}
