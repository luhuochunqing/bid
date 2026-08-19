// Input: 竞品 notes 字段值（已中标/未中标/历史自由文本）
// Output: parseCompetitorWon 解析断言
// Pos: Test/M4 竞品分析纯计算服务单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("M4 竞品分析纯计算 — 竞品是否中标枚举解析")
class CompetitorAnalysisComputationServiceTest {

    private final CompetitorAnalysisComputationService service = new CompetitorAnalysisComputationService();

    @Test
    @DisplayName("notes 为「已中标」时返回 true（允许首尾空白）")
    void parseCompetitorWonAcceptsWonEnumWithWhitespace() {
        assertThat(service.parseCompetitorWon("已中标")).isTrue();
        assertThat(service.parseCompetitorWon("  已中标 ")).isTrue();
    }

    @Test
    @DisplayName("notes 为「未中标」/null/空时返回 false")
    void parseCompetitorWonRejectsLostOrNull() {
        assertThat(service.parseCompetitorWon("未中标")).isFalse();
        assertThat(service.parseCompetitorWon(null)).isFalse();
        assertThat(service.parseCompetitorWon("")).isFalse();
        assertThat(service.parseCompetitorWon("   ")).isFalse();
    }

    @Test
    @DisplayName("历史「其他说明」自由文本不属于枚举，按未中标处理")
    void parseCompetitorWonTreatsLegacyFreeTextAsLost() {
        assertThat(service.parseCompetitorWon("价格偏高")).isFalse();
        assertThat(service.parseCompetitorWon("已中标（待确认）")).isFalse();
    }
}
