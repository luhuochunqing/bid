package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectionAliasesTest {

    @Test
    void qualification_shouldContainCoreAliases() {
        assertTrue(SectionAliases.QUALIFICATION.contains("资格要求"));
        assertTrue(SectionAliases.QUALIFICATION.contains("资格条件"));
        assertTrue(SectionAliases.QUALIFICATION.contains("投标人资格"));
        assertTrue(SectionAliases.QUALIFICATION.contains("资质要求"));
    }

    @Test
    void technical_shouldContainCoreAliases() {
        assertTrue(SectionAliases.TECHNICAL.contains("技术要求"));
        assertTrue(SectionAliases.TECHNICAL.contains("技术规范"));
        assertTrue(SectionAliases.TECHNICAL.contains("技术标准"));
    }

    @Test
    void commercial_shouldContainCoreAliases() {
        assertTrue(SectionAliases.COMMERCIAL.contains("商务要求"));
        assertTrue(SectionAliases.COMMERCIAL.contains("商务条款"));
        assertTrue(SectionAliases.COMMERCIAL.contains("合同条款"));
    }

    @Test
    void risk_shouldContainCoreAliases() {
        assertTrue(SectionAliases.RISK.contains("废标条款"));
        assertTrue(SectionAliases.RISK.contains("否决条款"));
        assertTrue(SectionAliases.RISK.contains("无效投标"));
    }

    @Test
    void allAliases_shouldNotContainNullOrBlank() {
        for (String alias : SectionAliases.QUALIFICATION) {
            assertNotNull(alias);
            assertFalse(alias.isBlank());
        }
        for (String alias : SectionAliases.TECHNICAL) {
            assertNotNull(alias);
            assertFalse(alias.isBlank());
        }
        for (String alias : SectionAliases.COMMERCIAL) {
            assertNotNull(alias);
            assertFalse(alias.isBlank());
        }
        for (String alias : SectionAliases.RISK) {
            assertNotNull(alias);
            assertFalse(alias.isBlank());
        }
    }

    @Test
    void scoring_shouldBePreserved() {
        // 评分标准别名词表应仍然可用
        assertEquals(ScoringSectionAliases.ALL, SectionAliases.SCORING);
    }
}
