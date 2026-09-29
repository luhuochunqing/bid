package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ScoreAnchorPromptTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("空锚点列表生成空提示词")
    void emptyAnchorsProducesEmptyString() {
        String prompt = ScoreAnchorPrompt.build(List.of(), objectMapper);
        assertThat(prompt).isEmpty();
    }

    @Test
    @DisplayName("大量源锚点超出 8000 字符预算时安全截断且不抛出异常")
    void largeAnchorsSafelyTruncatedWithinBudget() {
        List<SourceAnchorCatalog.Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String text = "这是第 " + i + " 个长文本评分标准段落，包含详细的业务评审规则与指标说明。";
            SourceSpan span = new SourceSpan(
                    "doc-1", "file-1.pdf", "PDF", "SCHEME-1", 34,
                    "t0010", "t0010-r" + i + "-c3", 0, text.length(), text, "locator-" + i);
            anchors.add(new SourceAnchorCatalog.Anchor(
                    "anchor-" + i, "SCHEME-1", "第三章 评审办法", "t0010",
                    List.of("t0010-r" + i + "-c3"), text, List.of(span)));
        }

        assertThatCode(() -> {
            String prompt = ScoreAnchorPrompt.build(anchors, objectMapper);
            assertThat(prompt).isNotEmpty();
            assertThat(prompt.length()).isLessThanOrEqualTo(8_000);
            assertThat(prompt).contains("anchor-0");
        }).doesNotThrowAnyException();
    }
}
