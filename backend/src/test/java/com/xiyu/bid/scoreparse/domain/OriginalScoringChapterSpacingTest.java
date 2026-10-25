package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalScoringChapterSpacingTest {

    @Test
    void chapterTitleWithInterCharacterSpacesRecognizedAsValidSchemeRoot() {
        assertTrue(OriginalScoringSchemePolicy.isValidSchemeRoot("第四章 评 标 办 法"));
        assertTrue(OriginalScoringSchemePolicy.isValidSchemeRoot("第三章 评 审 办 法"));
        assertTrue(OriginalScoringSchemePolicy.isValidSchemeRoot("第二章 评 分 标 准"));
        assertTrue(OriginalScoringSchemePolicy.isValidSchemeRoot("第四章 评 标 方 法"));
    }

    @Test
    void textScopeSpansAcrossWholeChapterWhenHeadingHasSpaces() {
        String tenderText = String.join("\n",
                "第三章 合同条款",
                "合同正文内容...",
                "第四章 评 标 办 法",
                "一、评标原则",
                "公开公平公正原则...",
                "二、评分办法",
                "满分为 100 分，按得分高低推荐...",
                "三、评分因素及分值细则",
                "技术部分70分，商务部分30分。",
                "| 序号 | 评分项 | 分值 |",
                "| 1 | 平台自营 | 8 |",
                "第五章 投标文件格式",
                "投标文件格式正文..."
        );

        List<OriginalScoringContext.TextScope> scopes = OriginalScoringTextScopePolicy.textScopes(tenderText);
        assertFalse(scopes.isEmpty());

        // 行号从 0 开始:
        // 2: "第四章 评 标 办 法"
        // 7: "三、评分因素及分值细则"
        // 8: "技术部分70分，商务部分30分。"
        // 10: "| 1 | 平台自营 | 8 |"
        // 11: "第五章 投标文件格式"
        OriginalScoringContext.TextScope scoringDetailScope = OriginalScoringTextScopePolicy.textScopeAt(tenderText, 8);
        assertEquals("IN_SCHEME", scoringDetailScope.scopeStatus());
        assertEquals("第四章 评 标 办 法", scoringDetailScope.title());

        OriginalScoringContext.TextScope tableRowScope = OriginalScoringTextScopePolicy.textScopeAt(tenderText, 10);
        assertEquals("IN_SCHEME", tableRowScope.scopeStatus());

        var artifact = new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(
                new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("48", "fixture-48", "PDF", "2.2"),
                List.of(), List.of());
        var context = OriginalScoringContext.resolve(artifact, tenderText);
        assertEquals(new BigDecimal("100"), context.total().value());
    }
}
