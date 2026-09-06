package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsivenessReviewPolicyTest {

    @Test
    @DisplayName("偏离表核查：标书声明无负偏离且填写规范时直通 PASS")
    void testCheck_WhenNoDeviationDeclared_Passes() {
        String text = """
                第四章 偏离表
                第一节 商务规格偏离表
                序号 | 招标文件条款 | 投标文件响应 | 偏离情况 | 说明
                1    | 付款方式     | 完全响应     | 无偏离   | 满足招标要求
                2    | 交货期       | 响应60天     | 无偏离   | 满足招标要求
                本投标人郑重承诺：我司完全响应招标文件全部商务与技术条款，无任何负偏离。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("无实质性负偏离");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("偏离表核查：标书自述实质性负偏离时判定 FAIL")
    void testCheck_WhenNegativeDeviationDeclared_Fails() {
        String text = """
                商务偏离表：
                条款3.2交货期，招标要求30天，我方因产能排期实际为45天，存在负偏离。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash2", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("实质性负偏离");
    }

    @Test
    @DisplayName("偏离表反假：偏离表中存在占位模板或下划线留白时判定 FAIL 拦截")
    void testCheck_WhenPlaceholdersPresent_Fails() {
        String text = """
                技术偏离表：
                序号 | 招标条款 | 投标响应 | 偏离说明
                1    | 参数A    | 响应     | 【请填写偏离说明】
                2    | 参数B    | 响应     | ___________
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash3", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位模板或空白留白");
    }

    @Test
    @DisplayName("偏离表反假：仅在目录中列出偏离表条目判定 FAIL")
    void testCheck_WhenOnlyInCatalog_Fails() {
        String text = "目录\n四、商务与技术条款偏离表............................35";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash4", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("仅在目录中");
    }

    @Test
    @DisplayName("偏离表核查：未提及偏离表时返回 null 留交 LLM 语义裁决")
    void testCheck_WhenNotMentioned_ReturnsNull() {
        String text = "普通项目建设方案正文";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNull();
    }
}
