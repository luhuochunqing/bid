// Input: ScoreParsePrompts.buildGlobalMergePrompt
// Output: 归并裁决 prompt 关键约束与输入嵌入验证（契约 §5 硬性规则 1-5）
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreGlobalMergePromptTest {

    @Test
    void prompt_containsAllFiveHardRules_andInputs() {
        String prompt = ScoreParsePrompts.buildGlobalMergePrompt(
                "[{\"idx\":0,\"code\":\"\",\"dim\":\"价格部分\",\"detail\":\"不平衡报价评审得4-5分\",\"weight\":5}]",
                "价格部分：\n不平衡报价评审：报价合理得4-5分。");
        assertThat(prompt)
                .contains("禁止发明候选池外的新评分项")
                .contains("必须归并为一项")
                .contains("必须保持独立，禁止合并")
                .contains("逐字摘自章节原文")
                .contains("不得遗漏");
        assertThat(prompt).contains("不平衡报价评审得4-5分");
        assertThat(prompt).contains("价格部分：\n不平衡报价评审：报价合理得4-5分。");
    }

    @Test
    void prompt_stripsImagePayloadsFromSectionText() {
        String withImage = "评分表\n![img](data:image/png;base64,AAAA)\n评审标准得5分";
        String prompt = ScoreParsePrompts.buildGlobalMergePrompt("[]", withImage);
        assertThat(prompt).doesNotContain("data:image");
        assertThat(prompt).contains("评审标准得5分");
    }
}
