package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Project1133SourceBoundaryTest {
    @Test void hyphenWrappedTocPageIsNotASchemeOrScopeBoundary() {
        String text = "# 目录\n第七章 评分标准 ...........................................- 52 -\n"
                + "# 第六章 投标文件格式\n总体组织实施方案（格式自拟）\n"
                + "# 第七章 评分标准\n总分100分。\n总体组织实施方案：更为优越得10-7.1分。";
        assertThat(OriginalScoringContext.textScopes(text)).filteredOn(s -> "IN_SCHEME".equals(s.scopeStatus()))
                .extracting(OriginalScoringContext.TextScope::title).containsOnly("第七章 评分标准");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(), List.of());
        assertThat(OriginalScoringContext.resolve(artifact, text).total().value()).isEqualByComparingTo("100");
        assertThat(OriginalScoringSchemePolicy.sameScheme("第七章 评分标准 ...- 52 -", "第七章 评分标准")).isTrue();
    }

    @Test void methodDeclarationIsNotAnIndependentScheme() {
        String text = "# 第二章 投标人须知前附表\n8 评标方法：综合评分法（本项目投标人满足3家时即可开标）。\n"
                + "# 第七章 评分标准\n总分100分。\n服务方案按档评分。";
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(), List.of());
        assertThat(OriginalScoringContext.schemes(artifact, text)).extracting(OriginalScoringContext.SchemeEvidence::title)
                .containsExactly("第七章 评分标准");
        assertThat(OriginalScoringContext.resolve(artifact, text).total().value()).isEqualByComparingTo("100");
    }

    @Test void actualNamedSchemesRemainSeparate() {
        String text = "# 第一标包 评分标准\n总分100分。\n# 第二标包 评分标准\n总分100分。";
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(), List.of());
        assertThat(OriginalScoringContext.schemes(artifact, text)).hasSize(2);
    }
    @Test void realMethodNamedChapterRemainsAScheme() {
        String text = "# 评标办法：综合评分法\n总分100分。\n方案评分规则。";
        assertThat(OriginalScoringContext.textScopes(text)).filteredOn(s -> "IN_SCHEME".equals(s.scopeStatus()))
                .extracting(OriginalScoringContext.TextScope::title).containsExactly("评标办法：综合评分法");
    }

}
