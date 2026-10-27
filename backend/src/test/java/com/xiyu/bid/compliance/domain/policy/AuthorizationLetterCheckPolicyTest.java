package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationLetterCheckPolicyTest {

    @Test
    @DisplayName("合规真实授权委托书：要素齐全且非目录页，判定为 PASS")
    void testCheck_ValidAuthorizationLetter_Pass() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_1", "", List.of(
                new DocumentContent.PageContent(1, "目录\n1. 投标函......3\n2. 授权委托书......5", List.of()),
                new DocumentContent.PageContent(5, """
                        法定代表人授权委托书
                        本授权书声明：某某信息技术有限公司法定代表人张三（身份证号：110101198001011234）授权本公司员工李四（身份证号：110101199001015678）为委托代理人。
                        代理人全权代表本公司签署、澄清、提交某某数字化采购平台项目投标文件，处理一切与本次投标有关的事务。
                        授权范围：签署投标文件
                        本授权书自签字盖章之日起生效，授权期限覆盖本项目投标有效期。
                        
                        法定代表人（签字）：张三
                        委托代理人（签字）：李四
                        投标人：某某信息技术有限公司（盖章）
                        2026年9月5日
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("要素齐全");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("目录索引页拦截：若仅在目录中命中授权委托书点线条目，绝不误判 PASS，判定为 MANUAL_REVIEW")
    void testCheck_CatalogPageOnly_ManualReview() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_toc", "", List.of(
                new DocumentContent.PageContent(1, """
                        目  录
                        第一章 投标函及投标函附录 ................. 1
                        第二章 法定代表人身份证明与授权委托书 ...... 4
                        第三章 商务资质证明文件 ................... 8
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("仅在目录索引中检索到授权委托书条目");
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("占位模板留白拦截：检测到未替换括号占位符或下划线留白，判定为 MANUAL_REVIEW")
    void testCheck_UnfilledPlaceholderTemplate_ManualReview() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_template", "", List.of(
                new DocumentContent.PageContent(4, """
                        法定代表人授权委托书（模板）
                        本人【法定代表人姓名】系【投标人名称】的法定代表人，现授权委托【被授权人姓名】（身份证号：__________________）为我方代理人。
                        代理人全权代表我方处理某某项目投标事宜。
                        
                        法定代表人：______
                        受托人：_______
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未填写的模板占位符或下划线留白");
    }

    @Test
    @DisplayName("法定代表人亲签豁免：招标允许法人自签免授权书且标书附法人证明，判定为 PASS")
    void testCheck_LegalRepresentativeOnlyAllowed_Pass() {
        AuthorizationRequirement requirement = new AuthorizationRequirement(
                true, true, "法定代表人身份证明", "法定代表人亲自参加免授权委托书");

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_legal_rep", "", List.of(
                new DocumentContent.PageContent(3, """
                        法定代表人身份证明
                        投标人名称：西域供应链（上海）有限公司
                        单位性质：有限责任公司
                        成立时间：2008年10月1日
                        经营期限：长期
                        姓名：叶永清  性别：男  年龄：45岁  职务：董事长
                        系西域供应链（上海）有限公司的法定代表人。特此证明。
                        身份证号码：310101198001012345
                        
                        投标人：西域供应链（上海）有限公司（盖章）
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc, requirement);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("免除授权委托书");
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("未发现任何授权委托书或身份证明时，判定为 FAIL")
    void testCheck_NotFound_Fail() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_not_found", "", List.of(
                new DocumentContent.PageContent(1, "技术方案描述第一章 系统架构与部署环境。", List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检测到法定代表人身份证明或授权委托书");
    }

    @Test
    @DisplayName("弱声明拦截：仅含角色词加签署投标但无身份证与强授权要素时不得 PASS")
    void testCheck_WeakDeclarationWithoutFacts_ManualReview() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_weak", "", List.of(
                new DocumentContent.PageContent(2, """
                        法定代表人授权委托书
                        本公司法定代表人委托本公司委托代理人代表我方签署投标文件。
                        法定代表人：张三
                        委托代理人：李四
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("掩码身份证兼容：脱敏身份证号仍计入事实要素并判定 PASS")
    void testCheck_MaskedIdCard_Pass() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_masked", "", List.of(
                new DocumentContent.PageContent(2, """
                        法定代表人授权委托书
                        本公司法定代表人张三授权委托代理人李四（身份证号：110101****1234）为我方代理人。
                        代理人全权代表我方处理本项目投标事宜。
                        授权期限：自签署之日起至投标有效期结束
                        法定代表人（签字）：张三
                        委托代理人（签字）：李四
                        """, List.of())
        ));

        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("目录误判回归：含项目记录字样加单点线不得误判目录页")
    void testCheck_ProjectRecordWithSingleDot_NotCatalog() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash_record", "", List.of(
                new DocumentContent.PageContent(2, """
                        法定代表人授权委托书
                        本项目记录如下：授权事项明确。
                        本公司法定代表人张三（身份证号：110101198001011234）授权委托代理人李四为我方代理人。
                        代理人全权代表我方处理本项目投标事宜，授权期限至投标有效期结束。
                        法定代表人（签字）：张三
                        委托代理人（签字）：李四
                        备注线 .................
                        """, List.of())
        ));

        assertThat(AuthorizationLetterCheckPolicy.isCatalogOrOutlinePage(
                bidDoc.pages().get(0).text())).isFalse();
        QualityCheckItemResult result = AuthorizationLetterCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void compositionInstructionsDoNotShadowActualAuthorization() {
        var doc = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(8, "投标文件应包括下列内容：\n(1)投标函\n(2)法定代表人身份证明\n(3)授权委托书（适用于有委托代理人的情况）", List.of()),
                new DocumentContent.PageContent(11, "授权委托书\n法定代表人：张三，委托代理人：李四，授权期限：自签署日起至投标有效期结束，授权范围：提交投标文件。", List.of())));
        var result = AuthorizationLetterCheckPolicy.check(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences().getFirst().pageNumber()).isEqualTo(11);
    }


    @Test
    void genericAttachmentNoticeDoesNotShadowActualAuthorization() {
        var doc = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(8, "投标文件须附：投标函、法定代表人身份证明、授权委托书（适用于有委托代理人的情况）", List.of()),
                new DocumentContent.PageContent(11, "授权委托书\n法定代表人：张三，委托代理人：李四，授权期限：自签署日起至投标有效期结束，授权范围：提交投标文件。", List.of())));
        assertThat(AuthorizationLetterCheckPolicy.check(doc).evidences().getFirst().pageNumber()).isEqualTo(11);
    }

    @Test
    void fillingInstructionsCannotReplaceIncompleteActualAuthorization() {
        var doc = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(11, "授权委托书\n法定代表人：张三\n委托代理人：李四", List.of()),
                new DocumentContent.PageContent(100, "授权委托书填写要求：应填写法定代表人、委托代理人的姓名、授权期限和授权范围。", List.of())));
        var result = AuthorizationLetterCheckPolicy.check(doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences().getFirst().pageNumber()).isEqualTo(11);
    }

    @Test
    void namedPartiesCannotMakeAnUnfilledScopeValid() {
        var doc = DocumentContent.fromText("bid.pdf", "hash",
                "授权委托书\n法定代表人：张三\n委托代理人：李四\n授权期限：请填写");
        assertThat(AuthorizationLetterCheckPolicy.check(doc).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("冲突比对覆盖无冒号写法：空间隔填写的第二份授权正文必须参与比对，不得静默通过第一份")
    void spaceSeparatedAuthorizationPageParticipatesInConflictComparison() {
        var doc = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(5, """
                        授权委托书
                        法定代表人：张三
                        委托代理人：李四
                        授权范围：提交投标文件
                        授权期限：自签署日起至投标有效期结束
                        """, List.of()),
                new DocumentContent.PageContent(20, """
                        授权委托书
                        法定代表人 王五
                        委托代理人 赵六
                        授权范围 全权代表本公司参加投标
                        授权期限 长期有效
                        """, List.of())));

        var result = AuthorizationLetterCheckPolicy.check(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("多个内容不同的授权正文");
    }

    @Test
    @DisplayName("真实写法覆盖：全权代表本公司参加投标 + 长期有效 应正向确认为已填写")
    void representativeScopeAndLongTermValidityAreRecognizedAsFilled() {
        var doc = DocumentContent.fromText("bid.pdf", "hash",
                "授权委托书\n法定代表人：张三\n委托代理人：李四\n"
                        + "授权范围：全权代表本公司参加投标\n授权期限：长期有效");

        var result = AuthorizationLetterCheckPolicy.check(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("要素齐全");
    }

    @Test
    @DisplayName("法人证明掩码身份证：法定代表人姓名字段加掩码身份证号应正向确认")
    void maskedIdCardLegalRepresentativeProofIsRecognized() {
        AuthorizationRequirement requirement = new AuthorizationRequirement(
                true, true, "法定代表人身份证明", "法定代表人亲自参加免授权委托书");
        var doc = DocumentContent.fromText("bid.pdf", "hash",
                "法定代表人身份证明\n投标人：某某信息技术有限公司\n法定代表人：张三\n身份证号：110101********1234");

        var result = AuthorizationLetterCheckPolicy.check(doc, requirement);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("免除授权委托书");
    }

    @Test
    @DisplayName("换行无冒号写法：角色与姓名跨行书写时第二份授权正文同样参与冲突比对")
    void newlineSeparatedAuthorizationPageParticipatesInConflictComparison() {
        var doc = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(5, """
                        授权委托书
                        法定代表人：张三
                        委托代理人：李四
                        授权范围：提交投标文件
                        授权期限：自签署日起至投标有效期结束
                        """, List.of()),
                new DocumentContent.PageContent(20, """
                        授权委托书
                        法定代表人
                        王五
                        委托代理人
                        赵六
                        授权范围 全权代表本公司参加投标
                        授权期限 长期有效
                        """, List.of())));

        var result = AuthorizationLetterCheckPolicy.check(doc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("多个内容不同的授权正文");
    }

    @Test
    @DisplayName("截断掩码身份证：位数不足的掩码不得作为法人证明实值")
    void truncatedMaskedIdCardDoesNotProveLegalRepresentative() {
        AuthorizationRequirement requirement = new AuthorizationRequirement(
                true, true, "法定代表人身份证明", "法定代表人亲自参加免授权委托书");
        var doc = DocumentContent.fromText("bid.pdf", "hash",
                "法定代表人身份证明\n法定代表人：张三\n身份证号：110101******1234");

        var result = AuthorizationLetterCheckPolicy.check(doc, requirement);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
