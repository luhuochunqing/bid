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
}
