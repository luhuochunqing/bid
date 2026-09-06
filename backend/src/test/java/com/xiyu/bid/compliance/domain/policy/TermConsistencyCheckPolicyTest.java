package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermConsistencyCheckPolicyTest {

    @Test
    @DisplayName("工期超期拦截：标书响应工期超过招标文件上限，判定 FAIL")
    void check_scheduleExceedsRequirement_returnsFail() {
        String pageText = """
                投标函
                致：采购人
                我方承诺本项目实施服务工期为：240日历天。
                质保期为：3年。
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash1", "", List.of(
                new DocumentContent.PageContent(1, pageText, List.of("投标函"))
        ));

        BusinessTermsRequirement req = new BusinessTermsRequirement(
                true,
                180,
                "工期：180日历天",
                24,
                "质保期：2年",
                90,
                "投标有效期：90天",
                null,
                null,
                "招标文件商务要求"
        );

        QualityCheckItemResult result = TermConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.SCHEDULE_RESPONSE.getCode());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("投标工期响应（240日历天）超过招标文件最高允许工期（180日历天）");
    }

    @Test
    @DisplayName("质保期缩水拦截：标书质保期短于招标文件最低要求，判定 FAIL")
    void check_warrantyLessThanRequirement_returnsFail() {
        String pageText = """
                商务响应表
                工期承诺：120日历天
                质保期承诺：1年免费维保
                投标有效期：90天
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash2", "", List.of(
                new DocumentContent.PageContent(2, pageText, List.of("商务响应表"))
        ));

        BusinessTermsRequirement req = new BusinessTermsRequirement(
                true,
                180,
                "工期：180日历天",
                36,
                "质保期：不少于3年",
                90,
                "投标有效期：90天",
                null,
                null,
                "要求质保期不少于3年"
        );

        QualityCheckItemResult result = TermConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("质保期响应（1年）低于招标文件规定的最低要求（3年）");
    }

    @Test
    @DisplayName("投标有效期不足拦截：标书有效期短于招标要求，判定 FAIL")
    void check_validityShortfall_returnsFail() {
        String pageText = """
                投标函
                工期承诺：90日历天
                质保期承诺：3年
                投标有效期：60天
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash3", "", List.of(
                new DocumentContent.PageContent(1, pageText, List.of("投标函"))
        ));

        BusinessTermsRequirement req = new BusinessTermsRequirement(
                true,
                180,
                "工期：180天",
                36,
                "质保期：3年",
                90,
                "投标有效期：90天",
                null,
                null,
                "投标有效期90天"
        );

        QualityCheckItemResult result = TermConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("投标有效期响应（60天）短于招标文件要求的最低有效期限（90天）");
    }

    @Test
    @DisplayName("占位模板留白拦截：商务承诺存在下划线或待填留白，判定 FAIL")
    void check_placeholderUnfilled_returnsFail() {
        String pageText = """
                投标承诺函
                我方承诺本工程工期为：______日历天，质保期为：【请填写保修期限】。
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash4", "", List.of(
                new DocumentContent.PageContent(3, pageText, List.of("投标承诺函"))
        ));

        QualityCheckItemResult result = TermConsistencyCheckPolicy.check(bidDoc, BusinessTermsRequirement.standard());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("商务条款承诺中检测到未填写的模板占位符或空白留白");
    }

    @Test
    @DisplayName("目录页过滤与穿透：目录点线页不作为最终判定依据，穿透至正文合格页直通 PASS")
    void check_catalogFilteredAndRealBodyPassed_returnsPass() {
        String catalogPage = """
                目 录
                第一章 投标函及商务响应.......................1
                第二章 工期与进度承诺.........................15
                第三章 售后及质保承诺.........................25
                """;

        String bodyPage = """
                第二章 工期与进度承诺
                我方郑重承诺：
                本项目工期为：150日历天（自合同签订之日起算）。
                质保期为：3年。
                投标有效期：90日历天。
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash5", "", List.of(
                new DocumentContent.PageContent(1, catalogPage, List.of("目录")),
                new DocumentContent.PageContent(15, bodyPage, List.of("工期与进度承诺"))
        ));

        BusinessTermsRequirement req = new BusinessTermsRequirement(
                true,
                180,
                "工期要求：180日历天",
                36,
                "质保期：3年",
                90,
                "投标有效期：90日历天",
                null,
                null,
                "招标商务要求"
        );

        QualityCheckItemResult result = TermConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已做出明确且合规的工期与商务条款响应承诺");
        assertThat(result.conclusion()).contains("满足招标文件工期上限: 180日历天要求");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(15);
    }
}
