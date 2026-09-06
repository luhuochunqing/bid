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

class BidOpeningTableCheckPolicyTest {

    @Test
    @DisplayName("真实开标一览表：包含完整表头、项目名、总价与工期，直通 PASS 并提取总价")
    void check_realBidOpeningTable_returnsPass() {
        String pageText = """
                开标一览表
                项目名称：数智化投标管理平台采购项目
                项目编号：XY-2026-001
                投标人名称：上海西域供应链管理有限公司
                | 序号 | 标的名称 | 投标总价（元） | 工期（日历天） | 质量标准 | 备注 |
                | 1 | 投标总报价 | 3,280,000.00 | 30 | 合格 | 人民币大写：叁佰贰拾捌万元整 |
                法定代表人或授权代理人（签字）：张三
                投标人（盖章）：上海西域供应链管理有限公司
                日期：2026年9月6日
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash1", "", List.of(
                new DocumentContent.PageContent(1, "目录\n开标一览表 ............... 25", List.of("目录")),
                new DocumentContent.PageContent(25, pageText, List.of("开标一览表"))
        ));

        QualityCheckItemResult result = BidOpeningTableCheckPolicy.check(bidDoc);

        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.BID_OPENING_TABLE.getCode());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("包含规范完整的开标一览表");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(25);
        assertThat(BidOpeningTableCheckPolicy.extractTablePrice(bidDoc)).isEqualByComparingTo(new BigDecimal("3280000.00"));
    }

    @Test
    @DisplayName("目录索引点线干扰：仅在目录提及开标一览表，不能误判为正文通过")
    void check_catalogOnly_notPasses() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash2", "", List.of(
                new DocumentContent.PageContent(1, "目 录\n第一章 投标函及附录 ... 1\n第二章 开标一览表 ............ 18", List.of("目录"))
        ));

        QualityCheckItemResult result = BidOpeningTableCheckPolicy.check(bidDoc);

        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("未检测到正式填写的开标一览表");
    }

    @Test
    @DisplayName("未填写空白占位模板：包含【此处粘贴开标一览表】或下划线留白，直接拦截 FAIL")
    void check_unfilledPlaceholder_returnsFail() {
        String placeholderPage = """
                开标一览表
                【此处填写或粘贴经盖章签署的开标一览表】
                投标总价（大写）：__________________ （小写）：¥____________
                法定代表人或授权代表（签字）：__________
                """;

        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash3", "", List.of(
                new DocumentContent.PageContent(12, placeholderPage, List.of("开标一览表"))
        ));

        QualityCheckItemResult result = BidOpeningTableCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位模板");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("未提供开标一览表：标书中完全缺失开标一览表，判定 FAIL 提示补充")
    void check_missingTable_returnsFail() {
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "hash4", "", List.of(
                new DocumentContent.PageContent(1, "技术方案正文，无任何报价表格", List.of("正文"))
        ));

        QualityCheckItemResult result = BidOpeningTableCheckPolicy.check(bidDoc);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检测到开标一览表");
    }
}
