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

    @Test
    @DisplayName("结构化负偏离拦截：表格中存在负偏离行时精准指出条款与偏离原因并 FAIL")
    void testCheck_WhenStructuredNegativeRow_FailsWithDetail() {
        String text = """
                ### 商务偏离表
                | 序号 | 招标文件条款 | 投标文件响应 | 偏离情况 | 说明 |
                | 1 | 条款 3.2 质保期 | 质保期 2 年 | 负偏离 | 招标要求3年，因产品生命周期实际提供2年 |
                | 2 | 条款 4.1 付款 | 完全响应 | 无偏离 | 满足 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash6", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("实质性负偏离");
        assertThat(result.conclusion()).contains("条款 3.2 质保期");
        assertThat(result.conclusion()).contains("产品生命周期");
    }

    @Test
    @DisplayName("表头包含偏离选项防护：表头标注'偏离情况(正偏离/负偏离/无偏离)'但数据行全满足时不误报 FAIL")
    void testCheck_WhenTableHeaderContainsNegativeKeyword_Passes() {
        String text = """
                ### 技术偏离表
                | 序号 | 技术指标条款 | 招标参数要求 | 投标响应参数 | 偏离情况(正偏离/负偏离/无偏离) | 偏离说明 |
                | 1 | 1.1 性能指标 | TPS >= 1000 | TPS 1500 | 无偏离 | 完全响应 |
                | 2 | 1.2 兼容性 | 支持国产OS | 全面支持统信/麒麟 | 无偏离 | 完全满足 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash7", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("无实质性负偏离");
    }

    @Test
    @DisplayName("对抗测试：正偏离审慎机制（自述'正偏离'防假阳性免审，不直接 PASS，安全移交专家/LLM 复核）")
    void testCheck_WhenPositiveDeviationPresent_ReturnsNullForExpertReview() {
        String text = """
                ### 技术规格偏离表
                | 序号 | 规格条款 | 招标要求 | 投标响应 | 偏离情况 | 偏离说明 |
                | 1 | 架构冗余 | 单活架构 | 双活容灾热备 | 正偏离 | 容灾等级高于招标要求 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash8", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        // 真正的正偏离需经指标对比核验，不能单凭投标人自称免审放行，应移交 LLM/专家
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("对抗测试：正文中长距离否定修饰（'我方郑重声明绝不具有任何负偏离'）不被全文扫描误拦截 FAIL")
    void testCheck_WhenLongDistanceNegationInDeclaration_DoesNotTriggerFail() {
        String text = """
                商务及技术偏离表总体说明：
                我方在此郑重声明绝不具有任何负偏离。
                全体承诺完全响应招标文件全部要求。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash8_adv", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("双偏离表齐备核查：商务与技术偏离表双表齐备且无偏离直通 PASS")
    void testCheck_WhenDualTablesCompliant_Passes() {
        String text = """
                第一节 商务偏离表
                | 序号 | 条款 | 响应 | 偏离情况 | 说明 |
                | 1 | 付款方式 | 响应 | 无偏离 | 满足 |

                第二节 技术偏离表
                | 序号 | 参数 | 响应 | 偏离情况 | 说明 |
                | 1 | 交付周期 | 30天 | 无偏离 | 满足 |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash9", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("Major 1 回归：偏离表中存在 UNKNOWN 存疑行时，绝不能被文末自述无偏离绕过误 PASS")
    void testCheck_WhenUnknownRowWithDeclaration_ReturnsNull() {
        String text = """
                ### 商务偏离表
                | 序号 | 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |
                | 1 | 质保金 | 5% | 约定待定 | 需进一步确认协商 | 商务待定事项 |
                | 2 | 工期 | 180天 | 响应 | 无偏离 | 满足 |
                本公司郑重声明：我司完全响应招标文件全部商务与技术条款，无任何负偏离。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash10", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        // 存疑行不应直接 PASS，应交由 LLM 语义核验
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Major 2 回归：偏离表全空数据行不应判定为 NONE 导致误 PASS")
    void testCheck_WhenEmptyCellsRow_DoesNotPass() {
        String text = """
                ### 商务偏离表
                | 序号 | 招标文件条款 | 招标文件要求 | 投标文件响应 | 偏离情况 | 偏离说明 |
                | 1 | 条款A | 要求A | | | |
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash11", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        // 空行不代表无偏离，不应直接 PASS
        assertThat(result == null || result.status() != QualityCheckStatus.PASS).isTrue();
    }

    @Test
    @DisplayName("Major 3 回归：全量 NEGATIVES 词表覆盖（'部分满足'即使无外围管道符也应硬拦截 FAIL）")
    void testCheck_WhenSubItemPartiallySatisfiedWithoutPipes_Fails() {
        String text = """
                商务偏离说明：
                关于第三条交货期要求，我方因产能排期实际为部分满足。
                本公司郑重声明无任何负偏离。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash12", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("实质性负偏离");
        assertThat(result.conclusion()).contains("部分满足");
    }

    @Test
    @DisplayName("对抗审讯回归：正文'因自身原因未达到要求属负偏离'不被长距离'未'错误洗白，精准判定 FAIL")
    void testCheck_WhenUnfulfilledLeadingToNegativeDeviation_FailsAccurately() {
        String text = """
                第二章 商务偏离表说明：
                我方因自身产能排期限制未达到招标文件要求属负偏离。
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash13", text);

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(doc);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("实质性负偏离");
    }
}
