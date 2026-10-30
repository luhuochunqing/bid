package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoringNumberedHeadingTest {
    @Test void numberedScoringRulesHeadingOwnsItsBodyButStopsAtNextSection() {
        var text = String.join("\n", "6 投标须知", "投标须知正文", "7 招标评分规则（投标人需要提供证明材料索引，供评委打分参考）",
                "| 序号 | 评价维度 | 评分细则 | 分值 |", "| 1 | 服务 | 服务得100分 | 100 |",
                "| 2 | 合计 | | 100 |", "8 评标流程", "评标流程说明");
        assertThat(OriginalScoringSchemePolicy.isValidSchemeRoot("7 招标评分规则（投标人需要提供证明材料索引，供评委打分参考）")).isTrue();
        assertThat(OriginalScoringContext.textScopeAt(text, 4).scopeStatus()).isEqualTo("IN_SCHEME");
        assertThat(OriginalScoringContext.textScopeAt(text, 7).scopeStatus()).isNotEqualTo("IN_SCHEME");
    }
    @Test void referenceSamplesRemainOutsideScheme() {
        assertThat(OriginalScoringSchemePolicy.isValidSchemeRoot("7 参考评分规则示例")).isFalse();
        assertThat(OriginalScoringSchemePolicy.isOtherSection("7 参考评分规则示例")).isTrue();
        assertThat(OriginalScoringSchemePolicy.isOtherSection("7 招标评分规则（供评委打分参考）")).isFalse();
    }
}
