package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileEnhancerTest {

    private TenderRequirementProfile baseProfile() {
        return new TenderRequirementProfile(
                "项目名", "标讯", "范围", "招标人",
                null, null, null, null, null,
                List.of(), List.of(), List.of(),
                List.of(), List.of(),
                null, List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void enhance_shouldExtractQualificationRequirements() {
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                第一章 招标公告

                第二章 资格要求

                1. 投标人须具有独立法人资格
                2. 投标人须具有ISO9001质量管理体系认证
                3. 投标人注册资本不低于500万元

                第三章 评标办法
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertFalse(result.qualificationRequirements().isEmpty());
        assertTrue(result.qualificationRequirements().stream()
                .anyMatch(s -> s.contains("独立法人")));
        assertTrue(result.qualificationRequirements().stream()
                .anyMatch(s -> s.contains("ISO9001")));
    }

    @Test
    void enhance_shouldExtractTechnicalRequirements() {
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                技术要求

                1. 系统应支持1000并发用户
                2. 响应时间不超过2秒
                3. 数据存储容量不低于10TB

                第四章 商务要求
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertFalse(result.technicalRequirements().isEmpty());
        assertTrue(result.technicalRequirements().stream()
                .anyMatch(s -> s.contains("1000并发")));
        assertTrue(result.technicalRequirements().stream()
                .anyMatch(s -> s.contains("10TB")));
    }

    @Test
    void enhance_shouldExtractCommercialRequirements() {
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                第一章 招标公告

                商务条款

                1. 付款方式：验收合格后30天内付款
                2. 交货期：合同签订后60天内
                3. 质保期：验收合格后2年

                第五章 风险条款
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertFalse(result.commercialRequirements().isEmpty());
        assertTrue(result.commercialRequirements().stream()
                .anyMatch(s -> s.contains("付款方式")));
        assertTrue(result.commercialRequirements().stream()
                .anyMatch(s -> s.contains("质保期")));
    }

    @Test
    void enhance_shouldExtractRiskPoints() {
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                废标条款

                1. 投标文件未按招标文件要求签署的
                2. 投标报价超过最高限价的
                3. 不符合资格条件的

                第六章 附则
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertFalse(result.riskPoints().isEmpty());
        assertTrue(result.riskPoints().stream()
                .anyMatch(s -> s.contains("最高限价")));
        assertTrue(result.riskPoints().stream()
                .anyMatch(s -> s.contains("签署")));
    }

    @Test
    void enhance_shouldHandleNullFullText() {
        TenderRequirementProfile profile = baseProfile();
        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, null);
        assertTrue(result.qualificationRequirements().isEmpty());
        assertTrue(result.technicalRequirements().isEmpty());
    }

    @Test
    void enhance_shouldKeepOriginalWhenNoSectionFound() {
        TenderRequirementProfile profile = new TenderRequirementProfile(
                "项目", "标讯", "范围", "招标人",
                null, null, null, null, null,
                List.of("资质1"), List.of("技术1"), List.of("商务1"),
                List.of(), List.of(),
                null, List.of("材料1"), List.of("风险1"), List.of("标签1"), List.of()
        );

        String fullText = "这是一段不含任何标准章节标题的纯文本内容。";

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertEquals(1, result.qualificationRequirements().size());
        assertEquals("资质1", result.qualificationRequirements().get(0));
        assertEquals(1, result.technicalRequirements().size());
        assertEquals(1, result.commercialRequirements().size());
        assertEquals(1, result.riskPoints().size());
    }

    @Test
    void enhance_shouldPreserveOtherFields() {
        TenderRequirementProfile profile = new TenderRequirementProfile(
                "项目名", "标讯标题", "范围", "招标人",
                new BigDecimal("1000000"), "北京市", "IT",
                null, null,
                List.of("旧资质"), List.of("旧技术"), List.of("旧商务"),
                List.of(), List.of(),
                "截止说明",
                List.of("材料1"), List.of("旧风险"), List.of("标签1"),
                List.of(new TenderRequirementItemSnapshot("cat", "title", "content", false, "excerpt", 80))
        );
        String fullText = """
                资格要求

                1. 新资质要求

                技术要求

                1. 新技术要求
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertEquals("项目名", result.projectName());
        assertEquals("招标人", result.purchaserName());
        assertEquals(new BigDecimal("1000000"), result.budget());
        assertEquals("截止说明", result.deadlineText());
        assertEquals(1, result.requiredMaterials().size());
        assertEquals(1, result.items().size());
    }

    @Test
    void enhance_shouldAlsoEnhanceScoringCriteria() {
        // ProfileEnhancer 应同时增强评分标准（兼容 ScoringCriteriaEnhancer 逻辑）
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                评标办法

                1  价格  30分
                2  技术  70分
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertEquals(2, result.scoringCriteriaItems().size());
    }

    @Test
    void enhance_shouldExtractAllDimensionsSimultaneously() {
        TenderRequirementProfile profile = baseProfile();
        String fullText = """
                第一章 资格要求

                1. 具有独立法人资格
                2. 具有相关资质

                第二章 技术要求

                1. 系统支持高并发
                2. 响应时间小于2秒

                第三章 商务条款

                1. 付款周期30天
                2. 质保期2年

                第四章 废标条款

                1. 超过最高限价
                2. 资格不符合
                """;

        TenderRequirementProfile result = ProfileEnhancer.enhance(profile, fullText);

        assertFalse(result.qualificationRequirements().isEmpty(), "qualification should be extracted");
        assertFalse(result.technicalRequirements().isEmpty(), "technical should be extracted");
        assertFalse(result.commercialRequirements().isEmpty(), "commercial should be extracted");
        assertFalse(result.riskPoints().isEmpty(), "risk should be extracted");
    }
}
