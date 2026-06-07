package com.xiyu.bid.documents.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAssemblyServiceVariableReplacementTest extends AbstractDocumentAssemblyServiceTest {

    @Test
    void replaceVariables_ShouldCorrectlyReplaceAllPlaceholders() {
        String template = "尊敬的${name}，关于${project}的报价为${amount}元。";
        String variablesJson = "{\"name\":\"张三\",\"project\":\"ABC项目\",\"amount\":100000}";

        String result = documentAssemblyService.replaceVariables(template, variablesJson);

        assertThat(result).contains("张三");
        assertThat(result).contains("ABC项目");
        assertThat(result).contains("100000");
        assertThat(result).doesNotContain("${");
    }

    @Test
    void replaceVariables_WithEmptyJson_ShouldReturnOriginalTemplate() {
        String result = documentAssemblyService.replaceVariables("尊敬的${name}，这是测试内容。", "{}");

        assertThat(result).contains("${name}");
    }

    @Test
    void replaceVariables_WithExtraVariables_ShouldIgnoreExtra() {
        String result = documentAssemblyService.replaceVariables(
                "项目：${project}",
                "{\"project\":\"ABC\",\"extra\":\"忽略\"}"
        );

        assertThat(result).contains("ABC");
        assertThat(result).doesNotContain("${project}");
    }
}
