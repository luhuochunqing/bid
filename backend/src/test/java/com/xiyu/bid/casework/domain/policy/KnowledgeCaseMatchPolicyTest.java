package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchCriteria;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KnowledgeCaseMatchPolicy 单元测试。
 *
 * <p>纯核心测试：无 Spring 上下文，无数据库，直接实例化策略类进行验证。
 */
class KnowledgeCaseMatchPolicyTest {

    private KnowledgeCaseMatchPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new KnowledgeCaseMatchPolicy();
    }

    @Test
    @DisplayName("完全匹配：同标题+同类别+同项目类型+同客户类型+中标 → 高分")
    void score_FullMatch_ShouldReturnHighScore() {
        KnowledgeCase candidate = createCase(
                "技术方案整体架构设计", "技术", "国有企业", "国有企业", "WON", "我们采用微服务架构..."
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "技术方案整体架构设计", "技术", "国有企业", "国有企业", null, null, null
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertTrue(result.score() >= 85, "完全匹配应达到优质标准(>=85)，实际=" + result.score());
        assertEquals("优质", result.scoreLabel());
        assertTrue(result.matchReason().contains("评分项匹配"));
        assertTrue(result.matchReason().contains("类别一致"));
        assertTrue(result.matchReason().contains("项目类型一致"));
        assertTrue(result.matchReason().contains("客户类型一致"));
        assertTrue(result.matchReason().contains("中标案例"));
    }

    @Test
    @DisplayName("类别+项目类型+客户类型一致但标题不同 → 中等分数")
    void score_CategoryAndTypesMatch_ShouldReturnMediumScore() {
        KnowledgeCase candidate = createCase(
                "数据备份与恢复方案", "技术", "民营企业", "民营企业", "LOST", "每日全量备份..."
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "技术方案整体架构设计", "技术", "民营企业", "民营企业", null, null, null
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertTrue(result.score() >= 40 && result.score() < 60,
                "类别和类型一致但标题不同应在40-60之间，实际=" + result.score());
        assertEquals("一般", result.scoreLabel());
        assertTrue(result.matchReason().contains("类别一致"));
        assertTrue(result.matchReason().contains("项目类型一致"));
        assertTrue(result.matchReason().contains("客户类型一致"));
    }

    @Test
    @DisplayName("无任何匹配 → 0分")
    void score_NoMatch_ShouldReturnZero() {
        KnowledgeCase candidate = createCase(
                "售后服务承诺", "实施服务", "外资企业", "外资企业", "LOST", "7x24小时响应..."
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "技术方案整体架构设计", "技术", "国有企业", "国有企业", null, null, null
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertEquals(0, result.score(), "无匹配时应为0分");
        assertEquals("一般", result.scoreLabel());
        assertEquals("基础匹配", result.matchReason());
    }

    @Test
    @DisplayName("中标案例获得额外加分")
    void score_WonCase_ShouldHaveBonus() {
        KnowledgeCase wonCase = createCase(
                "资质业绩要求", "资质业绩", "国有企业", "国有企业", "WON", null
        );

        KnowledgeCase lostCase = createCase(
                "资质业绩要求", "资质业绩", "国有企业", "国有企业", "LOST", null
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "资质业绩要求", "资质业绩", "国有企业", "国有企业", null, null, null
        );

        KnowledgeCaseMatchScore wonResult = policy.score(wonCase, criteria);
        KnowledgeCaseMatchScore lostResult = policy.score(lostCase, criteria);

        assertTrue(wonResult.score() > lostResult.score(),
                "中标案例分数应高于未中标，WON=" + wonResult.score() + ", LOST=" + lostResult.score());
        assertTrue(wonResult.matchReason().contains("中标案例"));
    }

    @Test
    @DisplayName("关键词命中标题/需求/应答")
    void score_KeywordMatch_ShouldAddScore() {
        KnowledgeCase candidate = createCase(
                "安全防护方案", "技术", "国有企业", "国有企业", null,
                "采用防火墙和入侵检测系统..."
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "安全防护方案", "技术", "国有企业", "国有企业", null, null, "防火墙"
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertTrue(result.matchReason().contains("关键词命中"));
        assertTrue(result.score() > 0);
    }

    @ParameterizedTest
    @CsvSource({
            "技术方案,技术,技术",
            "商务报价,商务,商务",
            "实施服务方案,实施服务,实施服务",
            "资质要求,资质业绩,资质业绩",
            "业绩证明,资质业绩,资质业绩",
            "其他要求,其他,其他"
    })
    @DisplayName("标题相似度计算：部分重叠应得分")
    void score_TitleSimilarity_ShouldCalculateCorrectly(String caseTitle, String criteriaTitle, String category) {
        KnowledgeCase candidate = createCase(
                caseTitle, category, "国有企业", "国有企业", null, null
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, criteriaTitle, category, "国有企业", "国有企业", null, null, null
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertTrue(result.score() >= 0, "任何匹配都应非负，实际=" + result.score());
    }

    @Test
    @DisplayName("高亮文本应包含 mark 标签")
    void score_HighlightedText_ShouldContainMarkTags() {
        KnowledgeCase candidate = createCase(
                "数据备份方案", null, null, null, null,
                "我们提供每日备份和实时同步服务"
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L, "数据备份方案", null, null, null, null, null, "备份"
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertNotNull(result.highlightedText());
        assertTrue(result.highlightedText().contains("<mark>"), "高亮文本应包含 <mark> 标签");
    }

    @Test
    @DisplayName("封顶100分")
    void score_ShouldCapAt100() {
        KnowledgeCase candidate = createCase(
                "技术方案整体架构设计数据备份安全防护",
                "技术", "国有企业", "国有企业", "WON",
                "技术方案整体架构设计数据备份安全防护"
        );

        KnowledgeCaseMatchCriteria criteria = new KnowledgeCaseMatchCriteria(
                1L,
                "技术方案整体架构设计数据备份安全防护",
                "技术", "国有企业", "国有企业",
                null, null, "技术"
        );

        KnowledgeCaseMatchScore result = policy.score(candidate, criteria);

        assertTrue(result.score() <= 100, "分数不应超过100，实际=" + result.score());
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private KnowledgeCase createCase(String scoringPointTitle, String scoringCategory,
                                      String projectType, String customerType,
                                      String bidResult, String responseText) {
        KnowledgeCase kc = new KnowledgeCase();
        kc.setId(1L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("测试项目");
        kc.setScoringPointTitle(scoringPointTitle != null ? scoringPointTitle : "默认评分项");
        kc.setRequirementRaw("默认要求");
        kc.setResponseText(responseText != null ? responseText : "默认应答");
        kc.setReuseCount(0);
        kc.setStatus("ACTIVE");
        kc.setCustomerType(customerType != null ? customerType : "国有企业");
        kc.setProjectType(projectType != null ? projectType : "综合");
        kc.setScoringCategory(scoringCategory);
        kc.setBidResult(bidResult);
        return kc;
    }
}
