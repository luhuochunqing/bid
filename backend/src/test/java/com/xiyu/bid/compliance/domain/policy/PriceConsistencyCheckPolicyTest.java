package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceConsistencyCheckPolicyTest {

    @Test
    @DisplayName("正常合规报价：大小写完全一致且未超过最高限价，直通 PASS")
    void check_consistentAndUnderMaxPrice_returnsPass() {
        String bidText = """
                投标函
                致：采购人
                二、投标报价：
                投标总价（大写）：人民币叁佰贰拾万元整
                投标总价（小写）：¥3,200,000.00 元
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash1", "", List.of(
                new DocumentContent.PageContent(1, bidText, List.of("投标函"))
        ));

        PriceRequirement req = new PriceRequirement(
                true,
                new BigDecimal("3500000.00"),
                "最高限价为人民币 3,500,000.00 元",
                "总价报价",
                true,
                true,
                "招标文件规定最高限价 350 万元"
        );

        QualityCheckItemResult result = PriceConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("报价大写与小写金额完全一致");
        assertThat(result.conclusion()).contains("未超过招标文件最高投标限价");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("最高限价超限废标：标书报价超过最高限价，立即判定 FAIL 一票否决")
    void check_exceedsMaxPrice_returnsFail() {
        String bidText = """
                投标函
                投标总报价：
                大写：人民币肆佰万元整
                小写：¥4,000,000.00 元
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash2", "", List.of(
                new DocumentContent.PageContent(2, bidText, List.of("投标函"))
        ));

        PriceRequirement req = new PriceRequirement(
                true,
                new BigDecimal("3500000.00"),
                "最高限价为 350 万元",
                "总价报价",
                true,
                true,
                "限价 350 万"
        );

        QualityCheckItemResult result = PriceConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("超过招标文件最高投标限价");
        assertThat(result.conclusion()).contains("4000000");
        assertThat(result.conclusion()).contains("3500000");
    }

    @Test
    @DisplayName("大小写金额不一致：大写折算数字与小写阿拉伯数字不符，判定 FAIL")
    void check_inconsistentUpperAndLower_returnsFail() {
        String bidText = """
                投标总价：大写：人民币叁佰万元整，小写：¥3,200,000.00 元
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash3", "", List.of(
                new DocumentContent.PageContent(1, bidText, List.of("投标函"))
        ));

        PriceRequirement req = PriceRequirement.standard();

        QualityCheckItemResult result = PriceConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("报价大写金额与小写金额不一致");
    }

    @Test
    @DisplayName("表函不一致：开标一览表报价与投标函总价不一致，判定 FAIL")
    void check_tableAndLetterConflict_returnsFail() {
        String page1 = """
                投标函
                投标总价：大写：人民币叁佰万元整，小写：¥3,000,000.00 元
                """;
        String page2 = """
                开标一览表
                项目名称：采购项目
                投标人：测试公司
                序号 1 | 投标总价：3,500,000.00 元 | 工期：30天
                法定代表人（签字）：张三
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash4", "", List.of(
                new DocumentContent.PageContent(1, page1, List.of("投标函")),
                new DocumentContent.PageContent(15, page2, List.of("开标一览表"))
        ));

        PriceRequirement req = PriceRequirement.standard();

        QualityCheckItemResult result = PriceConsistencyCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("开标一览表总报价");
        assertThat(result.conclusion()).contains("与投标函总价");
        assertThat(result.conclusion()).contains("不一致");
    }
}
